package com.luma.downloader.engine

import com.luma.core.*
import com.luma.downloader.auth.SessionVault

/** All parser choices execute on-device. No remote parsing configuration is read. */
class ExtractorRouter(private val vault:SessionVault,private val runtime:MediaRuntime,private val local:com.luma.downloader.engine.local.LocalParseVideoClient, private val onSessionUse:(Platform)->Unit = {}) {
 fun parse(input:String,token:CancelToken,one:Boolean=false,offset:Int=0,engine:String="auto"):Media {
  val url=UrlPolicy.normalize(input);UrlPolicy.checkTransport(url)
  onSessionUse(Platform.of(url))
  val selected=ParseEngine.from(engine)
  if(selected==ParseEngine.PARSE_VIDEO_PY) {
   if(offset>0)throw PlatformError("本地 parse-video-py 不提供通用合集翻页，请改用 yt-dlp", "LOCAL_UNSUPPORTED")
   // Explicit engine selection never silently substitutes the native implementation.
   if(Platform.of(url)==Platform.BILIBILI && (queryParams(url)["p"]?.toIntOrNull()?:1)>1)
    throw PlatformError("当前固定版本 parse-video-py 仅支持 B站首分P；请明确选择原生或 yt-dlp 后重新解析，不会在后台替换引擎。","LOCAL_UNSUPPORTED")
   return local.parse(url,token)
  }
  if(selected==ParseEngine.YTDLP)return runtime.info(url,token,one,offset).copy(extractor=ParseEngine.YTDLP.id)
  val platform=Platform.of(url);var nativeFailure:Exception?=null
  if(offset==0)try {
   val media=NativeExtractors(Http(vault.jar(platform),platform.domestic)).extract(url,token,one)
   if(media!=null&&!manifest(media)&&(media.formats.isNotEmpty()||media.entries.isNotEmpty()))return media.copy(extractor=ParseEngine.NATIVE.id)
  }catch(e:TransferCancelled){throw e}catch(e:Exception){nativeFailure=e}
  if(nativeFailure is PlatformError&&nativeFailure.failureCode in setOf("PREVIEW_ONLY","DRM_PROTECTED","SESSION_REJECTED"))throw nativeFailure
  if(selected==ParseEngine.NATIVE)throw nativeFailure?:PlatformError("原生引擎没有返回可下载内容，请选择其他引擎","NATIVE_UNSUPPORTED")
  token.check()
  try{return runtime.info(url,token,one,offset).copy(extractor=ParseEngine.YTDLP.id)}
  catch(e:TransferCancelled){throw e}catch(e:Exception){if(nativeFailure is PlatformError)throw nativeFailure;throw e}
 }
 private fun manifest(m:Media)=m.formats.any{f->listOf(f.url,f.audioUrl).any{v->val p=runCatching{java.net.URI(v).path.orEmpty().lowercase()}.getOrDefault("");p.endsWith(".m3u8")||p.endsWith(".mpd")}}
}
