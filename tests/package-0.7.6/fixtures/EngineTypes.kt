package com.luma.downloader.engine
import com.luma.core.*
class MediaRuntime {
 var calls=0
 fun info(url:String,token:CancelToken,one:Boolean=false,offset:Int=0):Media {token.check();calls++;return Media("engine",url,Platform.of(url),"fixture",formats=listOf(Format("e",url="https://cdn.example/file.mp4",engine=true)))}
}
class ParserServiceStore {
 var calls=0;var manifest=false
 fun parse(url:String,token:CancelToken):Media {token.check();calls++;return Media("service",url,Platform.of(url),"fixture",formats=listOf(Format("source",url=if(manifest)"https://cdn.example/list.m3u8" else "https://cdn.example/a.mp4")),extractor="parse_video_py")}
}
