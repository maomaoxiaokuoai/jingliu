package com.luma.core

import java.net.URI

/** Only selects addresses actually returned for this stream. Never constructs a mirror URL. */
object BiliMediaPolicy {
    val apiHeaders=mapOf(
        "Referer" to "https://www.bilibili.com/",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    )
    fun checkAccess(play:Any?) {
        if(play.at("is_preview").truth())throw PlatformError("平台只返回试看内容，未按完整版下载。请确认观看权限。","PREVIEW_ONLY")
        if(play.at("has_drm").truth()||(play.n("drm_tech_type")?:0)>0)throw PlatformError("此资源使用 DRM 保护，不支持下载。","DRM_PROTECTED")
    }
    fun addresses(stream:Any?):List<String> {
        val candidates=listOf(stream.s("baseUrl"),stream.s("base_url"),stream.s("url"))+
            stream.list("backupUrl").map{it.str()}+stream.list("backup_url").map{it.str()}
        return candidates.filter(String::isNotBlank).map(::secureUrl).distinct()
            .filter{runCatching{UrlPolicy.checkTransport(it)}.isSuccess}
            .sortedBy{val u=URI(it);if(u.port in listOf(-1,443))0 else 1}
    }
    fun formats(play:Any?):List<Format> {
        checkAccess(play)
        val qualities=play.list("accept_quality").mapIndexedNotNull { index,q ->
            q.longOrNull()?.let{it to play.list("accept_description").getOrNull(index).str()}
        }.toMap()
        val audio=play.list("dash","audio").filter{addresses(it).isNotEmpty()}.let{all ->
            all.filter{it.s("codecs").startsWith("mp4a")}.maxByOrNull{it.n("bandwidth")?:0}
                ?:all.maxByOrNull{it.n("bandwidth")?:0}
        }
        val audioUrls=addresses(audio)
        val videos=play.list("dash","video").filterNot{it.at("has_drm").truth()}.mapNotNull { v ->
            val urls=addresses(v)
            if(urls.isEmpty())return@mapNotNull null
            // For a DASH response that advertises audio but has no usable audio address, fail
            // instead of publishing a seemingly complete silent video.
            if(play.list("dash","audio").isNotEmpty()&&audioUrls.isEmpty())return@mapNotNull null
            val q=v.n("id")
            Format("bili-$q-${v.n("codecid")}",qualities[q].orEmpty(),urls.first(),audioUrls.firstOrNull().orEmpty(),
                width=(v.n("width")?:0).toInt(),height=(v.n("height")?:0).toInt(),fps=frameRate(v.at("frameRate")?:v.at("frame_rate")),
                bitrate=v.n("bandwidth"),bytes=v.n("size"),audioBytes=audio.n("size"),audioBitrate=audio.n("bandwidth"),hasAudio=false,codec=v.s("codecs"),
                backupUrls=urls.drop(1),audioBackupUrls=audioUrls.drop(1),qualityCode=q?.toInt())
        }
        val legacy=play.list("durl")
        if(videos.isEmpty()&&legacy.size==1) {
            val d=legacy[0];val urls=addresses(d);val q=play.n("quality")
            if(urls.isNotEmpty())return listOf(Format("bili-$q",qualities[q].orEmpty(),urls.first(),bytes=d.n("size"),backupUrls=urls.drop(1),qualityCode=q?.toInt()))
        }
        if(videos.isEmpty()&&play.list("dash","video").isNotEmpty())
            throw PlatformError("平台返回了视频格式，但没有可用的安全媒体地址；这不是账号未登录。","BILI_CDN_ADDRESS")
        return videos+if(audioUrls.isNotEmpty())listOf(Format("bili-audio",label="原始音频",url=audioUrls.first(),extension="m4a",bitrate=audio.n("bandwidth"),bytes=audio.n("size"),video=false,backupUrls=audioUrls.drop(1)))else emptyList()
    }
    private fun frameRate(value:Any?):Double {
        val parts=value.str().split('/')
        return if(parts.size==2)(parts[0].toDoubleOrNull()?:0.0)/(parts[1].toDoubleOrNull()?.takeIf{it>0}?:1.0)else value.doubleOrNull()?:0.0
    }
}
