package com.luma.downloader.engine

import android.content.Context
import com.luma.core.*
import com.luma.downloader.auth.SessionVault
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/** Android-packaged upstream binaries, no Python install/server required. No shell interpolation. */
class MediaRuntime(private val context:Context,private val vault:SessionVault) {
    private val engineLock=ReentrantReadWriteLock()
    @Volatile private var initialized=false
    private val base get()=File(context.noBackupFilesDir,"youtubedl-android")
    private val bins get()=File(context.applicationInfo.nativeLibraryDir)
    private val secrets=File(context.noBackupFilesDir,"engine-cookie-tmp")
    @Synchronized fun init(){
        if(initialized)return
        secrets.mkdirs();secrets.listFiles()?.forEach{it.delete()}
        YoutubeDL.init(context);FFmpeg.init(context)
        check(File(bins,"libpython.so").isFile&&File(bins,"libffmpeg.so").isFile){"当前设备 ABI 的解析组件未正确安装"}
        initialized=true
    }
    fun version():String {init();return YoutubeDL.version(context)?:"内置版本"}
    fun update():String=engineLock.write{init();YoutubeDL.updateYoutubeDL(context,YoutubeDL.UpdateChannel.STABLE).toString()}
    fun info(url:String,token:CancelToken,one:Boolean=false,offset:Int=0):Media {
        val args=mutableListOf("--dump-single-json","--skip-download","--no-warnings")
        if(one)args.add("--no-playlist") else args.addAll(listOf("--flat-playlist","--playlist-start",(offset+1).toString(),"--playlist-end",(offset+100).toString()))
        val output=yt(url,args,token)
        return PlatformJson.yt(Json.parse(output.trim()),url,offset)
    }
    fun downloadStream(source:String,selector:String,output:File,token:CancelToken,onProgress:(ByteProgress)->Unit) {
        require(selector.matches(Regex("[a-zA-Z0-9_.:-]+"))){"格式编号异常；请重新解析"}
        // Download one stream at a time. App owns mux process, so cancel/delete can stop every writer.
        val args=listOf("--no-playlist","--no-simulate","--newline","--continue","--no-mtime","--no-warnings",
            "--fixup","never","--downloader","native","--retries","0","--fragment-retries","0","--abort-on-unavailable-fragments",
            "--format",selector,"--output",output.absolutePath,"--progress-template",
            "download:SHILIU|%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s")
        yt(source,args,token,capture=false){line->
            if(line.startsWith("SHILIU|")) {
                val p=line.split('|');if(p.size>=5){val done=p[1].toDoubleOrNull()?.toLong()?:0;val total=p[2].toDoubleOrNull()?.toLong()?:p[3].toDoubleOrNull()?.toLong();val speed=p[4].toDoubleOrNull()?:0.0
                    onProgress(ByteProgress(done,total,speed))}
            }
        }
        token.check();check(output.isFile&&output.length()>0){"下载引擎未生成完整文件"}
        onProgress(ByteProgress(output.length(),output.length(),0.0))
    }
    fun downloadManifest(media:Media,format:Format,output:File,token:CancelToken,onProgress:(ByteProgress)->Unit) {
        UrlPolicy.checkTransport(format.url)
        require(format.id=="pv-manifest"&&media.extractor==ParseEngine.PARSE_VIDEO_PY.id)
        val args=mutableListOf("--force-generic-extractor","--no-playlist","--no-simulate","--newline",
            "--continue","--fixup","never","--downloader","native","--retries","0","--fragment-retries","0",
            "--abort-on-unavailable-fragments","--format","best","--output",output.absolutePath,
            "--progress-template","download:SHILIU|%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s")
        media.headers["Referer"]?.takeIf{it.startsWith("https://")&&!it.contains('\n')&&!it.contains('\r')}?.let{args.addAll(listOf("--referer",it))}
        media.headers["User-Agent"]?.takeIf{!it.contains('\n')&&!it.contains('\r')}?.let{args.addAll(listOf("--user-agent",it))}
        if(media.platform.domestic)args.addAll(listOf("--proxy",""))
        yt(format.url,args,token,capture=false,cookiePlatform=media.platform){line->
            if(line.startsWith("SHILIU|")) {
                val parts=line.split('|')
                if(parts.size>=5)onProgress(ByteProgress(parts[1].toDoubleOrNull()?.toLong()?:0,
                    parts[2].toDoubleOrNull()?.toLong()?:parts[3].toDoubleOrNull()?.toLong(),parts[4].toDoubleOrNull()?:0.0))
            }
        }
        token.check();check(output.isFile&&output.length()>0){"分段媒体没有生成完整文件"}
    }
    private fun yt(url:String,args:List<String>,token:CancelToken,capture:Boolean=true,cookiePlatform:Platform?=null,line:(String)->Unit={}):String=engineLock.read {
        init();token.check()
        val platform=cookiePlatform?:Platform.of(url)
        val cookies=vault.get(platform)?.cookies.orEmpty()
        val cookieFile=if(cookies.isNotEmpty())File(secrets,UUID.randomUUID().toString()+".txt").apply {
            writeText(CookieCodec.netscape(cookies));setReadable(false,false);setWritable(false,false);setReadable(true,true);setWritable(true,true)
        } else null
        try {
            val command=mutableListOf(File(bins,"libpython.so").absolutePath,File(base,"yt-dlp/yt-dlp").absolutePath,
                "--ignore-config","--no-cache-dir","--socket-timeout","20","--js-runtimes","quickjs:${File(bins,"libqjs.so").absolutePath}",
                "--ffmpeg-location",File(bins,"libffmpeg.so").absolutePath)
            if(platform.domestic)command.addAll(listOf("--proxy",""))
            cookieFile?.let{command.addAll(listOf("--cookies",it.absolutePath))}
            command.addAll(args);command.add("--");command.add(url)
            run(command,token,capture,line)
        }finally{cookieFile?.delete()}
    }
    fun mux(video:File,audio:File,output:File,token:CancelToken) = engineLock.read {
        init();val temp=File(output.parentFile,"muxing."+output.extension)
        val command=mutableListOf(File(bins,"libffmpeg.so").absolutePath,"-nostdin","-hide_banner","-loglevel","error","-y",
            "-i",video.absolutePath,"-i",audio.absolutePath,"-map","0:v:0","-map","1:a:0","-c","copy")
        if(output.extension=="mp4")command.addAll(listOf("-movflags","+faststart"))
        command.add(temp.absolutePath)
        try {run(command,token,false);token.check();require(temp.length()>0){"音视频合并未生成文件"};moveFile(temp,output)}finally{temp.delete()}
    }
    fun remuxManifest(input:File,output:File,token:CancelToken) = engineLock.read {
        init();token.check()
        val temporary=File(output.parentFile,"manifest-remux.mkv")
        try {
            run(listOf(File(bins,"libffmpeg.so").absolutePath,"-nostdin","-hide_banner","-loglevel","error","-y",
                "-i",input.absolutePath,"-map","0:v:0?","-map","0:a:0?","-c","copy",temporary.absolutePath),token,false)
            token.check();check(temporary.length()>0){"分段媒体无损封装未生成文件"};moveFile(temporary,output)
        }finally{temporary.delete()}
    }
    private fun environment(builder:ProcessBuilder) {
        val packages=File(base,"packages")
        builder.environment().apply {
            this["LD_LIBRARY_PATH"]="${File(packages,"python/usr/lib")}:${File(packages,"ffmpeg/usr/lib")}:${File(packages,"aria2c/usr/lib")}"
            this["SSL_CERT_FILE"]=File(packages,"python/usr/etc/tls/cert.pem").absolutePath
            remove("PYTHONPATH")
            this["PYTHONHOME"]=File(packages,"python/usr").absolutePath
            this["HOME"]=File(packages,"python/usr").absolutePath
            this["TMPDIR"]=context.cacheDir.absolutePath
            this["PATH"]=(System.getenv("PATH")?:"")+":"+bins.absolutePath
        }
    }
    private fun run(command:List<String>,token:CancelToken,capture:Boolean,onLine:(String)->Unit={}):String {
        token.check();val builder=ProcessBuilder(command).redirectErrorStream(false);environment(builder)
        val process=builder.start();val stop=token.listen{process.destroy();if(process.isAlive)process.destroyForcibly()}
        val stderr=StringBuilder()
        val reader=Thread {
            runCatching {process.errorStream.bufferedReader().useLines{lines->lines.forEach{synchronized(stderr){if(stderr.length < 32000)stderr.append(it.take(2000)).append('\n')}}}}
        }.apply {isDaemon=true;start()}
        try {
            val output=StringBuilder()
            process.inputStream.bufferedReader().useLines{lines->lines.forEach{line->token.check();onLine(line)
                if(capture){require(output.length + line.length < 24 * 1024 * 1024){"解析结果过大，请缩小合集范围"};output.append(line).append('\n')}}}
            val code=process.waitFor();reader.join(2000);token.check()
            if(code!=0)throw EngineFailure(classify(synchronized(stderr){stderr.toString()}))
            return output.toString()
        }finally{stop();if(process.isAlive)process.destroyForcibly();
            var interrupted=Thread.interrupted()
            while(process.isAlive){try{process.waitFor()}catch(_:InterruptedException){interrupted=true;process.destroyForcibly()}}
            if(interrupted)Thread.currentThread().interrupt()
            process.inputStream.close();process.errorStream.close();process.outputStream.close()}
    }
    private fun classify(message:String):String=when {
        message.contains("403")||message.contains("412")||message.contains("429")->"平台拒绝请求或触发风控；请在官方页面验证后重试，勿反复高频请求。"
        message.contains("cookies",true)||message.contains("sign in",true)||message.contains("login",true)->"需要有效会话或平台验证；请在账号页登录或导入自己的 Cookie。"
        message.contains("DRM",true)->"该资源有 DRM 保护，不能下载。"
        message.contains("timed out",true)||message.contains("resolve",true)||message.contains("network",true)->"网络连接失败；国内平台使用直连，海外平台需处于可访问的网络。"
        message.contains("format",true)->"所选格式当前不可用；请重新解析并选择实际可用画质。"
        message.contains("Unsupported URL",true)->"当前引擎不支持这个链接类型。"
        else->"解析或媒体处理引擎返回错误；可更新引擎后重试。未保存含 Cookie 的原始日志。"
    }
}
class EngineFailure(message:String):Exception(message)
