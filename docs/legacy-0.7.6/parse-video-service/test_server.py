"""Tests inject a fake parser; they do not install or exercise the real upstream library."""
import base64
import http.client
import json
import threading
import unittest
from urllib.parse import urlencode
from server import ParseServer, validate_source

class ServerContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.received = []
        async def fixture(source):
            cls.received.append(source)
            return {'title': 'fixture', 'video_url': 'https://cdn.example/fixture.mp4'}
        cls.server = ParseServer(('127.0.0.1', 0), 'local', 'test', parser=fixture, check_dns=False)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown();cls.server.server_close();cls.thread.join()
    def request(self, method, path, auth=True):
        c=http.client.HTTPConnection('127.0.0.1', self.server.server_port, timeout=5)
        headers={'Authorization':'Basic '+base64.b64encode(b'local:test').decode()} if auth else {}
        try:
            c.request(method,path,headers=headers);r=c.getresponse();return r.status,json.loads(r.read())
        finally:c.close()
    def test_health_requires_auth(self):
        self.assertEqual(self.request('GET','/health',False)[0],401)
        self.assertEqual(self.request('GET','/health')[1]['data']['mode'],'private-server')
    def test_real_http_contract_and_original_share_query(self):
        source='https://www.xiaohongshu.com/explore/test?xsec_token=a%2Bb'
        status,payload=self.request('GET','/video/share/url/parse?'+urlencode({'url':source}))
        self.assertEqual(status,200);self.assertEqual(payload['data']['title'],'fixture');self.assertEqual(self.received[-1],source)
    def test_post_cookie_is_rejected(self):
        self.assertEqual(self.request('POST','/video/share/url/parse')[0],405)
    def test_unsupported_site_is_rejected(self):
        status,_=self.request('GET','/video/share/url/parse?'+urlencode({'url':'https://attacker.example/video'}))
        self.assertEqual(status,400)
    def test_duplicate_url_is_rejected(self):
        status,_=self.request('GET','/video/share/url/parse?url=https://douyin.com/&url=https://douyin.com/')
        self.assertEqual(status,400)
    def test_bilibili_second_part_is_rejected(self):
        with self.assertRaises(ValueError):validate_source('https://www.bilibili.com/video/BVxx/?p=2',False)
    def test_cookies_parameter_is_rejected(self):
        self.assertEqual(self.request('GET','/video/share/url/parse?url=https://douyin.com/&cookie=not-accepted')[0],400)
    def test_lookalike_domain_is_rejected(self):
        with self.assertRaises(ValueError):validate_source('https://bilibili.com.attacker.example/video',False)
    def test_no_unauthenticated_external_binding(self):
        with self.assertRaises(ValueError):ParseServer(('0.0.0.0',0))
if __name__=='__main__':unittest.main()
