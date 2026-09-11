package com.luma.downloader.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.luma.core.*
import com.luma.downloader.data.*
import java.io.File

class MediaPublisher(private val context:Context,private val store:TaskStore) {
    fun mime(ext:String):String=when(ext.lowercase()){ "mp4","mov"->"video/mp4";"mkv"->"video/x-matroska";"webm"->"video/webm";"flv"->"video/x-flv";"mp3"->"audio/mpeg";"m4a"->"audio/mp4";"opus","ogg"->"audio/ogg";"jpg","jpeg"->"image/jpeg";"png"->"image/png";"webp"->"image/webp";"gif"->"image/gif";"avif"->"image/avif";else->"application/octet-stream" }
    fun publish(id:String,source:File,fileName:String,token:CancelToken):String {
        val task=store.get(id)?:error("任务已删除")
        token.check();if(task.finalUri.isNotBlank()&&exists(task.finalUri))return task.finalUri
        require(source.length()>0){"不能保存空文件"}
        task.pendingUri.takeIf{it.isNotBlank()}?.let{deleteUri(it)}
        val type=mime(fileName.substringAfterLast('.'))
        val uri=if(Build.VERSION.SDK_INT>=29) {
            val values=ContentValues().apply {put(MediaStore.MediaColumns.DISPLAY_NAME,fileName);put(MediaStore.MediaColumns.MIME_TYPE,type);put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/镜流");put(MediaStore.MediaColumns.IS_PENDING,1)}
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:error("无法创建媒体库文件")
        } else {
            val root=File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?:context.filesDir,"镜流").apply{mkdirs()}
            FileProvider.getUriForFile(context,context.packageName+".files",File(root,fileName))
        }
        store.update(id){it.copy(pendingUri=uri.toString(),fileName=fileName,mime=type)}
        try {
            context.contentResolver.openOutputStream(uri,"w")?.use { output->
                source.inputStream().use {input->val buffer=ByteArray(128*1024);var count=0L;var tick=System.nanoTime();var sample=0L
                    while(true){token.check();val n=input.read(buffer);if(n<0)break;output.write(buffer,0,n);count+=n
                        val now=System.nanoTime();if(now-tick>=300_000_000){store.progress(id,TransferStage.SAVING,count,source.length(),(count-sample)*1e9/(now-tick));sample=count;tick=now}}
                    output.flush();require(count==source.length()){"媒体库写入不完整"}
                }
            }?:error("无法打开媒体库输出文件")
            token.check()
            if(Build.VERSION.SDK_INT>=29)context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
            token.check()
            store.update(id){old->if(old.stage==TransferStage.DELETING)old.copy(finalUri=uri.toString(),pendingUri="")else old.copy(finalUri=uri.toString(),pendingUri="",downloaded=source.length(),total=source.length())}
            return uri.toString()
        }catch(e:Exception){runCatching{deleteUri(uri.toString())};throw e}
    }
    fun exists(raw:String):Boolean=try {context.contentResolver.openFileDescriptor(Uri.parse(raw),"r")?.use{true}?:false}catch(_:java.io.FileNotFoundException){false}
    fun deleteUri(raw:String) {
        if(raw.isBlank())return
        val uri=Uri.parse(raw)
        require(uri.scheme=="content"&&uri.authority in setOf("media",context.packageName+".files")){"拒绝删除非本应用登记的资源"}
        context.contentResolver.delete(uri,null,null)
        check(!exists(raw)){"系统尚未删除该媒体文件"}
    }
}
