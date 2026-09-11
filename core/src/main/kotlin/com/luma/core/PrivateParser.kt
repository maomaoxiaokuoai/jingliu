package com.luma.core

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.Proxy
import java.util.Base64

/** Separate service credential, never a platform Cookie. Explicit user opt-in only. */
object PrivateParserPolicy {
 fun origin(value:String,allowDebugLocal:Boolean=false):String {
  val raw=value.trim().trimEnd('/');val u=URI(raw)
  require(raw.length<=2048&&u.host!=null&&u.userInfo==null&&u.rawQuery==null&&u.fragment==null&&u.path.orEmpty().isEmpty()){"请填写服务根地址，不带路径、查询串或凭据"}
  val loopback=u.host.lowercase() in setOf("127.0.0.1","localhost","10.0.2.2")
  require(u.scheme=="https"||(allowDebugLocal&&loopback&&u.scheme=="http")){"服务必须使用 HTTPS；仅 Debug 允许本机测试 HTTP"}
  require(u.port==-1||u.port in 1..65535){"服务端口无效"}
  return "${u.scheme}://${u.rawAuthority}"
 }
 fun source(value:String):String {
  val url=UrlPolicy.normalize(value);UrlPolicy.checkTransport(url)
  val p=Platform.of(url)
  if(p !in setOf(Platform.BILIBILI,Platform.DOUYIN,Platform.KUAISHOU,Platform.XIAOHONGSHU,Platform.X))
   throw PlatformError("此备用包装接口未开放当前平台，请使用本机原生或 yt-dlp","BACKUP_PLATFORM")
  if(p==Platform.BILIBILI&&(queryParams(url)["p"]?.toIntOrNull()?:1)>1)
   throw PlatformError("此备用引擎的 B站接口不能保证非首分P正确，请选择原生或 yt-dlp","BACKUP_BILI_PART")
  return url
 }
 fun credentials(username:String,password:String) {
  require(username.length<=512&&password.length<=4096&&(':' !in username)&&!(username+password).any{it<' '||it=='\u007f'}){"服务账号格式无效"}
  require(username.isBlank()==password.isBlank()){"服务用户名与密码需要一起填写，或同时留空"}
 }
}
object ParseVideoResponse {
 fun decode(response:Any?,source:String,local:Boolean=false):Media {
  val code=response.n("code")
  if(code!=null&&code !in listOf(0L,200L))throw PlatformError("备用服务返回错误代码 $code；没有记录原始响应","BACKUP_RESPONSE")
  val d=if(response.obj().containsKey("data"))response.at("data") else response
  if(d.obj().isEmpty())throw PlatformError("备用接口没有返回媒体对象","BACKUP_SCHEMA")
  val id=d.s("id").ifBlank{digest(source)}
  fun checked(raw:String):String {val u=secureUrl(raw);if(u.isBlank())return "";UrlPolicy.checkTransport(u);return u}
  val entries=d.list("images").mapIndexedNotNull { index,item->
   val u=checked(if(item is String)item else item.s("url"));if(u.isBlank())null else MediaEntry("$id-image-$index","图片 ${index+1}",u,checked(item.s("thumbnail")).ifBlank{u},true,item.n("size"),MediaRequestPolicy.candidates(u,item.list("backup_urls").map{it.str()}).drop(1))
  }
  val url=checked(d.s("video_url"));
  val isManifest=url.isNotBlank() && URI(url).path.orEmpty().lowercase().let{it.endsWith(".m3u8")||it.endsWith(".mpd")}
  if(isManifest&&!local)
   throw PlatformError("备用服务只返回分段清单，请改用 yt-dlp","BACKUP_MANIFEST")
val title=d.s("title").ifBlank{"平台返回的媒体"}
  val author=d.at("author");val stats=d.at("statistics")?:d.at("stats")
  val headers=mapOf("Referer" to source,"User-Agent" to USER_AGENT)
  val formats=if(url.isNotBlank()&&entries.isEmpty())listOf(Format(if(isManifest)"pv-manifest" else "pv-source",label=if(local)"源文件 · 本地解析实际返回" else "源文件 · 备用服务实际返回",url=url,engine=isManifest,
   extension=URI(url).path.orEmpty().substringAfterLast('.').lowercase().takeIf{it in setOf("mp4","webm","mov","mkv") }?:"mp4",
   width=(d.n("width")?:0).toInt(),height=(d.n("height")?:0).toInt(),bytes=d.n("size")?.takeIf{it>0},backupUrls=MediaRequestPolicy.candidates(url,d.list("video_backup_urls").map{it.str()}).drop(1)))else emptyList()
  if(formats.isEmpty()&&entries.isEmpty())throw PlatformError("备用接口没有返回可下载的视频或图片","BACKUP_EMPTY")
  return Media(id,source,Platform.of(source),title,d.s("description").ifBlank{title},runCatching{checked(d.s("cover_url"))}.getOrDefault("").ifBlank{entries.firstOrNull()?.thumbnail.orEmpty()},
   Author(author.s("uid"),author.s("name"),runCatching{checked(author.s("avatar"))}.getOrDefault(""),author.n("followers")),
   Stats(stats.n("views"),stats.n("likes"),stats.n("favorites"),stats.n("comments"),stats.n("shares")),
   duration=d.at("duration").doubleOrNull(),kind=if(entries.isNotEmpty())Kind.GALLERY else Kind.VIDEO,
   formats=formats,entries=entries,headers=headers,
   notice=if(local)"由手机内置 parse-video-py 库返回；未经过解析服务器。只提供一个源文件时不会虚构多档画质；缺失统计保持未知。"+(if(d.list("images").any{it.s("live_photo_url").isNotBlank()})" 动态照片当前仅保存静态图片。" else "") else "由你配置的 parse-video-py 服务返回；没有转发本机平台 Cookie。缺失的画质、大小和统计保持未知。",
   extractor=ParseEngine.PARSE_VIDEO_PY.id)
 }
}
/** No redirects, cookies or shared platform HTTP jar. Authentication goes only to the configured origin. */
class PrivateParserClient(private val origin:String,private val username:String="",private val password:String="",private val allowDebugLocal:Boolean=false) {
 fun parse(source:String,token:CancelToken):Media {
  val normalized=PrivateParserPolicy.source(source)
  val address=PrivateParserPolicy.origin(origin,allowDebugLocal)
  PrivateParserPolicy.credentials(username,password);token.check()
  val connection=URI(address+"/video/share/url/parse?url="+enc(normalized)).toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
  connection.instanceFollowRedirects=false;connection.connectTimeout=15000;connection.readTimeout=65000
  connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("Accept-Encoding","identity")
  if(username.isNotBlank())connection.setRequestProperty("Authorization","Basic "+Base64.getEncoder().encodeToString((username+":"+password).toByteArray(Charsets.UTF_8)))
  val remove=token.listen{connection.disconnect()}
  try {
   val code=connection.responseCode
   if(code in 300..399)throw PlatformError("备用服务地址发生跳转，已拒绝转发服务认证，请填写最终地址","BACKUP_REDIRECT")
   if(code==401)throw PlatformError("备用服务用户名或密码不正确；与平台登录 Cookie 无关","BACKUP_AUTH")
   if(code !in 200..299)throw PlatformError("备用服务 HTTP $code，请检查自建服务日志（不要上传凭据）","BACKUP_HTTP")
   val out=ByteArrayOutputStream()
   connection.inputStream.use { input->val buf=ByteArray(16384);while(true){token.check();val n=input.read(buf);if(n<0)break
    if(out.size()+n>8*1024*1024)throw PlatformError("备用响应超过大小限制","BACKUP_TOO_LARGE");out.write(buf,0,n)} }
   token.check();return ParseVideoResponse.decode(Json.parse(out.toString("UTF-8")),normalized)
  } catch(e:Exception){token.check();throw e} finally{remove();connection.disconnect()}
 }
}
