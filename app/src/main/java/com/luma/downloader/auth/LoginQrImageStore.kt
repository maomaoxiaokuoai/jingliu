package com.luma.downloader.auth

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.luma.core.QrExportGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Only user-confirmed QR pixels are exported. Never a screenshot, Cookie, token, or profile file. */
object LoginQrImageStore {
    fun bitmap(code:String):Bitmap {
        require(code.isNotBlank() && code.length<=8192)
        val size=768
        val matrix=MultiFormatWriter().encode(code,BarcodeFormat.QR_CODE,size,size,mapOf(EncodeHintType.MARGIN to 4))
        val pixels=IntArray(size*size){i->if(matrix[i%size,i/size])android.graphics.Color.BLACK else android.graphics.Color.WHITE}
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888)
    }
    suspend fun png(bitmap:Bitmap,guard:QrExportGuard):ByteArray=withContext(Dispatchers.Default) {
        currentCoroutineContext().ensureActive();guard.requireValid()
        val bytes=ByteArrayOutputStream().use {out->check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray()}
        currentCoroutineContext().ensureActive();guard.requireValid();bytes
    }
    fun fileName()="Jingliu-Login-QR-${UUID.randomUUID()}.png"

    /** API 29+ adds an image owned by this app: no broad photo-read or storage permission. */
    @RequiresApi(29)
    suspend fun saveToGallery(context:Context,bytes:ByteArray,guard:QrExportGuard):Uri {
        check(Build.VERSION.SDK_INT>=29)
        var created:Uri?=null
        try {
            return withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive();guard.requireValid();validatePng(bytes)
                val values=ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME,fileName())
                    put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/镜流/登录二维码")
                    put(MediaStore.Images.Media.IS_PENDING,1)
                }
                val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
                    ?:error("无法建立二维码图片")
                created=uri
                write(context,uri,bytes,guard)
                currentCoroutineContext().ensureActive();guard.requireValid()
                check(context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.Images.Media.IS_PENDING,0)},null,null)>0){"二维码图片未能发布"}
                currentCoroutineContext().ensureActive();guard.requireValid()
                uri
            }
        } catch(e:Throwable) {
            withContext(NonCancellable+Dispatchers.IO){created?.let{runCatching{context.contentResolver.delete(it,null,null)}}}
            throw e
        }
    }

    private suspend fun write(context:Context,uri:Uri,bytes:ByteArray,guard:QrExportGuard) {
        check(uri.scheme=="content"){"保存位置必须来自系统文件选择器或媒体库"}
        context.contentResolver.openOutputStream(uri,"w")?.use {out->
            for(start in bytes.indices step 16384){currentCoroutineContext().ensureActive();guard.requireValid();out.write(bytes,start,minOf(16384,bytes.size-start))}
            out.flush();currentCoroutineContext().ensureActive();guard.requireValid()
        }?:error("无法写入二维码文件")
    }
    private fun validatePng(bytes:ByteArray) {
        val signature=byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)
        require(bytes.size in 8..(4*1024*1024) && bytes.take(8).toByteArray().contentEquals(signature)){"不是有效的二维码 PNG 图片"}
    }
}
