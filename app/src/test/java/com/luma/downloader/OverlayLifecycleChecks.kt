package com.luma.downloader
import com.luma.downloader.data.OverlayLifecycle
import com.luma.downloader.data.MenuPolicy
object OverlayLifecycleChecks {
 @JvmStatic fun main(args:Array<String>){run()}
 fun run(){var n=0
  fun test(name:String,body:()->Unit){body();n++;println("PASS overlay lifecycle: $name")}
  test("closed does not accept input"){check(!OverlayLifecycle().acceptsInput)}
  test("opening has a generation and becomes open"){val s=OverlayLifecycle();val a=s.open();check(a>0&&s.acceptsInput);check(s.opened(a)&&s.phase==OverlayLifecycle.Phase.OPEN)}
  test("a stale open completion cannot reopen a closing menu"){val s=OverlayLifecycle();val a=s.open();s.close();check(!s.opened(a)&&!s.acceptsInput)}
  test("close blocks a second selection immediately"){val s=OverlayLifecycle();s.open();check(s.close()!=null);check(s.close()==null&&!s.acceptsInput)}
  test("close can complete only once"){val s=OverlayLifecycle();s.open();val a=s.close()!!;check(s.closed(a));check(!s.closed(a))}
  test("quick reopen invalidates delayed old close"){val s=OverlayLifecycle();s.open();val old=s.close()!!;val current=s.open();check(!s.closed(old)&&s.acceptsInput);check(s.opened(current))}
  test("disposal invalidates in-flight callbacks"){val s=OverlayLifecycle();val old=s.open();s.dispose();check(!s.opened(old));check(s.phase==OverlayLifecycle.Phase.CLOSED)}
  test("disposal prevents completion action after route removal"){val s=OverlayLifecycle();s.open();val old=s.close()!!;s.dispose();check(!s.closed(old))}
  test("generation remains strictly monotonic through repeated reversals"){
   val s=OverlayLifecycle();var previous=0L;repeat(50){val a=s.open();check(a>previous);val b=s.close()!!;check(b>a);previous=b;check(s.closed(b))}
  }
  test("scale bounded during spring overshoot"){listOf(-1f,0f,.5f,1f,1.3f,Float.NaN).forEach{val f=MenuPolicy.frame(it,true);check(f.scale in .92f..1.02f)}}
  test("menu geometry deterministic with no extra layout callback"){
   val a=com.luma.downloader.data.MenuAnchor(30,120,360,180)
   check(MenuPolicy.place(a,400,800,320,200,false,10,6,14)==MenuPolicy.place(a,400,800,320,200,false,10,6,14))
  }
  println("Overlay lifecycle checks: $n passed, 0 failed; no GPU/render verification")
 }
}
