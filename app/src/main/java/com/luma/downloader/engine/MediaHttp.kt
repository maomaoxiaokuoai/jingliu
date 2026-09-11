package com.luma.downloader.engine

import android.content.Context
import coil.ImageLoader
import com.luma.core.*
import com.luma.downloader.AppGraph
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One connection pool and memory image cache. Credentials are evaluated on the final request
 * URL after every redirect; Cookie/Authorization are never carried across origins blindly. */
object MediaHttp {
 @Volatile private var http:OkHttpClient?=null
 @Volatile private var images:ImageLoader?=null
 fun client(context:Context):OkHttpClient=http?:synchronized(this) {
  http?:OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
   .followRedirects(true).followSslRedirects(false).retryOnConnectionFailure(true)
   .cookieJar(CookieJar.NO_COOKIES)
   .addNetworkInterceptor {chain->
    val request=chain.request();val address=request.url.toString()
    try {UrlPolicy.checkTransport(address)}catch(_:Exception){throw IOException("媒体地址不符合安全策略")}
    val builder=request.newBuilder().removeHeader("Cookie").removeHeader("Authorization")
    MediaRequestPolicy.headers(address,supplied=request.headers.names().associateWith{request.header(it).orEmpty()}).forEach{(k,v)->builder.header(k,v)}
    val p=Platform.of(address)
    if(p!=Platform.DIRECT) {
     val cookie=runCatching{AppGraph.get(context.applicationContext).vault.jar(p).header(address)}.getOrDefault("")
     if(cookie.isNotBlank())builder.header("Cookie",cookie)
    }
    chain.proceed(builder.build())
   }.build().also{http=it}
 }
 fun imageLoader(context:Context):ImageLoader=images?:synchronized(this) {
  images?:ImageLoader.Builder(context.applicationContext).okHttpClient(client(context)).crossfade(true)
   // Do not persist signed artwork or account-dependent media into a disk cache.
   .diskCache(null).build().also{images=it}
 }
 fun clearImageMemory(){images?.memoryCache?.clear()}
}
