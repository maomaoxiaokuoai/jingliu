package package076
import com.luma.core.*
import com.luma.downloader.data.*
import com.luma.downloader.engine.*
import com.luma.downloader.auth.SessionVault
import java.nio.file.Files
object WiringChecks {
 @JvmStatic fun main(args:Array<String>) {
  var count=0
  fun checkCase(name:String,block:()->Unit){block();count++;println("PASS $name")}
  val root=Files.createTempDirectory("jingliu-ledger-").toFile()
  try {
   val context=android.content.Context(root);val store=TaskStore(context);store.load()
   val task=DownloadTask(source="https://example.com/x",title="fixture",platform=Platform.BILIBILI,parserEngine="native",formatId="policy:bili:80",executionId="runA",failureDetails="CONTROLLED_CODE")
   checkCase("legacy tasks default to auto without losing format"){val t=DownloadTask.from(task.json()-setOf("parserEngine","resolvedEngine","failureDetails"));check(t.parserEngine=="auto"&&t.formatId=="policy:bili:80")}
   checkCase("actual task JSON storage roundtrips new fields"){store.add(listOf(task));val reread=TaskStore(context).apply{load()}.get(task.id)!!;check(reread.parserEngine=="native"&&reread.failureDetails=="CONTROLLED_CODE")}
   checkCase("stale worker does not overwrite persisted task"){store.progress(task.id,TransferStage.VIDEO,50,100,2.0,"oldRun");check(store.get(task.id)!!.downloaded==0L)}
   checkCase("current worker writes progress keeping engine selection"){store.progress(task.id,TransferStage.VIDEO,50,100,2.0,"runA");check(store.get(task.id)!!.downloaded==50L&&store.get(task.id)!!.parserEngine=="native")}
   val runtime=MediaRuntime();val service=ParserServiceStore();val router=ExtractorRouter(SessionVault(),runtime,service)
   checkCase("AUTO media direct path stays on-device"){val m=router.parse("https://cdn.example/a.mp4",CancelToken());check(m.extractor=="native"&&service.calls==0&&runtime.calls==0)}
   checkCase("explicit yt-dlp routes only to runtime"){val m=router.parse("https://cdn.example/a.mp4",CancelToken(),engine="ytdlp");check(m.extractor=="ytdlp"&&runtime.calls==1&&service.calls==0)}
   checkCase("explicit fallback routes only to user service"){val m=router.parse("https://www.douyin.com/video/1",CancelToken(),engine="parse_video_py");check(m.extractor=="parse_video_py"&&service.calls==1)}
   checkCase("native unsupported does not call user service"){val e=runCatching{router.parse("https://example.com/no-parser",CancelToken(),engine="native")}.exceptionOrNull();check(e is PlatformError&&e.failureCode=="NATIVE_UNSUPPORTED"&&service.calls==1)}
   checkCase("manifest is refused before raw-file transfer"){service.manifest=true;val e=runCatching{router.parse("https://www.douyin.com/video/1",CancelToken(),engine="parse_video_py")}.exceptionOrNull();check(e is PlatformError&&e.failureCode=="BACKUP_MANIFEST")}
  }finally{root.deleteRecursively()}
  println("WiringChecks: $count cases passed with host Context/engine types; not Android runtime or actual yt-dlp.")
 }
}
