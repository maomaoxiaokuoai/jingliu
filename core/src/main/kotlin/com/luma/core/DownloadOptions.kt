package com.luma.core

/** Engine ids are persisted with tasks. Changing the current parser never changes an existing task. */
enum class ParseEngine(val id:String,val title:String) {
    AUTO("auto","自动 · 本机原生优先"), NATIVE("native","原生 Kotlin"),
    YTDLP("ytdlp","yt-dlp · 本机通用"), PARSE_VIDEO_PY("parse_video_py","parse-video-py · 手机本地");
    companion object {fun from(id:String)=entries.firstOrNull{it.id==id}?:AUTO}
}
data class QualityOption(val key:String,val title:String,val size:SizeEstimate?=null)
object BatchQuality {
    fun options(sample:Media):List<QualityOption> {
        val videos=sample.formats.filter{it.video&&!it.drm}.sortedWith(compareByDescending<Format>{it.width.toLong()*it.height}.thenByDescending{it.fps})
        val best=sample.best
        val out=mutableListOf(QualityOption("best","最高可用 · 每集独立选择",best?.size(sample.duration)))
        for(f in videos) {
            val key=key(f)?:continue
            if(out.none{it.key==key})out.add(QualityOption(key,f.label.ifBlank{f.display},f.size(sample.duration)))
        }
        audioBest(sample.formats)?.let{out.add(QualityOption("audio","仅音频",it.size(sample.duration)))}
        return out
    }
    fun key(f:Format):String? = if(!f.video)"audio" else when {
        f.qualityCode!=null -> "bili:${f.qualityCode}"
        f.height>0&&f.width>0 -> "short:${minOf(f.width,f.height)}"
        f.height>0 -> "height:${f.height}"
        else->null
    }
    fun select(m:Media,key:String):Format? {
        val available=m.formats.filterNot{it.drm}
        if(key=="best")return m.best
        if(key=="audio")return audioBest(available)
        val matching=when {
            key.startsWith("bili:")->available.filter{it.video&&it.qualityCode==key.substringAfter(':').toIntOrNull()}
            key.startsWith("short:")->available.filter{it.video&&it.width>0&&it.height>0&&minOf(it.width,it.height)==key.substringAfter(':').toIntOrNull()}
            key.startsWith("height:")->available.filter{it.video&&it.height==key.substringAfter(':').toIntOrNull()}
            // Compatibility with old tasks/preferences; these explicitly mean a maximum height.
            key in setOf("1080p","720p")->available.filter{it.video&&it.height>0&&minOf(it.width.takeIf{w->w>0}?:it.height,it.height)<=key.removeSuffix("p").toInt()}
            else->emptyList()
        }
        return m.copy(formats=matching).best
    }
}
