package com.luma.core

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest

class PlatformError(message:String,val failureCode:String="PLATFORM_RESPONSE"):Exception(message)
fun secureUrl(value:String):String = when { value.startsWith("//")->"https:$value";value.startsWith("http://")->"https://"+value.removePrefix("http://");else->value }
fun urlList(value:Any?):String = when(value){is String->secureUrl(value);is List<*>->value.map{urlList(it)}.firstOrNull{it.startsWith("https://")}.orEmpty();is Map<*,*>->urlList(value.at("url_list")?:value.at("urlList")?:value.at("url")?:value.at("src"));else->""}
fun seconds(value:Any?,millis:Boolean=false):Double?=value.doubleOrNull()?.takeIf{it>0}?.let{if(millis)it/1000 else it}
fun queryParams(url:String):Map<String,String> = URI(url).rawQuery.orEmpty().split('&').mapNotNull { val p=it.split('=',limit=2);if(p.size==2)URLDecoder.decode(p[0],"UTF-8") to URLDecoder.decode(p[1],"UTF-8")else null }.toMap()

/** Public WBI request signing, not an access-control bypass. All entitlement errors are retained. */
object BiliWbi {
    private val permutation=intArrayOf(46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,33,9,42,19,29,28,14,39,12,38,41,13,37,48,7,16,24,55,40,61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,36,20,34,44,52)
    fun sign(params:Map<String,String>,imgKey:String,subKey:String,timestamp:Long):String {
        val joined=imgKey+subKey;require(joined.length>=64){"WBI 密钥无效"}
        val key=permutation.take(32).map{joined[it]}.joinToString("")
        val q=(params+("wts" to timestamp.toString())).toSortedMap().entries.joinToString("&"){enc(it.key)+"="+enc(it.value.filterNot{c->c in "!'()*"})}
        val hash=MessageDigest.getInstance("MD5").digest((q+key).toByteArray()).joinToString(""){"%02x".format(it)}
        return "$q&w_rid=$hash"
    }
}
class NativeExtractors(private val http:Http) {
    fun extract(url:String,token:CancelToken,one:Boolean=false):Media? {
        return when(Platform.of(url)) {
            Platform.BILIBILI -> bili(url,token,one)
            Platform.DOUYIN,Platform.KUAISHOU,Platform.XIAOHONGSHU,Platform.TIKTOK -> share(url,token)
            Platform.X -> twitterPhotos(url,token)
            Platform.DIRECT -> if(Regex("\\.(mp4|webm|m4a|mp3|mov|mkv|jpg|jpeg|png|webp)(\\?|$)",RegexOption.IGNORE_CASE).containsMatchIn(url))direct(url)else null
            else -> null
        }
    }
    private fun data(url:String,token:CancelToken):Any? { val r=http.json(url,token,BiliMediaPolicy.apiHeaders);if(r.n("code")!=0L)throw PlatformError("B站接口未返回可用数据（代码 ${r.n("code")?:0}）",if(r.n("code") == -101L)"SESSION_REJECTED" else "BILI_API");return r.at("data") }
    fun bili(input:String,token:CancelToken,one:Boolean=false):Media? {
        var url=input
        if(URI(url).host.endsWith("b23.tv"))url=http.text(url,token).url
        val bvid=Regex("BV[0-9A-Za-z]{10}").find(url)?.value
        val aid=Regex("(?:/av|aid=)([0-9]+)").find(url)?.groupValues?.get(1)
        if(bvid==null&&aid==null)return null
        val d=data("https://api.bilibili.com/x/web-interface/view?"+(bvid?.let{"bvid=$it"}?:"aid=$aid"),token)
        val canonical="https://www.bilibili.com/video/${d.s("bvid")}/"
        val mid=d.n("owner","mid")?.toString().orEmpty()
        val followers=try{data("https://api.bilibili.com/x/relation/stat?vmid=$mid",token).n("follower")}catch(e:TransferCancelled){throw e}catch(_:Exception){null}
        val author=Author(mid,d.s("owner","name"),secureUrl(d.s("owner","face")),followers)
        val stats=Stats(d.n("stat","view"),d.n("stat","like"),d.n("stat","favorite"),d.n("stat","reply"),d.n("stat","share"))
        val pages=d.list("pages");val p=queryParams(url)["p"]?.toIntOrNull()
        val base=Media(d.s("bvid"),url,Platform.BILIBILI,d.s("title"),d.s("desc"),secureUrl(d.s("pic")),author,stats,seconds(d.at("duration")),headers=BiliMediaPolicy.apiHeaders+("Referer" to canonical))
        if(!one&&p==null&&pages.size>1)return base.copy(kind=Kind.PLAYLIST,entries=pages.mapIndexed{index,v->MediaEntry(v.n("cid").toString(),v.s("part").ifBlank{"第 ${index+1} 集"},"$canonical?p=${index+1}",base.thumbnail)},totalEntries=pages.size)
        if(!one&&p==null) {
            val episodes=d.list("ugc_season","sections").flatMap{it.list("episodes")}.distinctBy{it.s("bvid")}.filter{it.s("bvid").isNotBlank()}
            if(episodes.size>1)return base.copy(kind=Kind.PLAYLIST,entries=episodes.map{MediaEntry(it.s("bvid"),it.s("title"),"https://www.bilibili.com/video/${it.s("bvid")}/?p=1",secureUrl(it.s("arc","pic")))},totalEntries=episodes.size)
        }
        if(p!=null&&(p<1||p>pages.size))throw PlatformError("所选分P不存在，未替换成第一分P","QUALITY_UNAVAILABLE")
        val page=pages.getOrNull((p?:1)-1)?:pages.firstOrNull()
        val cid=page.n("cid")?:d.n("cid")?:throw PlatformError("未找到视频 cid")
        val nav=http.json("https://api.bilibili.com/x/web-interface/nav",token,BiliMediaPolicy.apiHeaders+("Referer" to canonical)).at("data")
        fun key(name:String)=nav.s("wbi_img",name).substringAfterLast('/').substringBefore('.')
        val q=BiliWbi.sign(mapOf("bvid" to d.s("bvid"),"cid" to cid.toString(),"qn" to "127","fnval" to "4048","fourk" to "1"),key("img_url"),key("sub_url"),System.currentTimeMillis()/1000)
        val play=data("https://api.bilibili.com/x/player/wbi/playurl?$q",token)
        BiliMediaPolicy.checkAccess(play)
        val result=PlatformJson.biliFormats(play)
        if(result.isEmpty())return null // Legacy multi-segment or unsupported stream: handled by yt-dlp.
        return base.copy(url="$canonical?p=${p?:1}",title=if(pages.size>1)"${base.title} · ${page.s("part")}" else  base.title,
            duration=seconds(page.at("duration"))?:base.duration,formats=result,notice="仅显示平台实际返回的可播放画质；会员与登录权限可能影响最高档。")
    }
    fun share(input:String,token:CancelToken):Media? {
        val directId=DouyinSharePolicy.id(input)
        if(Platform.of(input)==Platform.DOUYIN&&directId!=null)slides(input,directId,token)?.let{return it}
        val response=http.text(input,token);val url=response.url;val platform=Platform.of(url)
        val target=if(platform==Platform.DOUYIN)DouyinSharePolicy.id(url) else null
        if(target!=null&&target!=directId)slides(url,target,token)?.let{return it}
        val html=response.body
        val roots=mutableListOf<Any?>()
        listOf("window.__INITIAL_STATE__","window.__APOLLO_STATE__","window._ROUTER_DATA","window.__INIT_STATE__","window.INIT_STATE","window.__INITIAL_PROPS__").forEach { assignedJson(html,it)?.let{roots.add(it)} }
        for(id in listOf("RENDER_DATA","__UNIVERSAL_DATA_FOR_REHYDRATION__","SIGI_STATE","__NEXT_DATA__")) {
            val raw=Regex("<script[^>]*id=[\"']$id[\"'][^>]*>(.*?)</script>",setOf(RegexOption.DOT_MATCHES_ALL,RegexOption.IGNORE_CASE)).find(html)?.groupValues?.get(1)
            if(raw!=null)runCatching{Json.parse(if(id=="RENDER_DATA")URLDecoder.decode(raw,"UTF-8")else raw)}.getOrNull()?.let{roots.add(it)}
        }
        when(platform) {
            Platform.XIAOHONGSHU -> roots.forEach { root -> findObject(root){(it.containsKey("imageList")||it.containsKey("video"))&&it.containsKey("noteId") }?.let {return PlatformJson.xhs(it,url)} }
            Platform.DOUYIN -> {
                roots.forEach{root->findObject(root){it.s("aweme_id")==target&&(it.containsKey("video")||it.containsKey("images"))}?.let{return PlatformJson.douyin(it,url)}}
                val id=Regex("/(?:video|note)/(\\d+)").find(url)?.groupValues?.get(1)?:queryParams(url)["modal_id"]
                if(id!=null){val d=http.json("https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=$id",token,mapOf("Referer" to url)).at("aweme_detail");if(d.s("aweme_id")==id)return PlatformJson.douyin(d,url)}
            }
            Platform.KUAISHOU -> roots.forEach{root->findObject(root){it.containsKey("photoId")&&(it.containsKey("photoUrl")||it.containsKey("videoResource")||it.containsKey("atlas"))}?.let{return PlatformJson.kuaishou(it,url)}}
            Platform.TIKTOK -> roots.forEach{root->findObject(root){it.containsKey("id")&&(it.containsKey("imagePost")||it.containsKey("image_post_info"))}?.let{return PlatformJson.tiktok(it,url)}}
            else->Unit
        };return null
    }
    private fun slides(url:String,id:String,token:CancelToken):Media? {
        for(endpoint in DouyinSharePolicy.endpoints(id)) {
            token.check()
            try {
                val root=http.json(endpoint,token,mapOf("Referer" to "https://www.iesdouyin.com/"))
                val item=root.list("aweme_details").firstOrNull{it.s("aweme_id")==id}?:continue
                val media=PlatformJson.douyin(item,url)
                if(media.formats.isNotEmpty()||media.entries.isNotEmpty())return media
            }catch(e:TransferCancelled){throw e}catch(_:Exception){/* bounded public-interface fallback */}
        };return null
    }
    private fun twitterPhotos(url:String,token:CancelToken):Media? {
        val id=Regex("/status/(\\d+)").find(url)?.groupValues?.get(1)?:return null
        // Syndication occasionally rejects requests or omits private/account-only media. Never infer success.
        val d=http.json("https://cdn.syndication.twimg.com/tweet-result?id=$id&lang=en&token="+twitterToken(id),token)
        val pictures=d.list("photos")
        if(pictures.isEmpty()||d.list("mediaDetails").any{it.s("type") in listOf("video","animated_gif")})return null
        return Media(id,url,Platform.X,d.s("text").take(100).ifBlank{"X 图集"},d.s("text"),author=Author(d.s("user","id_str"),d.s("user","name"),secureUrl(d.s("user","profile_image_url_https")),d.n("user","followers_count")),stats=Stats(likes=d.n("favorite_count"),comments=d.n("conversation_count"),shares=d.n("retweet_count")),kind=Kind.GALLERY,
            entries=pictures.mapIndexed{i,v->val original=secureUrl(v.s("url"));MediaEntry("$id-$i","图片 ${i+1}",original,original,true)})
    }
    private fun twitterToken(id:String):String {
        var value=(id.toDouble()/1e15)*Math.PI;val integer=value.toLong();value-=integer
        val b=StringBuilder(java.lang.Long.toString(integer,36)).append('.')
        repeat(12){value*=36;val digit=value.toInt().coerceIn(0,35);b.append("0123456789abcdefghijklmnopqrstuvwxyz"[digit]);value-=digit}
        return b.toString().replace(Regex("0+|\\."),"")
    }
    private fun direct(url:String):Media {
        val name=URLDecoder.decode(URI(url).path.substringAfterLast('/'),"UTF-8");val ext=name.substringAfterLast('.').lowercase();val image=ext in setOf("jpg","jpeg","png","webp")
        return Media(digest(url),url,Platform.DIRECT,name,kind=if(image)Kind.GALLERY else if(ext in setOf("mp3","m4a"))Kind.AUDIO else Kind.VIDEO,
            entries=if(image)listOf(MediaEntry(digest(url),name,url,url,true))else emptyList(),formats=if(!image)listOf(Format("source",url=url,extension=ext,video=ext !in setOf("mp3","m4a")))else emptyList())
    }
}
object PlatformJson {
    fun biliFormats(play:Any?):List<Format> = BiliMediaPolicy.formats(play)
    fun douyin(d:Any?,url:String):Media {
        val id=d.s("aweme_id");val author=d.at("author");val st=d.at("statistics");val v=d.at("video")
        val base=Media(id,url,Platform.DOUYIN,d.s("desc").take(100).ifBlank{"抖音作品 $id"},d.s("desc"),urlList(v.at("cover")),
            Author(author.s("uid"),author.s("nickname"),urlList(author.at("avatar_larger")?:author.at("avatar_thumb")),author.n("follower_count")),
            Stats(st.n("play_count"),st.n("digg_count"),st.n("collect_count"),st.n("comment_count"),st.n("share_count")),seconds(v.at("duration"),true),headers=mapOf("Referer" to "https://www.douyin.com/"))
        val images=d.list("images").mapIndexedNotNull { i,image->val u=urlList(image.at("url_list"));if(u.isBlank())null else MediaEntry("$id-image-$i","图片 ${i+1}",u,u,true) }
        if(images.isNotEmpty())return base.copy(kind=Kind.GALLERY,entries=images,thumbnail=images.first().thumbnail)
        val variants=v.list("bit_rate").ifEmpty{listOf(mapOf("play_addr" to v.at("play_addr"),"width" to v.n("width"),"height" to v.n("height")))}
        val formats=variants.mapIndexedNotNull { i,r ->val p=r.at("play_addr");val u=urlList(p);if(u.isBlank())null else Format("dy-${r.s("gear_name").ifBlank{i.toString()}}",url=u,width=(r.n("width")?:p.n("width")?:v.n("width")?:0).toInt(),height=(r.n("height")?:p.n("height")?:v.n("height")?:0).toInt(),fps=fps(r.at("fps")),bitrate=r.n("bit_rate"),bytes=p.n("data_size"))}
        return base.copy(formats=formats.distinctBy{it.id})
    }
    fun xhs(d:Any?,url:String):Media {
        val id=d.s("noteId");val author=d.at("user");val st=d.at("interactInfo")
        val base=Media(id,url,Platform.XIAOHONGSHU,d.s("title").ifBlank{d.s("desc").take(80)}.ifBlank{"小红书笔记 $id"},d.s("desc"),author=Author(author.s("userId"),author.s("nickname"),secureUrl(author.s("avatar")),author.n("fans")),stats=Stats(likes=st.n("likedCount"),favorites=st.n("collectedCount"),comments=st.n("commentCount"),shares=st.n("shareCount")),duration=seconds(d.at("video","media","video","duration"),true),headers=mapOf("Referer" to "https://www.xiaohongshu.com/"))
        val images=d.list("imageList").mapIndexedNotNull{i,v->val u=secureUrl(v.s("urlDefault").ifBlank{v.list("infoList").firstNotNullOfOrNull{it.s("url").takeIf(String::isNotBlank)}.orEmpty()});if(u.isBlank())null else MediaEntry("$id-image-$i","图片 ${i+1}",u,u,true)}
        if(d.s("type")!="video"&&images.isNotEmpty())return base.copy(kind=Kind.GALLERY,entries=images,thumbnail=images.first().thumbnail)
        val formats=d.at("video","media","stream").obj().values.flatMap{it.arr()}.mapIndexedNotNull{i,v->val u=secureUrl(v.s("masterUrl"));if(u.isBlank())null else Format("xhs-$i",url=u,width=(v.n("width")?:0).toInt(),height=(v.n("height")?:0).toInt(),fps=fps(v.at("fps")),bitrate=v.n("avgBitrate")?:v.n("videoBitrate"),bytes=v.n("size"),codec=v.s("videoCodec"))}
        return base.copy(formats=formats,thumbnail=images.firstOrNull()?.thumbnail.orEmpty())
    }
    fun kuaishou(d:Any?,url:String):Media {
        val id=d.s("photoId");val author=d.at("user")?:d.at("author")
        val base=Media(id,url,Platform.KUAISHOU,d.s("caption").take(100).ifBlank{"快手作品 $id"},d.s("caption"),secureUrl(d.s("coverUrl")),Author(author.s("id"),author.s("name").ifBlank{d.s("userName")},secureUrl(author.s("headerUrl")),author.n("fan")),Stats(d.n("viewCount"),d.n("likeCount"),d.n("collectCount"),d.n("commentCount"),d.n("shareCount")),seconds(d.at("duration"),true),headers=mapOf("Referer" to "https://www.kuaishou.com/"))
        val atlas=d.at("atlas");val urls=atlas.list("cdnList");val cdn=urlList(urls).trimEnd('/')
        val images=(atlas.list("list").ifEmpty{d.list("images")}).mapIndexedNotNull{i,v->val path=v.str().ifBlank{v.s("url")};val u=if(path.startsWith("http"))secureUrl(path)else if(cdn.isNotBlank())"$cdn/${path.trimStart('/')}" else  "";if(u.isBlank())null else MediaEntry("$id-$i","图片 ${i+1}",u,u,true)}
        if(images.isNotEmpty())return base.copy(kind=Kind.GALLERY,entries=images,thumbnail=images.first().thumbnail)
        val candidates=mutableListOf<Any?>();d.at("videoResource").obj().values.forEach{root->
            root.list("adaptationSet").forEach{set->candidates.addAll(set.list("representation"))}
        }
        val formats=candidates.mapIndexedNotNull{i,v->val u=urlList(v.at("url"));if(u.isBlank())null else Format("ks-$i",url=u,width=(v.n("width")?:0).toInt(),height=(v.n("height")?:0).toInt(),bitrate=v.n("avgBitrate"),fps=fps(v.at("frameRate")))}.ifEmpty {
            val u=secureUrl(d.s("photoUrl"));if(u.isBlank())emptyList()else listOf(Format("ks-source",url=u,width=(d.n("width")?:0).toInt(),height=(d.n("height")?:0).toInt())) }
        return base.copy(formats=formats)
    }
    fun tiktok(d:Any?,url:String):Media {
        val id=d.s("id");val a=d.at("author");val st=d.at("statsV2")?:d.at("stats");val images=d.list("imagePost","images").ifEmpty{d.list("image_post_info","images")}.mapIndexedNotNull{i,v->val u=urlList(v.at("imageURL")?:v.at("display_image"));if(u.isBlank())null else MediaEntry("$id-$i","图片 ${i+1}",u,u,true)}
        return Media(id,url,Platform.TIKTOK,d.s("desc").take(100).ifBlank{"TikTok $id"},d.s("desc"),images.firstOrNull()?.thumbnail.orEmpty(),Author(a.s("id"),a.s("nickname"),urlList(a.at("avatarLarger")?:a.at("avatarMedium")),d.n("authorStats","followerCount")),Stats(st.n("playCount"),st.n("diggCount"),st.n("collectCount"),st.n("commentCount"),st.n("shareCount")),kind=Kind.GALLERY,entries=images)
    }
    fun yt(d:Any?,source:String,offset:Int=0):Media {
        val platform=Platform.of(source);val id=d.s("id").ifBlank{digest(source)}
        val thumbs=d.list("thumbnails");val thumb=secureUrl(d.s("thumbnail").ifBlank{thumbs.lastOrNull().s("url")})
        val base=Media(id,d.s("webpage_url").ifBlank{source},platform,d.s("title").ifBlank{"未命名作品 $id"},d.s("description"),thumb,Author(d.s("uploader_id").ifBlank{d.s("channel_id")},d.s("uploader").ifBlank{d.s("channel")},secureUrl(d.s("uploader_avatar")),d.n("channel_follower_count")),Stats(d.n("view_count"),d.n("like_count"),d.n("favorite_count"),d.n("comment_count"),d.n("repost_count")),seconds(d.at("duration")))
        if(d.at("is_live").truth()||d.s("live_status")=="is_live")throw PlatformError("当前是直播流；此版本仅下载已结束的点播内容")
        val entries=d.list("entries")
        if(d.s("_type") in listOf("playlist","multi_video")||entries.isNotEmpty()) {
            val items=entries.mapIndexedNotNull{i,v->
                if(v==null)null else {val u=v.s("webpage_url").ifBlank{v.s("url")};val fixed=if(u.startsWith("http"))u else if(platform==Platform.YOUTUBE&&u.matches(Regex("[A-Za-z0-9_-]{11}")))"https://www.youtube.com/watch?v=$u" else  ""
                    if(fixed.isBlank())null else MediaEntry(v.s("id").ifBlank{"entry-${offset+i}"},v.s("title").ifBlank{"第 ${offset+i+1} 项"},fixed,secureUrl(v.s("thumbnail").ifBlank{v.list("thumbnails").lastOrNull().s("url")}))}
            }
            val total=d.n("playlist_count")?.toInt()
            return base.copy(kind=Kind.PLAYLIST,entries=items,totalEntries=total,nextOffset=if(items.size>=100&&(total==null||offset+items.size<total))offset+100 else null,notice="合集按 100 项分页读取；全选仅选中已加载的项目。")
        }
        val all=d.list("formats").ifEmpty{if(d.s("url").isNotBlank())listOf(d)else emptyList()}.filter{!it.at("has_drm").truth()&&it.s("format_id").isNotBlank()&&it.s("vcodec")!="images"&&it.s("protocol")!="mhtml"}
        val audios=all.filter{it.s("vcodec")=="none"&&it.s("acodec")!="none"}
        val audio=audios.filter{it.s("ext")=="m4a"}.maxByOrNull{it.at("abr").doubleOrNull()?:it.at("tbr").doubleOrNull()?:0.0}?:audios.maxByOrNull{it.at("tbr").doubleOrNull()?:0.0}
        val formats=all.mapNotNull { v->
            if(v.s("url").isBlank())null else {
                val video=v.s("vcodec")!="none";val hasAudio=v.s("acodec")!="none"
                if(video&&!hasAudio&&audio==null)return@mapNotNull null
                val selector=v.s("format_id")+if(video&&!hasAudio)"+${audio.s("format_id")}" else  ""
                val codec=v.s("vcodec")
                // Requested merge is stream-copy into MKV (broad codec support), never transcode.
                Format(selector,label=if(video)"${v.n("height")?.let{"${it}p"}?:"源画质"}${if(fps(v.at("fps"))>=50)" · ${fps(v.at("fps")).toInt()}fps" else ""}${if(codec.isNotBlank())" · ${codec.substringBefore('.')}" else ""}" else "音频 · ${v.s("ext")}",
                    url=v.s("url"),audioUrl=if(video&&!hasAudio)audio.s("url")else "",extension=if(video&&!hasAudio)"mkv" else  v.s("ext").ifBlank{"mp4"},width=(v.n("width")?:0).toInt(),height=(v.n("height")?:0).toInt(),fps=fps(v.at("fps")),bitrate=v.at("tbr").doubleOrNull()?.times(1000)?.toLong(),bytes=v.n("filesize"),audioBytes=if(video&&!hasAudio)audio.n("filesize")else null,audioBitrate=audio.at("tbr").doubleOrNull()?.times(1000)?.toLong(),hasAudio=hasAudio,video=video,engine=true,codec=codec)
            }
        }.distinctBy{it.id}
        if(formats.isEmpty())throw PlatformError("未返回可下载的非 DRM 流；请确认链接、登录、地区和观看权限")
        return base.copy(kind=if(formats.none{it.video})Kind.AUDIO else Kind.VIDEO,formats=formats,notice="字段缺失时显示“未提供”；最高画质为当前会话实际可用的最高档。")
    }
    private fun fps(v:Any?):Double { val s=v.str();return if(s.contains('/')) {val p=s.split('/');(p[0].toDoubleOrNull()?:0.0)/(p[1].toDoubleOrNull()?.takeIf{it!=0.0}?:1.0)}else v.doubleOrNull()?:0.0 }
}
