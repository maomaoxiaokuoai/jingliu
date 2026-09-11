package com.luma.core

import java.io.*
import java.net.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class TransferCancelled:IOException("操作已取消")
class HttpFailure(val status:Int):IOException("HTTP $status")
class CancelToken {
    private val stopped=AtomicBoolean(false)
    private val handlers=CopyOnWriteArrayList<()->Unit>()
    val cancelled:Boolean get()=stopped.get()
    fun check(){if(cancelled||Thread.currentThread().isInterrupted)throw TransferCancelled()}
    fun cancel(){if(stopped.compareAndSet(false,true))handlers.forEach{runCatching{it()}}}
    fun listen(block:()->Unit):()->Unit { handlers.add(block);if(cancelled)block();return{handlers.remove(block)} }
}
data class Cookie(val domain:String,val name:String,val value:String,val path:String="/",val secure:Boolean=true,val expires:Long=0,val hostOnly:Boolean=false,val httpOnly:Boolean=false) {
    fun matches(uri:URI,now:Long=System.currentTimeMillis()/1000):Boolean {
        val p=uri.path.orEmpty().ifBlank{"/"}
        val pathMatch=p==path||p.startsWith(path.let{if(it.endsWith('/'))it else "$it/"})
        return (expires==0L||expires>now)&&(!secure||uri.scheme=="https")&&pathMatch&&
            (if(hostOnly)uri.host.equals(domain,true)else domainMatches(uri.host,domain))
    }
    fun json():Map<String,Any?> = mapOf("domain" to domain,"name" to name,"value" to value,"path" to path,"secure" to secure,"expires" to expires,"hostOnly" to hostOnly,"httpOnly" to httpOnly)
    companion object { fun from(v:Any?)=Cookie(v.s("domain"),v.s("name"),v.s("value"),v.s("path").ifBlank{"/"},v.at("secure")!=false,v.n("expires")?:0,v.at("hostOnly").truth(),v.at("httpOnly").truth()) }
}
interface CookieStore {
    fun cookies():List<Cookie>
    fun put(cookies:List<Cookie>)
    fun header(url:String):String { val u=URI(url);return cookies().filter{it.matches(u)}.sortedByDescending{it.path.length}.joinToString("; "){"${it.name}=${it.value}"} }
}
open class MemoryCookies(initial:List<Cookie> = emptyList()):CookieStore {
    private var values=initial
    @Synchronized override fun cookies():List<Cookie> = values.toList()
    @Synchronized override fun put(cookies:List<Cookie>){values=(values+cookies).associateBy{Triple(it.domain,it.path,it.name)}.values.filter{it.expires==0L||it.expires>System.currentTimeMillis()/1000}}
}
object CookieCodec {
    fun parse(text:String,platform:Platform):List<Cookie> {
        require(text.length<=1024*1024){"Cookie 文件过大"}
        val normalized=text.replace("\r\n","\n")
        require(!normalized.contains('\r')){"Cookie 含有非法控制字符"}
        val lines=normalized.lineSequence().map{it.trim()}.filter{it.isNotEmpty()&&(!it.startsWith('#')||it.startsWith("#HttpOnly_"))}.toList()
        val result=if(lines.any{it.contains('\t')})lines.mapNotNull { line ->
            val httpOnly=line.startsWith("#HttpOnly_");val parts=line.removePrefix("#HttpOnly_").split('\t')
            if(parts.size!=7)null else Cookie(parts[0].trimStart('.'),parts[5],parts[6],parts[2],parts[3].equals("true",true),parts[4].toLongOrNull()?:0,!parts[1].equals("true",true),httpOnly)
        } else normalized.trim().removePrefix("Cookie:").also{require(!it.contains('\n')){"请求头内容不能包含换行"}}.split(';').mapNotNull { v ->
            val i=v.indexOf('=');if(i<=0)null else Cookie(platform.domains.firstOrNull()?:error("直链不导入账号"),v.take(i).trim(),v.substring(i+1).trim())
        }
        val safe=result.filter{platform.owns(it.domain)&&it.name.matches(Regex("[!#$%&'*+.^_`|~0-9a-zA-Z-]+"))&&!it.value.any{c->c<' '||c=='\u007f'}&&it.path.startsWith('/')}
        require(safe.isNotEmpty()){ "未发现属于 ${platform.title} 的有效 Cookie；请导入 Netscape cookies.txt 或 Cookie 请求头" }
        return safe
    }
    fun netscape(cookies:List<Cookie>):String = "# Netscape HTTP Cookie File\n"+cookies.joinToString("\n") {
        "${if(it.httpOnly)"#HttpOnly_" else ""}${if(it.hostOnly)"" else "."}${it.domain}\t${if(it.hostOnly)"FALSE" else "TRUE"}\t${it.path}\t${if(it.secure)"TRUE" else "FALSE"}\t${it.expires}\t${it.name}\t${it.value}"
    }+"\n"
    fun received(url:String,headers:List<String>):List<Cookie> {
        val u=URI(url)
        return headers.flatMap { raw -> runCatching { HttpCookie.parse(raw).mapNotNull { h ->
            val d=h.domain?.trimStart('.')?:u.host
            if(!domainMatches(u.host,d)||!d.contains('.'))null else Cookie(d,h.name,h.value,h.path?:"/",h.secure,
                if(h.maxAge<0)0 else System.currentTimeMillis()/1000+h.maxAge,h.domain==null,h.isHttpOnly)
        } }.getOrDefault(emptyList()) }
    }
}
const val USER_AGENT="Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
data class HttpText(val url:String,val body:String,val status:Int,val headers:Map<String,List<String>>)
class Http(val jar:CookieStore=MemoryCookies(),val direct:Boolean=false,private val allowLoopback:Boolean=false) {
    fun open(url:String,token:CancelToken,headers:Map<String,String> = emptyMap(),method:String="GET",body:ByteArray?=null):HttpURLConnection {
        var current=url;var verb=method;var payload=body
        repeat(8) {
            token.check(); val uri=UrlPolicy.checkTransport(current,allowLoopback)
            val c=(if(direct)uri.toURL().openConnection(Proxy.NO_PROXY)else uri.toURL().openConnection()) as HttpURLConnection
            c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=25000;c.requestMethod=verb
            c.setRequestProperty("User-Agent",USER_AGENT);c.setRequestProperty("Accept-Encoding","identity")
            headers.filterKeys{!it.equals("Cookie",true)&&!it.equals("Authorization",true)&&!it.equals("Host",true)}.forEach{(k,v)->require(!v.contains('\n')&&!v.contains('\r'));c.setRequestProperty(k,v)}
            jar.header(current).takeIf{it.isNotEmpty()}?.let{c.setRequestProperty("Cookie",it)}
            val unlisten=token.listen{c.disconnect()}
            try {
                if(payload!=null){c.doOutput=true;c.setFixedLengthStreamingMode(payload!!.size);c.outputStream.use{it.write(payload!!)}}
                val code=c.responseCode
                val received=c.headerFields.entries.filter{it.key?.equals("set-cookie",true)==true}.flatMap{it.value}
                jar.put(CookieCodec.received(current,received))
                if(code in listOf(301,302,303,307,308)) {
                    val next=URI(current).resolve(c.getHeaderField("Location")?:throw IOException("重定向缺少地址")).toString()
                    UrlPolicy.checkTransport(next,allowLoopback)
                    // Never relay a POST body across origins.
                    if(code !in listOf(307,308)||URI(next).host!=uri.host){verb="GET";payload=null}
                    c.disconnect();current=next
                } else return c
            } catch(e:Exception){c.disconnect();token.check();throw e} finally{unlisten()}
        };throw IOException("重定向次数过多")
    }
    fun text(url:String,token:CancelToken=CancelToken(),headers:Map<String,String> = emptyMap(),form:Map<String,String>?=null):HttpText {
        val bytes=form?.entries?.joinToString("&"){enc(it.key)+"="+enc(it.value)}?.toByteArray()
        val c=open(url,token,headers+if(form!=null)mapOf("Content-Type" to "application/x-www-form-urlencoded")else emptyMap(),if(form!=null)"POST" else "GET",bytes)
        val remove=token.listen{c.disconnect()}
        try{
            if(c.responseCode !in 200..299)throw HttpFailure(c.responseCode)
            val buffer=ByteArrayOutputStream();c.inputStream.use { input->val chunk=ByteArray(32768);while(true){token.check();val n=input.read(chunk);if(n<0)break;require(buffer.size()+n<=24*1024*1024){"响应过大"};buffer.write(chunk,0,n)}}
            val rawHeaders:Map<String?,List<String>> = c.headerFields
            val responseHeaders=rawHeaders.entries.mapNotNull { (key,values) -> key?.let {it to values} }.toMap()
            return HttpText(c.url.toString(),buffer.toString("UTF-8"),c.responseCode,responseHeaders)
        }finally{remove();c.disconnect()}
    }
    fun json(url:String,token:CancelToken=CancelToken(),headers:Map<String,String> = emptyMap(),form:Map<String,String>?=null):Any? = Json.parse(text(url,token,headers,form).body)
}
fun enc(s:String):String=URLEncoder.encode(s,"UTF-8").replace("+","%20")
data class ByteProgress(val downloaded:Long,val total:Long?,val speed:Double)

/** Resumes only with a server validator. HTTP 200 during a Range request ALWAYS truncates. */
class ResumableTransfer(private val http:Http) {
    fun download(url:String,destination:File,identity:String,token:CancelToken,headers:Map<String,String> = emptyMap(),onProgress:(ByteProgress)->Unit):File {
        destination.parentFile?.mkdirs()
        val doneFile=File(destination.path+".done.json")
        val doneMeta=runCatching{Json.parse(doneFile.readText())}.getOrNull()
        if(destination.isFile&&destination.length()>0&&doneMeta.s("identity")==identity&&doneMeta.n("bytes")==destination.length()) {
            token.check();onProgress(ByteProgress(destination.length(),destination.length(),0.0));return destination
        }
        val part=File(destination.path+".part");val meta=File(destination.path+".resume.json")
        val previous=runCatching{Json.parse(meta.readText())}.getOrNull()
        var start=part.takeIf{it.exists()}?.length()?:0L
        val validator=previous.s("validator")
        fun preservePartial() {
            if(part.isFile&&part.length()>0) {
                val archive=destination.path+".retained-"+System.nanoTime()
                moveFile(part,File(archive+".part"))
                if(meta.exists())moveFile(meta,File(archive+".resume.json"))
            } else {part.delete();meta.delete()}
        }
        if(previous.s("identity")!=identity||validator.isBlank()){preservePartial();start=0}
        val range=if(start>0)mapOf("Range" to "bytes=$start-","If-Range" to validator)else emptyMap()
        val c=http.open(url,token,headers+range)
        val remove=token.listen{c.disconnect()}
        try {
            val code=c.responseCode
            val contentRange=c.getHeaderField("Content-Range").orEmpty()
            val returnedValidator=c.getHeaderField("ETag")?.takeUnless{it.startsWith("W/")}?:c.getHeaderField("Last-Modified").orEmpty()
            if(start>0 && code in listOf(206,416) && returnedValidator.isNotBlank() && returnedValidator!=validator) {
                preservePartial();throw ResourceVersionChanged()
            }
            val complete=Regex("bytes \\*/(\\d+)").matchEntire(contentRange)?.groupValues?.get(1)?.toLongOrNull()
            if(code==416 && start>0 && complete==start && returnedValidator==validator) { token.check();moveFile(part,destination);meta.delete();atomicText(doneFile,Json.stringify(mapOf("identity" to identity,"bytes" to start)));onProgress(ByteProgress(start,start,0.0));return destination }
            if(code !in listOf(200,206))throw HttpFailure(code)
            if(code==200&&start>0){preservePartial();start=0}
            if(code==206) {
                val r=Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(contentRange)?:throw IOException("Content-Range 格式错误")
                require(r.groupValues[1].toLong()==start){"服务器续传偏移不一致"}
            }
            val type=c.contentType.orEmpty().lowercase();if(type.contains("text/html")||type.contains("application/json")||type.contains("mpegurl")||type.contains("dash+xml"))throw MediaBodyRejected()
            val length=c.getHeaderFieldLong("Content-Length",-1).takeIf{it>=0}
            val total=if(code==206)contentRange.substringAfterLast('/').toLongOrNull() else length
            val newValidator=c.getHeaderField("ETag")?.takeUnless{it.startsWith("W/")}?:c.getHeaderField("Last-Modified").orEmpty()
            atomicText(meta,Json.stringify(mapOf("identity" to identity,"validator" to newValidator)))
            var current=start;var sample=start;var tick=System.nanoTime();var speed=0.0
            c.inputStream.use { input -> RandomAccessFile(part,"rw").use { output ->
                output.setLength(start);output.seek(start)
                val block=ByteArray(128*1024)
                while(true){token.check();val n=input.read(block);if(n<0)break;output.write(block,0,n);current+=n
                    val now=System.nanoTime();if(now-tick>=250_000_000){speed=(current-sample)*1e9/(now-tick);onProgress(ByteProgress(current,total,speed));tick=now;sample=current}}
                output.fd.sync()
            } }
            token.check();if(total!=null&&current!=total)throw IncompleteTransfer()
            require(current>0){"服务器返回空文件"};moveFile(part,destination);meta.delete();atomicText(doneFile,Json.stringify(mapOf("identity" to identity,"bytes" to current)));onProgress(ByteProgress(current,total,speed));return destination
        }finally{remove();c.disconnect()}
    }
}
fun atomicText(file:File,text:String){file.parentFile?.mkdirs();val temp=File(file.path+".tmp");FileOutputStream(temp).use{it.write(text.toByteArray());it.fd.sync()};moveFile(temp,file)}
fun moveFile(from:File,to:File){try{Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}catch(e:java.nio.file.AtomicMoveNotSupportedException){Files.move(from.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING)}}
