@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.luma.downloader.preview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import com.luma.core.*
import com.luma.downloader.AppGraph
import com.luma.downloader.data.appearance
import com.luma.downloader.engine.MediaHttp
import com.luma.downloader.ui.*
import java.util.UUID

/** A short-lived private in-memory ticket, never a signed URL or Cookie in an Intent or bundle. */
private data class PreviewTicket(val media:Media,val image:MediaEntry?,val format:Format?,val created:Long=System.nanoTime())
object MediaPreview {
 private val pending=linkedMapOf<String,PreviewTicket>()
 @Synchronized private fun put(ticket:PreviewTicket):String {
  pending.entries.removeAll{System.nanoTime()-it.value.created>900_000_000_000L}
  while(pending.size>=4)pending.remove(pending.keys.first())
  val key=UUID.randomUUID().toString();pending[key]=ticket;return key
 }
 @Synchronized internal fun get(key:String):PreviewData?=pending[key]?.takeIf{System.nanoTime()-it.created<=900_000_000_000L}?.let{PreviewData(it.media,it.image,it.format)}
 @Synchronized internal fun remove(key:String){pending.remove(key)}
 fun image(context:Context,media:Media,entry:MediaEntry){open(context,PreviewTicket(media,entry,null))}
 fun video(context:Context,media:Media,format:Format?){format?.takeUnless{it.drm}?.let{open(context,PreviewTicket(media,null,it))}}
 private fun open(context:Context,t:PreviewTicket){val id=put(t);try{context.startActivity(Intent(context,MediaPreviewActivity::class.java).putExtra("ticket",id))}catch(e:Exception){remove(id);throw e}}
}
internal data class PreviewData(val media:Media,val image:MediaEntry?,val format:Format?)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MediaPreviewActivity:ComponentActivity() {
 private var ticket=""
 private var data:PreviewData?=null
 private var player by mutableStateOf<ExoPlayer?>(null)
 private var failure by mutableStateOf("")
 private var videoCandidate=0
 private var position=0L
 private var shouldPlay=true
 private var started=false
 private var playbackGeneration=0
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState);enableEdgeToEdge()
  ticket=intent.getStringExtra("ticket").orEmpty();data=MediaPreview.get(ticket)
  setContent {
   val settings by AppGraph.get(this).settings.flow.collectAsState()
   CompositionLocalProvider(LocalUiSettings provides settings){LumaTheme(settings.appearance()) {
    GlassWindow {
    Scaffold(containerColor=androidx.compose.ui.graphics.Color.Transparent,topBar={GlassTopAppBar(title={Text(if (data?.image != null) "图片预览" else "视频预览")},navigationIcon={GlassTextButton(onClick={finish()}){Text("关闭")}})}) {padding->
     val d=data
     if(d==null)Box(Modifier.padding(padding).padding(24.dp)){Text("预览信息已失效。返回解析页重新打开，不会丢失下载任务。")}
     else Column(Modifier.padding(padding).fillMaxSize()) {
      if(d.image!=null) {
       var scale by remember{mutableFloatStateOf(1f)};var x by remember{mutableFloatStateOf(0f)};var y by remember{mutableFloatStateOf(0f)}
       val transform=rememberTransformableState{zoom,pan,_->scale=(scale*zoom).coerceIn(1f,4f);if(scale==1f){x=0f;y=0f}else{x+=pan.x;y+=pan.y}}
       Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().transformable(transform)) {
        RemoteImage(d.image.url,d.image.title,Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=x;translationY=y},
         d.media.url,true,d.media.headers,d.image.backupUrls)
       }
       GlassTextButton(onClick={scale=1f;x=0f;y=0f},modifier=Modifier.fillMaxWidth()){Text("恢复图片位置")}
      }else {
       AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(),factory={c->PlayerView(c).apply{useController=true;keepScreenOn=true;player=this@MediaPreviewActivity.player}},update={it.player=player})
       if(failure.isNotBlank()) {Text(failure,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error);GlassTextButton(onClick={videoCandidate=0;startPlayer()}){Text("重试预览")}}
       Text("播放来自平台当前返回的源流；预览兼容性与下载结果分别处理。",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)
      }
     }
    }
   }}}
  }
 }
 override fun onStart(){super.onStart();started=true;startPlayer()}
 private fun startPlayer() {
  if(!started)return
  val d=data?:return;val f=d.format?:return
  releasePlayer()
  try {
   val candidates=MediaRequestPolicy.candidates(f.url,f.backupUrls)
   val url=candidates.getOrNull(videoCandidate)?:throw IllegalStateException("没有可播放地址")
   UrlPolicy.checkTransport(url)
   val factory=OkHttpDataSource.Factory(MediaHttp.client(this)).setDefaultRequestProperties(MediaRequestPolicy.headers(url,d.media.url,d.media.headers))
   val sources=DefaultMediaSourceFactory(factory)
   val video=sources.createMediaSource(MediaItem.fromUri(url))
   val merged=if(f.audioUrl.isNotBlank()) {
    UrlPolicy.checkTransport(f.audioUrl)
    val audioFactory=OkHttpDataSource.Factory(MediaHttp.client(this)).setDefaultRequestProperties(MediaRequestPolicy.headers(f.audioUrl,d.media.url,d.media.headers))
     MergingMediaSource(true,true,video,DefaultMediaSourceFactory(audioFactory).createMediaSource(MediaItem.fromUri(f.audioUrl)))
   }else video
   val next=ExoPlayer.Builder(this).build()
   val generation=playbackGeneration
   next.addListener(object:Player.Listener {
    override fun onPlayerError(error:PlaybackException) {
     if(generation!=playbackGeneration||!started)return
     // Only try a platform-supplied alternative; no fabricated host or lower-quality stream.
     if(error.errorCode in 2000..2999&&videoCandidate+1<candidates.size){videoCandidate++;window.decorView.post{if(started&&!isFinishing&&generation==playbackGeneration)startPlayer()}}
     else failure="预览失败（${error.errorCodeName}）。可能是源地址过期、节点拒绝或设备解码不兼容，可重新解析；不把它当成未登录。"
    }
   })
   player=next;failure="";next.setMediaSource(merged);next.seekTo(position);next.playWhenReady=shouldPlay;next.prepare()
  }catch(_:Exception){releasePlayer();failure="无法准备当前预览资源，请返回后重新解析。"}
 }
 private fun releasePlayer(){playbackGeneration++;player?.let{position=it.currentPosition;shouldPlay=it.playWhenReady;it.release()};player=null}
 override fun onStop(){started=false;releasePlayer();super.onStop()}
 override fun onDestroy(){releasePlayer();if(isFinishing)MediaPreview.remove(ticket);super.onDestroy()}
}
