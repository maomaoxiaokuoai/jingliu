"""In-process adapter for the real, build-time-pinned parse_video_py package.

No Flask/FastAPI/Uvicorn, listener, localhost request, runtime pip, downloaded code,
or CLI. The Android host is a non-exported bound service in a private process.
An explicitly selected engine always calls its own implementation. Page compatibility
repairs only use the current upstream request responses, never another hidden engine.
"""
from __future__ import annotations

import asyncio
import contextlib
import contextvars
import dataclasses
import importlib
import importlib.metadata
import ipaddress
import json
import logging
import re
import socket
import sys
import time
from typing import Any
from urllib.parse import urlsplit, urlunsplit

UPSTREAM_COMMIT = "5fcf87256edb5ffcdebf0e4aac2a5a41745da76e"
MAX_REQUEST = 96 * 1024
MAX_RESULT = 192 * 1024  # comfortably below Android Binder's shared 1 MiB transaction budget
MAX_BODY = 8 * 1024 * 1024
MAX_IMAGES = 500
_current: contextvars.ContextVar[Any] = contextvars.ContextVar("jingliu_request", default=None)
_registry = None


class LocalFailure(Exception):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


class _DiscardText:
    def write(self, text):
        return len(text)
    def flush(self):
        pass
    def isatty(self):
        return False


def _url(value: str) -> str:
    if not isinstance(value, str) or not value or len(value) > 16384:
        raise LocalFailure("LOCAL_INPUT")
    p = urlsplit(value)
    # Old upstream endpoints sometimes say HTTP: require TLS rather than sending a Cookie there.
    if p.scheme not in ("https", "http") or not p.hostname or p.username or p.password:
        raise LocalFailure("LOCAL_URL_POLICY")
    try:
        port = p.port
    except ValueError:
        raise LocalFailure("LOCAL_URL_POLICY") from None
    if port not in (None, 80, 443):
        raise LocalFailure("LOCAL_URL_POLICY")
    host = p.hostname.lower().rstrip('.')
    if host == "localhost" or host.endswith(".localhost") or '.' not in host:
        raise LocalFailure("LOCAL_URL_POLICY")
    try:
        if not ipaddress.ip_address(host).is_global:
            raise LocalFailure("LOCAL_URL_POLICY")
    except ValueError:
        pass
    return urlunsplit(("https", host if port in (None, 80) else host+":443", p.path, p.query, ""))


def _dns_address_allowed(value: str) -> bool:
    ip = ipaddress.ip_address(value)
    # System VPN clients such as mihomo may resolve public HOSTNAMES to this Fake-IP
    # routing range. URL literals in this range remain forbidden by _url(). TLS
    # hostname/certificate checks remain enabled. Never allow RFC1918 or loopback.
    return ip.is_global or (ip.version==4 and ip in ipaddress.ip_network("198.18.0.0/15"))


def _belongs(host: str, domain: str) -> bool:
    d = domain.lower().lstrip('.')
    return host.lower() == d or host.lower().endswith('.'+d)


def cookie_header(cookies: list[dict], url: str, now: int | None = None) -> str:
    """Java host filters the platform first; recheck host-only/path/expiry per redirect here."""
    now = int(time.time()) if now is None else now
    u = urlsplit(url)
    found = []
    for c in cookies:
        domain, path = c.get("domain", ""), c.get("path", "/") or "/"
        name, value = c.get("name", ""), c.get("value", "")
        if not all(isinstance(x, str) for x in (domain, path, name, value)):
            continue
        if not re.fullmatch(r"[!#$%&'*+.^_`|~0-9A-Za-z-]+", name):
            continue
        if any(ord(x)<32 or ord(x)==127 for x in value):
            continue
        if not _belongs(u.hostname or "", domain):
            continue
        if c.get("hostOnly") and (u.hostname or "").lower()!=domain.lower().lstrip('.'):
            continue
        if c.get("secure", True) and u.scheme!="https":
            continue
        expiry = c.get("expires", 0)
        if not isinstance(expiry, (float, int)) or (expiry and expiry<=now):
            continue
        target_path = u.path or "/"
        if not (target_path==path or target_path.startswith(path if path.endswith('/') else path+'/')):
            continue
        found.append((len(path), name+'='+value))
    return '; '.join(x[1] for x in sorted(found, key=lambda x: -x[0]))


@dataclasses.dataclass
class _Context:
    cookies: list[dict]
    cancel: Any
    dns: set = dataclasses.field(default_factory=set)
    user_agent: str = ""
    received: Any = None
    pages: list = dataclasses.field(default_factory=list)

    def __post_init__(self):
        import httpx
        self.received = httpx.Cookies()

    def check(self):
        if self.cancel.isCancelled():
            raise LocalFailure("LOCAL_CANCELLED")

    async def address(self, url):
        self.check()
        u = urlsplit(_url(str(url)))
        if u.hostname not in self.dns:
            values = await asyncio.wait_for(asyncio.get_running_loop().getaddrinfo(
                u.hostname, 443, type=socket.SOCK_STREAM), 10)
            if not values or any(not _dns_address_allowed(v[4][0]) for v in values):
                raise LocalFailure("LOCAL_URL_POLICY")
            self.dns.add(u.hostname)
        self.check()


def _client_factory(**kwargs):
    import httpx
    ctx = _current.get()
    if ctx is None:
        raise LocalFailure("LOCAL_CONTEXT")
    # This is the only HTTP implementation injected into the inspected upstream helpers.
    # Each parser call is scoped; no credential globals or environment proxy are accepted.
    kwargs.pop("proxies", None)
    kwargs.pop("proxy", None)
    kwargs.pop("cookies", None)
    if "headers" in kwargs:
        # Some upstream code feeds the first response headers into the next request.
        # Remove hop-by-hop/response-only fields; rebuild cookies from a scoped jar below.
        kwargs["headers"] = {k:v for k,v in dict(kwargs["headers"]).items()
            if k.lower() not in ("set-cookie","cookie","authorization","host","content-length",
                                  "content-encoding","transfer-encoding","connection","server","date") }
    kwargs["trust_env"] = False
    kwargs["verify"] = True
    kwargs["timeout"] = httpx.Timeout(20.0, connect=12.0)
    kwargs["max_redirects"] = 8
    hooks = kwargs.pop("event_hooks", {})
    if hooks:
        raise LocalFailure("LOCAL_NETWORK_CONTRACT")

    async def request_guard(request):
        ctx.check()
        request.url = httpx.URL(_url(str(request.url)))
        await ctx.address(request.url)
        # Prevent forwarding previously-authenticated headers to a redirect destination.
        request.headers.pop("authorization", None)
        own = cookie_header(ctx.cookies, str(request.url))
        request.headers.pop("cookie", None)
        ctx.received.set_cookie_header(request)
        existing = request.headers.get("cookie", "")
        # Client cookies received from the platform are already scoped by HTTPX's jar.
        # Never duplicate a stored account cookie with a weaker anonymous response value.
        own_names = {part.split('=', 1)[0].strip() for part in own.split(';') if '=' in part}
        safe_existing = '; '.join(part.strip() for part in existing.split(';')
                                  if '=' in part and part.split('=',1)[0].strip() not in own_names)
        request.headers.pop("cookie", None)
        if own or safe_existing:
            request.headers["Cookie"] = '; '.join(x for x in (own, safe_existing) if x)
        request.headers["Accept-Encoding"] = "identity"
        ctx.user_agent = request.headers.get("User-Agent", "")[:2048]

    async def response_guard(response):
        ctx.check()
        ctx.received.extract_cookies(response)

    class _BoundedClient(httpx.AsyncClient):
        async def send(self, request, **opts):
            ctx.check()
            opts["stream"] = True
            response = await super().send(request, **opts)
            try:
                mime = response.headers.get("content-type", "").lower()
                # Some upstream redirect probes reach a video body: metadata parsing must not
                # download that entire file into Python RAM. Preserve URL/status/headers only.
                body = bytearray()
                if not mime.startswith(("video/", "audio/", "image/")):
                    async for piece in response.aiter_bytes(chunk_size=32768):
                        ctx.check()
                        if len(body)+len(piece)>MAX_BODY:
                            raise LocalFailure("LOCAL_RESPONSE_TOO_LARGE")
                        body.extend(piece)
                address = str(response.url)
                host = urlsplit(address).hostname or ""
                if response.status_code in range(200,300) and any(_belongs(host,d) for d in ("xiaohongshu.com","kuaishou.com","chenzhongtech.com","gifshow.com")):
                    # Keep bounded page state only during this parse, never write it or credentials to disk.
                    ctx.pages.append((address, bytes(body).decode("utf-8", errors="replace")))
                    while len(ctx.pages)>4 or sum(len(v) for _,v in ctx.pages)>MAX_BODY:
                        ctx.pages.pop(0)
                headers = [(k,v) for k,v in response.headers.multi_items()
                           if k.lower() not in ("content-encoding", "content-length", "transfer-encoding")]
                return httpx.Response(response.status_code, headers=headers, content=bytes(body),
                                      request=response.request, history=response.history,
                                      extensions=response.extensions)
            finally:
                await response.aclose()

    return _BoundedClient(event_hooks={"request": [request_guard], "response": [response_guard]}, **kwargs)


def _load_registry():
    global _registry
    if _registry is not None:
        return _registry
    parser = importlib.import_module("parse_video_py.parser")
    utils = importlib.import_module("parse_video_py.utils")
    mapping = parser.video_source_info_mapping
    if not isinstance(mapping, dict) or not mapping:
        raise LocalFailure("LOCAL_UPSTREAM_CONTRACT")
    original = utils.create_async_client
    import httpx
    class HttpxFacade:
        def __getattr__(self, name):
            return _client_factory if name=="AsyncClient" else getattr(httpx, name)
    facade = HttpxFacade()
    # Patch already-imported aliases, not just utils (upstream uses `from utils import ...`).
    # One serial worker calls Python in its private process, so this does not affect yt-dlp.
    for name,module in list(sys.modules.items()):
        if module is not None and (name=="parse_video_py" or name.startswith("parse_video_py.")):
            for attribute,value in list(vars(module).items()):
                if value is original:
                    setattr(module, attribute, _client_factory)
                elif value is httpx.AsyncClient:
                    setattr(module, attribute, _client_factory)
                elif value is httpx:
                    setattr(module, attribute, facade)
    utils.create_async_client = _client_factory
    _registry = mapping
    return mapping


def _parser_for(source: str, mapping):
    host = urlsplit(source).hostname or ""
    # Match the URL HOST, never a substring in a query or path as upstream currently does.
    candidates = []
    for spec in mapping.values():
        for domain in spec["domain_list"]:
            if _belongs(host, domain):
                candidates.append((len(domain), spec["parser"]))
    if not candidates:
        raise LocalFailure("LOCAL_UNSUPPORTED")
    return max(candidates,key=lambda x:x[0])[1]


def _clean_result(value) -> dict:
    if dataclasses.is_dataclass(value):
        result = dataclasses.asdict(value)
    elif isinstance(value, dict):
        result = value
    else:
        raise LocalFailure("LOCAL_SCHEMA")
    if not isinstance(result.get("images", []), list) or len(result.get("images", []))>MAX_IMAGES:
        raise LocalFailure("LOCAL_RESPONSE_TOO_LARGE")
    return result


async def _parse_async(request, cancel):
    ctx = _Context(request.get("cookies", []), cancel)
    handle = _current.set(ctx)
    try:
        ctx.check()
        source = _url(request["url"])
        cls = _parser_for(source, _load_registry())
        # This executes the REAL upstream class, not our old remote wrapper or an empty mock.
        parser = cls()
        work = asyncio.create_task(parser.parse_share_url(source))
        async def watch():
            while True:
                ctx.check()
                await asyncio.sleep(.08)
        guard = asyncio.create_task(watch())
        try:
            done, _ = await asyncio.wait((work, guard), timeout=65,
                                         return_when=asyncio.FIRST_COMPLETED)
            if not done:
                raise LocalFailure("LOCAL_TIMEOUT")
            if guard in done:
                await guard  # propagate explicit cancellation
            # Prefer actual returned page URLs over upstream guessed image-origin rewrites.
            from .media_compat import from_pages, PageFailure
            try:
                compatible = from_pages(ctx.pages)
            except PageFailure as error:
                raise LocalFailure(error.code) from None
            if compatible is not None:
                value, _page = compatible
                # Original share URL remains the stable item identity across retries.
                if not work.done():work.cancel()
            else:
                value = await work
            ctx.check()
            return {"ok": True, "data": _clean_result(value), "source": source,
                    "backend": "parse_video_py", "commit": UPSTREAM_COMMIT,
                    "headers": {"User-Agent": ctx.user_agent}}
        finally:
            work.cancel(); guard.cancel()
            await asyncio.gather(work, guard, return_exceptions=True)
    finally:
        _current.reset(handle)


def _error(error: BaseException) -> dict:
    if isinstance(error, LocalFailure):
        code = error.code
    elif isinstance(error, (ImportError, ModuleNotFoundError)):
        code = "LOCAL_DEPENDENCY"
    elif isinstance(error, (asyncio.TimeoutError, TimeoutError)):
        code = "LOCAL_TIMEOUT"
    else:
        code = "LOCAL_PARSE_FAILED"
        try:
            import httpx
            if isinstance(error, httpx.TimeoutException):code="LOCAL_TIMEOUT"
            elif isinstance(error, httpx.HTTPStatusError):code="LOCAL_HTTP_"+str(error.response.status_code)
            elif isinstance(error, httpx.TransportError):code="LOCAL_NETWORK"
        except ImportError:
            pass
    # Do not return repr(error), exception message, URL, request headers or traceback.
    return {"ok": False, "error": code}


def execute(request_json: str, cancel) -> str:
    """Chaquopy entry point. JNI/Binder input contains credentials, never log it."""
    disabled = logging.root.manager.disable
    try:
        if not isinstance(request_json, str) or len(request_json.encode('utf-8'))>MAX_REQUEST:
            raise LocalFailure("LOCAL_INPUT")
        request = json.loads(request_json)
        if not isinstance(request, dict) or request.get("schema")!=1:
            raise LocalFailure("LOCAL_INPUT")
        if not isinstance(request.get("cookies", []), list) or len(request.get("cookies", []))>256:
            raise LocalFailure("LOCAL_INPUT")
        # Upstream sometimes prints a URL when rejecting it. Its stdout/stderr and logging
        # must not write credentials to Android Logcat; one worker owns this private process.
        logging.disable(logging.CRITICAL)
        with contextlib.redirect_stdout(_DiscardText()), contextlib.redirect_stderr(_DiscardText()):
            if request.get("operation")=="selftest":
                if cancel.isCancelled():
                    raise LocalFailure("LOCAL_CANCELLED")
                registry = _load_registry()
                from lxml import etree
                from parsel import Selector
                import fake_useragent, yaml, httpx
                assert etree.fromstring(b"<a/>").tag=="a"
                assert Selector(text='<b>ok</b>').css('b::text').get()=='ok'
                assert yaml.safe_load('a: 1')=={'a': 1}
                assert fake_useragent.UserAgent(os="iOS").random
                result={"ok": True, "backend":"parse_video_py", "commit":UPSTREAM_COMMIT,
                        "version":importlib.metadata.version("parse-video-py"),
                        "parser_count":len(registry), "network_tested":False}
            else:
                result = asyncio.run(_parse_async(request, cancel))
        encoded=json.dumps(result,ensure_ascii=False,separators=(',',':'),allow_nan=False)
        if len(encoded.encode('utf-8'))>MAX_RESULT:
            raise LocalFailure("LOCAL_RESPONSE_TOO_LARGE")
        return encoded
    except BaseException as e:
        if isinstance(e, (KeyboardInterrupt, SystemExit)):
            return json.dumps({"ok":False,"error":"LOCAL_RUNTIME_STOPPED"})
        return json.dumps(_error(e))
    finally:
        logging.disable(disabled)
