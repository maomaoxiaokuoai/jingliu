package host078
import android.content.Context
import com.luma.core.*
import com.luma.downloader.data.*
import java.nio.file.Files
import java.io.File
import kotlinx.coroutines.*

fun main() {
 val dir=Files.createTempDirectory("jingliu-store078-").toFile();val context=Context(dir)
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);var passed=0
 fun test(name:String,body:()->Unit){body();passed++;println("PASS $name")}
 try {
  val s=SettingsStore(context,scope)
  atomicText(File(dir,"settings-v7.json"),Json.stringify(mapOf("retry" to "3","theme" to "dark","_visualSchema" to "3")))
  s.load()
  test("retry migration goes to maximum without resetting theme"){check(s.current().number("retry")==10f&&s.current().text("theme")=="dark")}
  s.set("retry","2");s.flush()
  test("explicit retry choice survives reload"){val next=SettingsStore(context,scope);next.load();check(next.current().number("retry")==2f)}
  val tasks=TaskStore(context);tasks.load();val task=DownloadTask(source="https://example.com/own",title="test fixture",platform=Platform.DIRECT,executionId="run-a")
  tasks.add(listOf(task));tasks.progress(task.id,TransferStage.VIDEO,1,100,1.0,"run-a")
  val file=File(dir,"tasks-v7.json");val before=file.readText()
  tasks.progress(task.id,TransferStage.VIDEO,20,100,1.0,"run-a")
  test("progress updates UI memory without synchronous JSON rewrite"){check(tasks.get(task.id)?.downloaded==20L&&file.readText()==before)}
  tasks.flush()
  test("flush persists latest byte progress"){val next=TaskStore(context);next.load();check(next.get(task.id)?.downloaded==20L)}
  val previous=tasks.get(task.id)
  tasks.progress(task.id,TransferStage.VIDEO,40,100,2.0,"run-b")
  test("old execution cannot mutate current progress"){check(tasks.get(task.id)==previous)}
  tasks.update(task.id){it.copy(stage=TransferStage.PAUSED)}
  tasks.progress(task.id,TransferStage.VIDEO,50,100,2.0,"run-a")
  test("late progress cannot resurrect paused task"){check(tasks.get(task.id)?.stage==TransferStage.PAUSED)}
  test("critical pause status is immediately on disk"){val next=TaskStore(context);next.load();check(next.get(task.id)?.stage==TransferStage.PAUSED)}
 }finally{scope.cancel();dir.deleteRecursively()}
 println("$passed file-store checks passed, real file IO with an Android Context test substitute; not Keystore or Android lifecycle testing.")
}
