package com.luma.downloader

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.luma.core.*
import com.luma.downloader.auth.LoginQrImageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic QR only; use an unmanaged disposable emulator. Provided, NOT executed here. */
@RunWith(AndroidJUnit4::class)
class QrExportDeviceTest {
    @Test @SdkSuppress(minSdkVersion=29)
    fun savedPngDecodesToTheOriginalPayloadAndIsDeletedAfterTest()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val payload="jingliu-owned-instrumentation-qr-not-a-login"
        val bitmap=LoginQrImageStore.bitmap(payload)
        val guard=QrExportGuard(QrExportStamp(Platform.BILIBILI,"test",QrDisplay.CODE,System.currentTimeMillis()+60_000))
        val expected=LoginQrImageStore.png(bitmap,guard)
        val uri=LoginQrImageStore.saveToGallery(context,expected,guard)
        try {
            val actual=context.contentResolver.openInputStream(uri)!!.use{it.readBytes()}
            assertArrayEquals(expected,actual)
            val decoded=BitmapFactory.decodeByteArray(actual,0,actual.size)
            val pixels=IntArray(decoded.width*decoded.height)
            decoded.getPixels(pixels,0,decoded.width,0,0,decoded.width,decoded.height)
            val source=RGBLuminanceSource(decoded.width,decoded.height,pixels)
            val result=MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))
            assertEquals(payload,result.text)
            decoded.recycle()
        } finally {context.contentResolver.delete(uri,null,null);bitmap.recycle()}
    }
    @Test @SdkSuppress(minSdkVersion=29)
    fun expiredOrRevokedAttemptCannotSave()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap=LoginQrImageStore.bitmap("local-test-only")
        val guard=QrExportGuard(QrExportStamp(Platform.BILIBILI,"test",QrDisplay.CODE,System.currentTimeMillis()+60_000))
        val png=LoginQrImageStore.png(bitmap,guard)
        guard.revoke()
        try {
            val failure=runCatching{LoginQrImageStore.saveToGallery(context,png,guard)}.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
        } finally {bitmap.recycle()}
    }
}
