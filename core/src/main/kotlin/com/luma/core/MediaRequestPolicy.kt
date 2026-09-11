package com.luma.core

import java.net.URI

/** Shared by thumbnail requests, preview playback and binary downloads. No raw Cookie in metadata. */
object MediaRequestPolicy {
    fun headers(mediaUrl:String, source:String="", supplied:Map<String,String> = emptyMap()):Map<String,String> {
        val u=URI(mediaUrl);require(u.host!=null&&u.userInfo==null){"媒体 URL 无效"}
        val safe=linkedMapOf<String,String>()
        supplied.forEach { (k,v) ->
            if(k.lowercase() in setOf("user-agent","referer","origin","accept") && v.length<=4096 && !v.contains('\r') && !v.contains('\n'))safe[k]=v
        }
        if(safe.keys.none{it.equals("User-Agent",true)})safe["User-Agent"]=USER_AGENT
        // Use a stable official referer, not the short-link redirect host. Do not attach app cookies here.
        val host=u.host.lowercase()
        val ref=when {
            domainMatches(host,"xhscdn.com") || domainMatches(host,"xiaohongshu.com") -> "https://www.xiaohongshu.com/"
            domainMatches(host,"hdslb.com") || domainMatches(host,"bilivideo.com") || domainMatches(host,"bilivideo.cn") -> "https://www.bilibili.com/"
            domainMatches(host,"kwimgs.com") || domainMatches(host,"kwaicdn.com") || domainMatches(host,"gifshow.com") -> "https://www.kuaishou.com/"
            else -> source.takeIf{runCatching{UrlPolicy.checkTransport(it)}.isSuccess}.orEmpty()
        }
        if(ref.isNotEmpty()) {safe.keys.filter{it.equals("Referer",true)}.toList().forEach{safe.remove(it)};safe["Referer"]=ref}
        return safe
    }
    /** Never generate a CDN address, strip a transform or drop a signature. Candidates must come from the page. */
    fun candidates(primary:String, extras:List<String>):List<String> = (listOf(primary)+extras).filter(String::isNotBlank)
        .mapNotNull{raw->runCatching{secureUrl(raw).also{UrlPolicy.checkTransport(it)}}.getOrNull()}.distinct().take(12)
    fun sameImage(a:String,b:String):Boolean=runCatching {
        val x=URI(a);val y=URI(b)
        if(x.host==y.host && x.rawPath==y.rawPath)return@runCatching true
        fun xhs(u:URI)=domainMatches(u.host.orEmpty(),"xhscdn.com")||domainMatches(u.host.orEmpty(),"xiaohongshu.com")
        // Legacy 0.7.7 guessed ci/notes_pre_post links can refer to the same image id in the real page URL.
        if(xhs(x)&&xhs(y)) {
            val first=x.path.substringAfterLast('/').substringBefore('!')
            val second=y.path.substringAfterLast('/').substringBefore('!')
            first.length>=12 && first==second
        } else false
    }.getOrDefault(false)
}
