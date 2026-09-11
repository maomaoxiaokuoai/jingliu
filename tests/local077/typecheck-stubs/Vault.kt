// TEST-ONLY external API shapes. Not real Android/Chaquopy/yt-dlp libraries.
package com.luma.downloader.auth
import com.luma.core.*
data class Account(val cookies:List<Cookie>)
class SessionVault {fun get(platform:Platform):Account?=null;fun jar(platform:Platform)=MemoryCookies()}
