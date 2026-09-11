"""Tests adapter policy with HTTPX MockTransport and explicit upstream TEST DOUBLES.
They do not import/download the real upstream project, access Android, or validate live sites.
"""
import asyncio
import dataclasses
import importlib
import json
import sys
import threading
import time
import types
import unittest
from pathlib import Path
from unittest.mock import patch

import httpx
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'app/src/main/python'))
import jingliu_local as adapter

class Cancel:
    def __init__(self):self.value=False
    def isCancelled(self):return self.value

@dataclasses.dataclass
class FixtureVideo:
    video_url:str='https://media.example/file.mp4'
    cover_url:str='https://media.example/cover.jpg'
    title:str='TEST ONLY'
    images:list=dataclasses.field(default_factory=list)

class FixtureParser:
    calls=[]
    async def parse_share_url(self,url):
        self.calls.append(url)
        return FixtureVideo()

class AdapterTests(unittest.TestCase):
    def setUp(self):
        self.cancel=Cancel()
        self.registry={"fixture":{"domain_list":["platform.example"],"parser":FixtureParser}}
        FixtureParser.calls.clear()
    def execute(self, **changes):
        request={"schema":1,"url":"https://platform.example/post/1","cookies":[],**changes}
        with patch.object(adapter,'_load_registry',return_value=self.registry):
            return json.loads(adapter.execute(json.dumps(request),self.cancel))
    def test_real_adapter_delegates_to_selected_class_not_http_service(self):
        value=self.execute()
        self.assertTrue(value['ok']);self.assertEqual(value['data']['title'],'TEST ONLY')
        self.assertEqual(FixtureParser.calls,['https://platform.example/post/1'])
        self.assertEqual(value['backend'],'parse_video_py');self.assertEqual(value['commit'],adapter.UPSTREAM_COMMIT)
    def test_host_not_query_controls_upstream_routing(self):
        value=self.execute(url='https://wrong.example/?url=https://platform.example/')
        self.assertEqual(value['error'],'LOCAL_UNSUPPORTED');self.assertEqual(FixtureParser.calls,[])
    def test_subdomain_boundary(self):
        self.assertEqual(self.execute(url='https://platform.example.attacker.example/post')['error'],'LOCAL_UNSUPPORTED')
        self.assertTrue(self.execute(url='https://sub.platform.example/post')['ok'])
    def test_no_upstream_youtube_claim(self):
        self.assertEqual(self.execute(url='https://www.youtube.com/watch?v=test')['error'],'LOCAL_UNSUPPORTED')
    def test_url_validation(self):
        for url in ['file:///data/a','https://a:b@platform.example/','https://127.0.0.1/','https://[::1]/','https://platform.example:444/','https://localhost/']:
            with self.subTest(url=url):self.assertEqual(self.execute(url=url)['error'],'LOCAL_URL_POLICY')
    def test_fake_ip_only_allowed_as_dns_route_not_url_literal(self):
        self.assertTrue(adapter._dns_address_allowed('198.18.1.2'))
        self.assertEqual(self.execute(url='https://198.18.1.2/post')['error'],'LOCAL_URL_POLICY')
        for ip in ['127.0.0.1','10.1.2.3','192.168.1.1','172.20.1.1','::1','fc00::1']:
            self.assertFalse(adapter._dns_address_allowed(ip))
    def test_input_size_and_shape_limits(self):
        self.assertEqual(json.loads(adapter.execute('x'*(adapter.MAX_REQUEST+1),self.cancel))['error'],'LOCAL_INPUT')
        self.assertEqual(json.loads(adapter.execute('[]',self.cancel))['error'],'LOCAL_INPUT')
        self.assertEqual(self.execute(cookies=[{}]*257)['error'],'LOCAL_INPUT')
    def test_cookies_host_path_expiry_and_injection(self):
        c={'domain':'platform.example','name':'sid','value':'secret','path':'/video','hostOnly':True,'secure':True,'expires':200}
        self.assertEqual(adapter.cookie_header([c],'https://platform.example/video/1',100),'sid=secret')
        for url in ['http://platform.example/video/1','https://sub.platform.example/video/1','https://platform.example/videos','https://other.example/video']:
            self.assertEqual(adapter.cookie_header([c],url,100),'')
        self.assertEqual(adapter.cookie_header([c],'https://platform.example/video',201),'')
        self.assertEqual(adapter.cookie_header([{**c,'value':'a\rb'}],'https://platform.example/video',100),'')
    def test_precancel_stops_before_parser(self):
        self.cancel.value=True
        self.assertEqual(self.execute()['error'],'LOCAL_CANCELLED');self.assertEqual(FixtureParser.calls,[])
    def test_cancel_during_async_request_cancels_work(self):
        closed=[]
        class Waiting:
            async def parse_share_url(s,url):
                try:await asyncio.sleep(15)
                finally:closed.append(True)
        self.registry['fixture']['parser']=Waiting
        timer=threading.Timer(.15,lambda:setattr(self.cancel,'value',True));timer.start()
        started=time.monotonic()
        try:value=self.execute()
        finally:timer.cancel()
        self.assertEqual(value['error'],'LOCAL_CANCELLED');self.assertLess(time.monotonic()-started,2);self.assertTrue(closed)
    def test_raw_exception_and_cookie_not_returned(self):
        class Failing:
            async def parse_share_url(s,url):raise ValueError('cookie=TOP_SECRET; https://private.example/?sig=secret')
        self.registry['fixture']['parser']=Failing
        value=self.execute();self.assertEqual(value['error'],'LOCAL_PARSE_FAILED')
        self.assertNotIn('TOP_SECRET',json.dumps(value));self.assertNotIn('private.example',json.dumps(value))
    def test_oversized_results_fail_without_returning_large_payload(self):
        class Huge:
            async def parse_share_url(s,url):return FixtureVideo(title='x'*(adapter.MAX_RESULT+1))
        self.registry['fixture']['parser']=Huge
        value=self.execute();self.assertEqual(value['error'],'LOCAL_RESPONSE_TOO_LARGE');self.assertLess(len(json.dumps(value)),256)
    def test_context_is_removed_after_parse(self):
        self.execute();self.assertIsNone(adapter._current.get())
        with self.assertRaises(adapter.LocalFailure):adapter._client_factory()
    def test_output_is_suppressed_and_missing_stats_not_invented(self):
        class Talking:
            async def parse_share_url(s,url):
                print('sensitive-url-do-not-log');return FixtureVideo()
        self.registry['fixture']['parser']=Talking
        import io, contextlib
        output=io.StringIO()
        with contextlib.redirect_stdout(output):value=self.execute()
        self.assertEqual(output.getvalue(),'');self.assertNotIn('stats',value['data'])
    def test_already_imported_http_helper_aliases_are_patched(self):
        original=lambda **kwargs:None
        fake_package=types.ModuleType('parse_video_py')
        fake_parser=types.ModuleType('parse_video_py.parser');fake_parser.video_source_info_mapping=self.registry
        fake_utils=types.ModuleType('parse_video_py.utils');fake_utils.create_async_client=original
        fake_leaf=types.ModuleType('parse_video_py.parser.fixture');fake_leaf.create_async_client=original;fake_leaf.httpx=httpx;fake_leaf.AsyncClient=httpx.AsyncClient
        modules={x.__name__:x for x in [fake_package,fake_parser,fake_utils,fake_leaf]}
        with patch.dict(sys.modules,modules),patch.object(adapter,'_registry',None):
            self.assertIs(adapter._load_registry(),self.registry)
            self.assertIs(fake_leaf.create_async_client,adapter._client_factory)
            self.assertIs(fake_utils.create_async_client,adapter._client_factory)
            self.assertIs(fake_leaf.httpx.AsyncClient,adapter._client_factory)
            self.assertIs(fake_leaf.AsyncClient,adapter._client_factory)
            self.assertIs(fake_leaf.httpx.Response,httpx.Response)
    def network_case(self, handler, run):
        async def test():
            ctx=adapter._Context([{'domain':'platform.example','name':'sid','value':'OWN','path':'/','secure':True}],self.cancel)
            async def checked(url):adapter._url(str(url))
            ctx.address=checked # DNS is explicitly mocked. Real HTTPX request/cookie/response code runs.
            key=adapter._current.set(ctx)
            try:
                async with adapter._client_factory(transport=httpx.MockTransport(handler),follow_redirects=True) as client:
                    return await run(client)
            finally:adapter._current.reset(key)
        return asyncio.run(test())
    def test_httpx_redirect_does_not_leak_account_cookie(self):
        seen=[]
        def handler(r):
            seen.append((r.url.host,r.headers.get('cookie','')))
            if r.url.host=='platform.example':return httpx.Response(302,headers={'Location':'https://media.example/video'})
            return httpx.Response(200,json={'ok':True})
        async def run(client):self.assertTrue((await client.get('https://platform.example/post')).json()['ok'])
        self.network_case(handler,run)
        self.assertEqual(seen,[('platform.example','sid=OWN'),('media.example','')])
    def test_httpx_response_payload_limit(self):
        def handler(r):return httpx.Response(200,content=b'x'*1025,headers={'Content-Type':'application/json'})
        async def run(client):
            with self.assertRaises(adapter.LocalFailure) as err:await client.get('https://platform.example/post')
            self.assertEqual(err.exception.code,'LOCAL_RESPONSE_TOO_LARGE')
        with patch.object(adapter,'MAX_BODY',1024):self.network_case(handler,run)
    def test_media_redirect_probe_does_not_buffer_whole_video(self):
        def handler(r):return httpx.Response(200,content=b'x'*65536,headers={'Content-Type':'video/mp4'})
        async def run(client):
            result=await client.get('https://platform.example/post');self.assertEqual(result.content,b'')
        self.network_case(handler,run)
    def test_request_client_forces_tls_env_proxy_disabled(self):
        async def run(client):
            self.assertFalse(client.trust_env)
            result=await client.get('http://platform.example/post')
            self.assertEqual(str(result.request.url),'https://platform.example/post')
        self.network_case(lambda r:httpx.Response(200,json={}),run)

if __name__=='__main__':unittest.main(verbosity=2)
