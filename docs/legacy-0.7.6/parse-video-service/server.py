"""Private compatibility adapter for the real parse_video_py library. Python 3.10+.
Not a platform-login service: it never accepts platform cookies or forwards request headers.
Install pinned requirements.txt first. Binds localhost by default, not a public parsing site.
"""
from __future__ import annotations

import asyncio
import base64
import dataclasses
import hmac
import ipaddress
import json
import os
import socket
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

# Deliberately limit this service to the platforms selected in the Android product.
# YouTube / TikTok use the on-device yt-dlp engine instead.
DOMAINS = (
    "bilibili.com", "b23.tv", "douyin.com", "iesdouyin.com", "kuaishou.com", "gifshow.com",
    "chenzhongtech.com", "xiaohongshu.com", "xhslink.com", "xhslink.cn", "x.com", "twitter.com",
)
MAX_URL = 12_000
MAX_RESULT = 8 * 1024 * 1024


def validate_source(source: str, check_dns: bool = True) -> str:
    u = urlsplit(source)
    host = (u.hostname or "").lower()
    if len(source) > MAX_URL or u.scheme != "https" or not host or u.username or u.password or u.port not in (None, 443):
        raise ValueError("source")
    if not any(host == d or host.endswith("." + d) for d in DOMAINS):
        raise ValueError("platform")
    if any(ord(c) < 32 or ord(c) == 127 for c in source):
        raise ValueError("control character")
    if check_dns:
        addresses = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
        if not addresses or not all(ipaddress.ip_address(a[4][0]).is_global for a in addresses):
            raise ValueError("non-public platform address")
    if (host == "bilibili.com" or host.endswith(".bilibili.com")) and int(parse_qs(u.query).get("p", ["1"])[0]) > 1:
        raise ValueError("upstream first-part-only")
    return source


async def parse_real(source: str) -> dict:
    # This is the upstream library, not copied pattern-matching or synthetic media.
    from parse_video_py import parse_video_share_url
    result = await asyncio.wait_for(parse_video_share_url(source), timeout=55)
    if not dataclasses.is_dataclass(result):
        raise TypeError("unexpected upstream schema")
    return dataclasses.asdict(result)


class ParseServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address, username: str = "", password: str = "", parser=parse_real, check_dns=True):
        if bool(username) != bool(password) or ":" in username or any(c in username + password for c in "\r\n"):
            raise ValueError("Set both service username and password; no colon in username")
        if address[0] not in ("127.0.0.1", "localhost", "::1") and not username:
            raise ValueError("Non-loopback binding requires service Basic authentication and TLS reverse proxy")
        super().__init__(address, Handler)
        self.expected_auth = ("Basic " + base64.b64encode((username + ":" + password).encode()).decode()) if username else ""
        self.parser = parser
        self.check_dns = check_dns
        # Limits platform requests and per-client waiting; busy clients fail, no unbounded queue.
        self.parse_slots = threading.BoundedSemaphore(2)

    def handle_error(self, request, client_address):
        # Never write traceback/request values containing share tokens into process logs.
        return


class Handler(BaseHTTPRequestHandler):
    server: ParseServer
    server_version = "JingliuPrivateParser/0.7.6"
    protocol_version = "HTTP/1.0"

    def setup(self):
        super().setup()
        self.connection.settimeout(12)

    def log_message(self, *args):
        # URLs contain user-supplied share tokens; no request/access logging.
        return

    def send_json(self, status: int, code: int, data=None, message=""):
        payload = json.dumps({"code": code, "msg": message, "data": data}, ensure_ascii=False, separators=(",", ":")).encode()
        if len(payload) > MAX_RESULT:
            status = 502
            payload = b'{"code":502,"msg":"Upstream response too large","data":null}'
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        try:
            self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        # No CORS: only the Android client or a private operator should invoke this endpoint.
        if self.server.expected_auth and not hmac.compare_digest(
                self.headers.get("Authorization", "").encode(), self.server.expected_auth.encode()):
            self.send_json(401, 401, message="Service authentication required")
            return
        route = urlsplit(self.path)
        if route.path == "/health" and not route.query:
            self.send_json(200, 200, {"adapter": "parse-video-py", "mode": "private-server"})
            return
        if route.path != "/video/share/url/parse":
            self.send_json(404, 404, message="Not found")
            return
        try:
            if len(self.path) > MAX_URL * 3 + 100:
                raise ValueError("URL length")
            query = parse_qs(route.query, keep_blank_values=True, max_num_fields=4)
            if set(query) != {"url"} or len(query["url"]) != 1:
                raise ValueError("single url required")
            source = validate_source(query["url"][0], self.server.check_dns)
        except (ValueError, OSError):
            self.send_json(400, 400, message="Unsupported/unsafe link or unsupported Bilibili part. Use native engine.")
            return
        if not self.server.parse_slots.acquire(blocking=False):
            self.send_json(429, 429, message="Busy; retry later")
            return
        try:
            result = asyncio.run(self.server.parser(source))
            self.send_json(200, 200, result)
        except (asyncio.TimeoutError, TimeoutError):
            self.send_json(504, 504, message="Platform parsing timed out")
        except Exception:
            # Raw exceptions can contain tokens or signed URLs: do not echo or log them.
            self.send_json(502, 502, message="Upstream parsing failed; no platform credentials were forwarded")
        finally:
            self.server.parse_slots.release()

    def do_POST(self):
        self.send_json(405, 405, message="This service does not accept cookie, login or upload requests")


def main():
    # Ensure missing native wheels fail before startup, not via an apparently working demo endpoint.
    from parse_video_py import parse_video_share_url  # noqa: F401
    address = os.environ.get("JINGLIU_PARSER_HOST", "127.0.0.1")
    port = int(os.environ.get("JINGLIU_PARSER_PORT", "8000"))
    if not 1 <= port <= 65535:
        raise ValueError("port")
    server = ParseServer((address, port), os.environ.get("PARSE_VIDEO_USERNAME", ""), os.environ.get("PARSE_VIDEO_PASSWORD", ""))
    print("Private parser started. No request URLs, Cookie values or upstream errors will be logged.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
