package com.luma.core
/** Alternate port accepted only for a platform media hostname, never a generated mirror. */
object MediaTransportPolicy {
 fun portAllowed(host:String,port:Int):Boolean = port in listOf(-1,443) ||
     port==4483 && listOf("bilivideo.com","bilivideo.cn").any{domainMatches(host,it)}
}
/** Limited public-share fallback; source: pinned parse-video-py commit, documented in SOURCES. */
object DouyinSharePolicy {
 fun id(url:String):String? = runCatching {
  if(Platform.of(url)!=Platform.DOUYIN)return@runCatching null
  (queryParams(url)["modal_id"]?:Regex("/(?:share/)?(?:video|note|slides)/(\\d+)").find(url)?.groupValues?.get(1))
    ?.takeIf{it.matches(Regex("[0-9]{1,30}"))}
 }.getOrNull()
 fun endpoints(id:String):List<String> {
  require(id.matches(Regex("[0-9]{1,30}")))
  val root="https://www.iesdouyin.com/web/api/v2/aweme/slidesinfo/?aweme_ids=%5B$id%5D"
  return listOf(root,root+"&request_source=200")
 }
}
