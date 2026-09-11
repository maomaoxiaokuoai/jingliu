// TEST-ONLY external API shapes. Not real Android/Chaquopy/yt-dlp libraries.
package com.yausername.youtubedl_android
import android.content.Context
object YoutubeDL {
 fun init(context:Context){}
 fun version(context:Context):String?="fixture"
 enum class UpdateChannel{STABLE}
 fun updateYoutubeDL(context:Context,channel:UpdateChannel)="fixture"
}
