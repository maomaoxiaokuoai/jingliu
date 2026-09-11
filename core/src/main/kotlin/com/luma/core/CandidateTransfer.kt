package com.luma.core
import java.io.File

/** A separate resume slot per official candidate. Signed query refresh keeps its origin/path identity. */
class CandidateTransfer(private val transfer:ResumableTransfer) {
 fun download(primary:String,backups:List<String>,destination:File,identity:String,token:CancelToken,
  headers:Map<String,String> = emptyMap(),progress:(ByteProgress)->Unit):File {
  val urls=StreamCandidates.ordered(primary,backups)
  if(urls.isEmpty())throw PlatformError("没有可下载的媒体地址","BILI_CDN_ADDRESS")
  val done=runCatching{Json.parse(File(destination.path+".selected.json").readText())}.getOrNull()
  if(destination.isFile&&destination.length()>0&&done.s("identity")==identity&&done.n("bytes")==destination.length()) {
   token.check();progress(ByteProgress(destination.length(),destination.length(),0.0));return destination
  }
  var last:Exception?=null
  for((i,url) in urls.withIndex()) {
   token.check()
   val key=StreamCandidates.identity(identity,url)
   val slot=File(destination.path+".candidate-"+digest(key).take(16))
   // Migrate old partials only when the stored resource identity matches this exact candidate.
   val old=File(destination.path+".resume.json")
   if(!File(slot.path+".part").exists()&&old.exists()&&runCatching{Json.parse(old.readText()).s("identity")==key}.getOrDefault(false)) {
    val previous=File(destination.path+".part");if(previous.exists())moveFile(previous,File(slot.path+".part"))
    moveFile(old,File(slot.path+".resume.json"))
   }
   try {
    val file=transfer.download(url,slot,key,token,MediaRequestPolicy.headers(url,supplied=headers),progress)
    token.check();moveFile(file,destination)
    atomicText(File(destination.path+".selected.json"),Json.stringify(mapOf("identity" to identity,"bytes" to destination.length())))
    return destination
   }catch(e:Exception){token.check();last=e;if(!StreamCandidates.canRetry(e)||i==urls.lastIndex)throw e}
  }
  throw last?:PlatformError("没有可下载的媒体地址","BILI_CDN_ADDRESS")
 }
}
