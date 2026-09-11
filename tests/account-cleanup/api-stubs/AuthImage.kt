// TEST-ONLY external API substitute; not Android implementation or device validation.
@file:Suppress("UNUSED_PARAMETER")
package com.luma.downloader.auth
import com.luma.core.QrExportGuard
import android.graphics.Bitmap
import android.net.Uri
import android.content.Context
data class QrTicket(val url:String,val key:String,val expiresAt:Long)
object LoginQrImageStore {
 fun bitmap(code:String)=Bitmap()
 suspend fun png(bitmap:Bitmap,guard:QrExportGuard):ByteArray=byteArrayOf()
 suspend fun saveToGallery(context:Context,bytes:ByteArray,guard:QrExportGuard):Uri=Uri.parse("content://test")
}
