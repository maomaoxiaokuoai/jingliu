"""Real adapter functions with explicit page fixtures and HTTPX MockTransport, no live sites."""
import asyncio
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
import httpx
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'app/src/main/python'))
import jingliu_local as adapter
from jingliu_local import media_compat as media
class Cancel:
    def isCancelled(self):return False

def page(note):
    return 'window.__INITIAL_STATE__='+json.dumps({'note':{'currentNoteId':'abcd','noteDetailMap':{'abcd':{'note':note}}}},ensure_ascii=False)+';'

class Tests(unittest.TestCase):
    def test_xhs_keeps_signed_transformed_original_not_guessed_origin(self):
        url='https://sns-img-qc.xhscdn.com/abcdefghijklmnop!nd_dft?sig=A%2Bb%3D'
        p=page({'title':'fixture','imageList':[{'urlDefault':url,'urlPre':'https://sns-img-bd.xhscdn.com/abcdefghijklmnop!nd_prv'}]})
        result=media.xhs('https://www.xiaohongshu.com/explore/abcd',p)
        self.assertEqual(result['images'][0]['url'],url)
        self.assertEqual(len(result['images'][0]['backup_urls']),1)
        self.assertNotIn('notes_pre_post',json.dumps(result))
    def test_xhs_unknown_stats_not_fabricated(self):
        p=page({'imageList':[{'urlDefault':'https://sns-img-qc.xhscdn.com/a'}],'interactInfo':{'likedCount':'0'}})
        result=media.xhs('https://www.xiaohongshu.com/explore/abcd',p)
        self.assertEqual(result['statistics']['likes'],0);self.assertIsNone(result['statistics']['shares'])
    def test_xhs_wrong_requested_post_rejected(self):
        with self.assertRaises(media.PageFailure):media.xhs('https://www.xiaohongshu.com/explore/ffff',page({'imageList':[]}))
    def test_xhs_video_keeps_cover_but_not_cover_as_gallery(self):
        p=page({'imageList':[{'urlDefault':'https://sns-img-qc.xhscdn.com/cover'}],'video':{'media':{'stream':{'h264':[{'masterUrl':'https://sns-video-bd.xhscdn.com/movie.mp4','width':1080,'height':1920}]}}}})
        result=media.xhs('https://www.xiaohongshu.com/explore/abcd',p)
        self.assertEqual(result['images'],[]);self.assertTrue(result['video_url'].endswith('movie.mp4'));self.assertTrue(result['cover_url'].endswith('cover'))
    def test_balanced_json_preserves_text_and_bare_undefined(self):
        x=media.assignment('window.INIT_STATE={"a":"} undefined", "b":undefined,"nested":{"x":[1,2]}}; junk',('window.INIT_STATE',))
        self.assertEqual(x['a'],'} undefined');self.assertIsNone(x['b']);self.assertEqual(x['nested']['x'],[1,2])
    def test_kuaishou_matching_not_recommended(self):
        body='window.INIT_STATE='+json.dumps({'detail':{'photo':{'photoId':'123','caption':'fixture','mainMvUrls':[{'url':'https://v.example/a.mp4'}]}},'recommended':{'photo':{'photoId':'456','photoUrl':'https://v.example/wrong.mp4'}}})
        self.assertEqual(media.kuaishou('https://www.kuaishou.com/short-video/123',body)['video_url'],'https://v.example/a.mp4')
        self.assertIsNone(media.kuaishou('https://www.kuaishou.com/short-video/789',body))
    def test_kuaishou_failure_not_success(self):
        body='window.INIT_STATE='+json.dumps({'detail':{'result':2,'photo':{'photoId':'123'}}})
        with self.assertRaises(media.PageFailure):media.kuaishou('https://www.kuaishou.com/short-video/123',body)
    def test_only_known_pages_are_parsed(self):
        self.assertIsNone(media.from_pages([('https://evil.example/explore/abcd',page({'imageList':[{'urlDefault':'https://cdn.example/a'}]}))]))
    def test_context_cookie_persists_across_two_actual_httpx_clients(self):
        async def run():
            ctx=adapter._Context([],Cancel());seen=[]
            async def address(url):pass
            ctx.address=address
            token=adapter._current.set(ctx)
            def transport(req):
                seen.append((req.url.host,req.headers.get('cookie','')))
                headers={'Content-Type':'text/html'}
                if req.url.path=='/first':headers['Set-Cookie']='anonymous=abc; Path=/; Secure'
                return httpx.Response(200,headers=headers,text='page')
            try:
                async with adapter._client_factory(transport=httpx.MockTransport(transport)) as c:await c.get('https://www.kuaishou.com/first')
                async with adapter._client_factory(transport=httpx.MockTransport(transport),headers={'Set-Cookie':'not-request','Content-Length':'888'}) as c:await c.get('https://www.kuaishou.com/second')
                async with adapter._client_factory(transport=httpx.MockTransport(transport)) as c:await c.get('https://foreign.example/third')
                self.assertIn('anonymous=abc',seen[1][1]);self.assertEqual(seen[2][1],'')
            finally:adapter._current.reset(token)
        asyncio.run(run())
    def test_successful_html_response_is_captured_bounded_in_context(self):
        async def run():
            ctx=adapter._Context([],Cancel())
            async def address(url):pass
            ctx.address=address;t=adapter._current.set(ctx)
            try:
                async with adapter._client_factory(transport=httpx.MockTransport(lambda req:httpx.Response(200,text=page({'imageList':[]})))) as c:
                    await c.get('https://www.xiaohongshu.com/explore/abcd')
                self.assertEqual(len(ctx.pages),1);self.assertIn('noteDetailMap',ctx.pages[0][1])
            finally:adapter._current.reset(t)
        asyncio.run(run())
    def test_parse_uses_captured_actual_page_instead_of_upstream_guess(self):
        class FixtureParser:
            async def parse_share_url(self,url):
                ctx=adapter._current.get()
                ctx.pages.append(('https://www.xiaohongshu.com/explore/abcd',page({'imageList':[{'urlDefault':'https://sns-img-qc.xhscdn.com/actual!nd_dft?sign=1'}]})))
                return {'images':[{'url':'https://ci.xiaohongshu.com/notes_pre_post/guess'}]}
        with patch.object(adapter,'_load_registry',return_value={'fixture':{'domain_list':['xiaohongshu.com'],'parser':FixtureParser}}):
            result=json.loads(adapter.execute(json.dumps({'schema':1,'url':'https://www.xiaohongshu.com/explore/abcd','cookies':[]}),Cancel()))
        self.assertTrue(result['ok']);self.assertIn('actual!nd_dft',result['data']['images'][0]['url'])
    def test_upstream_parse_error_can_only_recover_from_same_valid_page(self):
        class FixtureParser:
            async def parse_share_url(self,url):
                adapter._current.get().pages.append(('https://www.xiaohongshu.com/explore/abcd',page({'imageList':[{'urlDefault':'https://sns-img-qc.xhscdn.com/actual!nd_dft'}]})))
                raise ValueError('sensitive upstream response must not leak')
        with patch.object(adapter,'_load_registry',return_value={'fixture':{'domain_list':['xiaohongshu.com'],'parser':FixtureParser}}):
            result=json.loads(adapter.execute(json.dumps({'schema':1,'url':'https://www.xiaohongshu.com/explore/abcd','cookies':[]}),Cancel()))
        self.assertTrue(result['ok']);self.assertNotIn('sensitive',json.dumps(result))

if __name__=='__main__':unittest.main(verbosity=2)
