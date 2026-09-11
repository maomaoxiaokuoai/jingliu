"""Android compatibility layer for page-provided media, never a guessed/rewritten CDN URL.

The pinned upstream parser still executes in Python. These transformations preserve the
original signed URLs from the same response it reads (no separate Kotlin/network engine).
"""
from __future__ import annotations
import json
import re
from urllib.parse import urlsplit, parse_qs

class PageFailure(Exception):
    def __init__(self, code):
        self.code = code
        super().__init__(code)

def assignment(text, names):
    for name in names:
        match = re.search(re.escape(name) + r"\s*=\s*", text)
        if not match:
            continue
        start = match.end()
        if start >= len(text) or text[start] not in '[{':
            continue
        depth = 0; quoted = False; escape = False; end = None
        for i in range(start, len(text)):
            ch = text[i]
            if quoted:
                if escape: escape = False
                elif ch == '\\': escape = True
                elif ch == '"': quoted = False
            elif ch == '"': quoted = True
            elif ch in '[{': depth += 1
            elif ch in ']}':
                depth -= 1
                if depth == 0: end = i + 1; break
        if end is None: continue
        raw = text[start:end]
        # JSON string literals are kept verbatim; bare undefined/NaN only become null.
        raw = re.sub(r'"(?:\\.|[^"\\])*"|\b(?:undefined|NaN)\b',
                     lambda m: m[0] if m[0].startswith('"') else 'null', raw)
        try: return json.loads(raw)
        except (ValueError, RecursionError): continue
    return None

def urls(values):
    result = []
    for raw in values:
        if not isinstance(raw, str): continue
        if raw.startswith('//'): raw = 'https:' + raw
        if raw.startswith('http://'): raw = 'https://' + raw[7:]
        u = urlsplit(raw)
        if u.scheme == 'https' and u.hostname and not u.username and not u.password and raw not in result:
            result.append(raw)
    return result[:12]

def count(v):
    if isinstance(v, bool) or v is None: return None
    if isinstance(v, int): return max(0,v)
    word = str(v).replace(',','').strip()
    try:
        mul = {'万':1e4,'亿':1e8,'K':1e3,'k':1e3}.get(word[-1:],1)
        return int(float(word[:-1] if mul != 1 else word)*mul)
    except (ValueError,OverflowError): return None

def xhs(url, body):
    root=assignment(body, ('window.__INITIAL_STATE__','__INITIAL_STATE__'))
    if not isinstance(root,dict): return None
    note=root.get('note') or {}
    identity=note.get('currentNoteId')
    path=re.search(r'/(?:explore|discovery/item)/([a-fA-F0-9]+)',urlsplit(url).path)
    expected=path[1] if path else None
    if expected and identity and expected != identity: raise PageFailure('LOCAL_ITEM_MISMATCH')
    identity=expected or identity
    node=(note.get('noteDetailMap') or {}).get(identity) or {}
    data=node.get('note')
    if not isinstance(data,dict): return None
    result={'title':data.get('title') or data.get('desc') or '小红书作品','description':data.get('desc',''),
            'images':[], 'author':{},'statistics':{}}
    original_images=data.get('imageList') or []
    all_images=[]
    for item in original_images:
        actual=urls([item.get('urlDefault'),item.get('urlPre')]+[x.get('url') for x in item.get('infoList',[]) if isinstance(x,dict)])
        if actual: all_images.append({'url':actual[0],'thumbnail':actual[0],'backup_urls':actual[1:]})
    streams=((((data.get('video') or {}).get('media') or {}).get('stream') or {}).get('h264') or [])
    videos=[]
    for stream in streams:
        if isinstance(stream,dict):
            candidates=urls([stream.get('masterUrl')]+(stream.get('backupUrls') or []))
            if candidates: videos.append((int(stream.get('width') or 0)*int(stream.get('height') or 0),stream,candidates))
    if videos:
        _, stream, candidates=max(videos,key=lambda x:x[0])
        result.update(video_url=candidates[0],video_backup_urls=candidates[1:],width=stream.get('width'),height=stream.get('height'),size=stream.get('size'))
    else: result['images']=all_images
    result['cover_url']=all_images[0]['url'] if all_images else ''
    user=data.get('user') or {}
    result['author']={'uid':user.get('userId',''),'name':user.get('nickname',''),'avatar':user.get('avatar',''),'followers':count(user.get('fans'))}
    stats=data.get('interactInfo') or {}
    result['statistics']={k:count(stats.get(v)) for k,v in {'likes':'likedCount','favorites':'collectedCount','comments':'commentCount','shares':'shareCount','views':'viewCount'}.items()}
    if not result.get('video_url') and not result['images']: return None
    # Do not inject noteId into legacy generated gallery IDs; share-url based IDs remain stable across retries.
    return result

def kuaishou(url, body):
    root=assignment(body, ('window.INIT_STATE','window.__INITIAL_STATE__','INIT_STATE'))
    if not isinstance(root,dict): return None
    path=re.search(r'/(?:short-video|photo)/([\w-]+)',urlsplit(url).path)
    expected=parse_qs(urlsplit(url).query).get('photoId',[None])[0] or (path[1] if path else None)
    queue=[root]; found=[]
    while queue and len(found)<20:
        value=queue.pop()
        if isinstance(value,dict):
            if isinstance(value.get('photo'),dict): found.append(value)
            queue.extend(x for x in value.values() if isinstance(x,(dict,list)))
        elif isinstance(value,list): queue.extend(value[:500])
        if len(queue)>5000: raise PageFailure('LOCAL_RESPONSE_TOO_LARGE')
    node=next((n for n in found if expected and str(n['photo'].get('photoId') or n['photo'].get('id'))==expected),None)
    if node is None and len(found)==1: node=found[0]
    if node is None: return None
    if node.get('result',1)!=1: raise PageFailure('LOCAL_SESSION_REQUIRED')
    data=node['photo']
    actual_id=str(data.get('photoId') or data.get('id') or '')
    if expected and actual_id and expected!=actual_id: raise PageFailure('LOCAL_ITEM_MISMATCH')
    def path_urls(v): return urls([i.get('url') if isinstance(i,dict) else i for i in v or []])
    video=path_urls(data.get('mainMvUrls')) or path_urls(data.get('videoUrls')) or urls([data.get('photoUrl')])
    cover=path_urls(data.get('coverUrls'))
    atlas=(data.get('ext_params') or {}).get('atlas') or {}
    images=[]
    for image in atlas.get('list') or []:
        if not isinstance(image,str):continue
        actual=urls([image] if image.startswith(('http:','https:','//')) else [str(c).rstrip('/')+'/'+image.lstrip('/') if str(c).startswith('http') else 'https://'+str(c).strip('/')+'/'+image.lstrip('/') for c in atlas.get('cdn') or []])
        if actual:images.append({'url':actual[0],'thumbnail':actual[0],'backup_urls':actual[1:]})
    if not video and not images:return None
    return {'title':data.get('caption') or '快手作品','description':data.get('caption',''),
            'video_url':video[0] if video and not images else '', 'video_backup_urls':video[1:],
            'cover_url':cover[0] if cover else '', 'images':images,
            'author':{'uid':data.get('userId',''),'name':data.get('userName',''),'avatar':data.get('headUrl','')},
            'statistics':{k:count(data.get(v)) for k,v in {'likes':'likeCount','comments':'commentCount','views':'viewCount'}.items()}}

def from_pages(pages):
    for url,body in reversed(pages):
        host=urlsplit(url).hostname or ''
        if host=='xiaohongshu.com' or host.endswith('.xiaohongshu.com'):
            result=xhs(url,body)
            if result is not None:return result,url
        if any(host==d or host.endswith('.'+d) for d in ('kuaishou.com','chenzhongtech.com','gifshow.com')):
            result=kuaishou(url,body)
            if result is not None:return result,url
    return None
