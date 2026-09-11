package com.luma.core

import java.io.IOException
import java.io.InterruptedIOException
import java.net.*
import javax.net.ssl.SSLException

class MediaBodyRejected:IOException("服务器返回的不是完整媒体内容")
class ResourceVersionChanged:IOException("续传资源版本已变化，旧片段保留，下一次完整重新下载")
class IncompleteTransfer:IOException("连接提前结束，已有片段已保留")
object TransferRecoveryPolicy {
    const val DEFAULT_RETRIES=10
    fun retryable(e:Throwable):Boolean=when(e) {
        is TransferCancelled,is MediaBodyRejected,is SSLException,is ProtocolException,is java.io.FileNotFoundException -> false
        is HttpFailure -> e.status in setOf(408,429,500,502,503,504)
        is ResourceVersionChanged,is IncompleteTransfer,is SocketException,is SocketTimeoutException,is UnknownHostException,is ConnectException,is java.io.EOFException,is InterruptedIOException -> true
        else -> false
    }
    fun refreshAddress(e:Throwable)=e is HttpFailure && e.status in setOf(401,403,410)
}
