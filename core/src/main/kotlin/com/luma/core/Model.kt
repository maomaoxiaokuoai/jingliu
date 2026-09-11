package com.luma.core

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

enum class Platform(val title:String,val domains:List<String>,val loginUrl:String,val domestic:Boolean) {
    BILIBILI("哔哩哔哩", listOf("bilibili.com","b23.tv"),"https://passport.bilibili.com/login",true),
    DOUYIN("抖音",listOf("douyin.com","iesdouyin.com"),"https://www.douyin.com/",true),
    KUAISHOU("快手",listOf("kuaishou.com","gifshow.com","chenzhongtech.com"),"https://www.kuaishou.com/",true),
    XIAOHONGSHU("小红书",listOf("xiaohongshu.com","xhslink.com","xhslink.cn"),"https://www.xiaohongshu.com/",true),
    YOUTUBE("YouTube",listOf("youtube.com","youtu.be","google.com"),"https://accounts.google.com/ServiceLogin?service=youtube",false),
    TIKTOK("TikTok",listOf("tiktok.com"),"https://www.tiktok.com/login",false),
    X("X / Twitter",listOf("x.com","twitter.com","t.co"),"https://x.com/i/flow/login",false),
    DIRECT("媒体直链",emptyList(),"",false);
    fun owns(host:String):Boolean=domains.any{domainMatches(host,it)}
    companion object { fun of(url:String):Platform { val h=runCatching{URI(url).host.lowercase(Locale.ROOT)}.getOrDefault(""); return entries.firstOrNull{it.owns(h)}?:DIRECT } }
}
fun domainMatches(host:String,domain:String):Boolean { val h=host.lowercase(Locale.ROOT);val d=domain.trimStart('.').lowercase(Locale.ROOT);return h==d||h.endsWith(".$d") }
object UrlPolicy {
    fun normalize(text:String):String {
        val candidate=Regex("https?://[^\\s<>\"'，。！？]+",RegexOption.IGNORE_CASE).find(text.trim())?.value
            ?.trimEnd(')',']','}', '）','】',',',';','；')?:error("请粘贴完整的视频或图集分享链接")
        val u=URI(candidate);require(u.userInfo==null&&u.host!=null&&u.port in listOf(-1,443,80)){"链接不安全或端口不受支持"}
        require(u.scheme in listOf("https","http")){"只支持 HTTPS 链接"}
        val scheme=if(u.scheme=="http")"https" else u.scheme
        val result=URI(scheme,null,u.host.lowercase(Locale.ROOT),if(u.port==80)-1 else u.port,u.path,u.query,null)
        // URI constructor would double-encode existing percent escapes. Preserve raw components.
        return "$scheme://${u.host.lowercase(Locale.ROOT)}${if(u.port>0&&u.port!=80)":${u.port}" else ""}${u.rawPath.orEmpty()}${u.rawQuery?.let{"?$it"}.orEmpty()}"
            .also { require(result.host.isNotBlank()) }
    }
    fun checkTransport(url:String,allowLoopback:Boolean=false):URI {
        val u=URI(url);require(u.userInfo==null&&u.host!=null){"非法资源链接"}
        val host=u.host.lowercase(Locale.ROOT)
        if(allowLoopback&&host in setOf("127.0.0.1","localhost")&&u.scheme=="http")return u
        require(u.scheme=="https" && MediaTransportPolicy.portAllowed(host,u.port)){"拒绝不安全的媒体地址"}
        require(host!="localhost"&&host!="::1"&&!host.startsWith("127.")&&!host.startsWith("10.")&&!host.startsWith("192.168.")&&!host.startsWith("169.254.")&&!Regex("172\\.(1[6-9]|2[0-9]|3[01])\\..*").matches(host)){"拒绝本机或私有网络资源"}
        return u
    }
}
enum class Kind { VIDEO, AUDIO, GALLERY, PLAYLIST }
data class Author(val id:String="",val name:String="",val avatar:String="",val followers:Long?=null)
data class Stats(val views:Long?=null,val likes:Long?=null,val favorites:Long?=null,val comments:Long?=null,val shares:Long?=null)
data class Format(val id:String,val label:String="",val url:String="",val audioUrl:String="",val extension:String="mp4",
    val width:Int=0,val height:Int=0,val fps:Double=0.0,val bitrate:Long?=null,val bytes:Long?=null,val audioBytes:Long?=null,
    val audioBitrate:Long?=null,val hasAudio:Boolean=true,val video:Boolean=true,val engine:Boolean=false,val codec:String="",val drm:Boolean=false,
    val backupUrls:List<String> = emptyList(),val audioBackupUrls:List<String> = emptyList(),val qualityCode:Int?=null) {
    fun size(duration:Double?):SizeEstimate {
        if(bytes!=null && (audioUrl.isBlank()||audioBytes!=null))return SizeEstimate(bytes+(audioBytes?:0),false)
        val v=bytes?:if(duration!=null&&bitrate!=null)(duration*bitrate/8).toLong()else null
        val a=if(audioUrl.isBlank())0L else audioBytes?:if(duration!=null&&audioBitrate!=null)(duration*audioBitrate/8).toLong()else null
        return SizeEstimate(if(v!=null&&a!=null)v+a else null,true)
    }
    val display:String get()=label.ifBlank { if(!video)"音频" else if(height>0)"${height}p${if(fps>=50)" · ${fps.toInt()}fps" else ""}" else "源文件（分辨率未知）" }
}
data class SizeEstimate(val bytes:Long?,val estimated:Boolean)
data class MediaEntry(val id:String,val title:String,val url:String,val thumbnail:String="",val image:Boolean=false,val bytes:Long?=null,val backupUrls:List<String> = emptyList())
data class Media(val id:String,val url:String,val platform:Platform,val title:String,val description:String="",val thumbnail:String="",
    val author:Author=Author(),val stats:Stats=Stats(),val duration:Double?=null,val kind:Kind=Kind.VIDEO,
    val formats:List<Format> = emptyList(),val entries:List<MediaEntry> = emptyList(),val headers:Map<String,String> = emptyMap(),
    val notice:String="",val totalEntries:Int?=null,val nextOffset:Int?=null,val extractor:String="") {
    val best:Format? get()=formats.filterNot{it.drm}.sortedWith(compareByDescending<Format>{it.video}.thenByDescending{it.height.toLong()*it.width}.thenByDescending{it.height}.thenByDescending{it.fps}.thenByDescending{it.bitrate?:0}.thenBy{if(it.codec.startsWith("avc"))0 else 1}).firstOrNull()
}
object Selection {
    fun toggle(selected:Set<String>,id:String):Set<String> = if(id in selected)selected-id else selected+id
    fun all(selected:Set<String>,ids:Set<String>):Set<String> = if(ids.isNotEmpty()&&selected.containsAll(ids))emptySet()else ids
}
fun safeName(raw:String):String = raw.replace(Regex("[\\x00-\\x1f\\x7f/\\\\:*?\"<>|]"),"_").trim().trim('.').take(72).ifBlank{"media"}
fun digest(s:String):String=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
fun sizeText(bytes:Long?):String { if(bytes==null)return "大小未知"; if(bytes<1024)return "$bytes B";if(bytes<1024*1024)return "%.1f KB".format(Locale.ROOT,bytes/1024.0);if(bytes<1024L*1024*1024)return "%.1f MB".format(Locale.ROOT,bytes/1048576.0);return "%.2f GB".format(Locale.ROOT,bytes/1073741824.0) }
fun countText(n:Long?):String=when{n==null->"未提供";n>=10000->"%.1f万".format(Locale.ROOT,n/10000.0);else->n.toString()}
fun audioBest(list:List<Format>):Format?=list.filter{!it.video&&!it.drm}.maxByOrNull{it.bitrate?:0}
