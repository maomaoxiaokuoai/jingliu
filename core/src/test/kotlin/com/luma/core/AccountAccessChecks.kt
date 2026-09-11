package com.luma.core

/** Pure states; does not claim a live login, MediaStore or Android UI test. */
object AccountAccessChecks {
 @JvmStatic fun main(args:Array<String>) { run() }
 fun run() {
  var n=0
  fun test(label:String,body:()->Unit){body();n++;println("PASS $label")}
  val now=10_000L
  fun stamp(p:Platform=Platform.BILIBILI,k:QrDisplay=QrDisplay.CODE,expiry:Long=20_000L,id:String="local-test-code")=QrExportStamp(p,id,k,expiry)
  test("only Bili has a download-session QR path"){check(Platform.entries.filter(AccountAccessPolicy::hasDownloadQr)==listOf(Platform.BILIBILI))}
  test("bridged identities not download-session QR"){for(p in QrCapability.bridged){check(AccountAccessPolicy.hasIdentityQr(p));check(!AccountAccessPolicy.hasDownloadQr(p))}}
  test("unconfigured help never claims ready"){check(AccountAccessPolicy.identityHelp(false).contains("尚未配置"));check(AccountAccessPolicy.identityHelp(false).contains("不提供下载 Cookie"))}
  test("configured identity still not Cookie"){check(AccountAccessPolicy.identityHelp(true).contains("不会生成下载 Cookie"))}
  test("native launch explicitly denies SDK authorization"){for(p in Platform.entries)check(AccountAccessPolicy.nativeLaunchHelp(p).contains("不会发起官方 SDK 授权"))}
  test("Bili help describes real session polling"){check(AccountAccessPolicy.downloadHelp(Platform.BILIBILI).contains("轮询"))}
  test("manual platforms not claimed automatic"){for(p in Platform.entries.filter{it!=Platform.BILIBILI})check(AccountAccessPolicy.downloadHelp(p).contains("尚未"))}
  test("live Bili code exportable"){check(QrPresentationPolicy.canExport(stamp(),AuthorizationPhase.WAITING,now))}
  test("scanned live code exportable"){check(QrPresentationPolicy.canExport(stamp(),AuthorizationPhase.SCANNED,now))}
  test("live TikTok raw code exportable"){check(QrPresentationPolicy.canExport(stamp(p=Platform.TIKTOK),AuthorizationPhase.WAITING,now))}
  test("website URL never made into fake QR"){check(!QrPresentationPolicy.canExport(stamp(k=QrDisplay.OFFICIAL_PAGE),AuthorizationPhase.WAITING,now))}
  test("empty identity not exportable"){check(!QrPresentationPolicy.canExport(stamp(id=""),AuthorizationPhase.WAITING,now))}
  test("expires exactly at boundary"){check(!QrPresentationPolicy.canExport(stamp(expiry=now),AuthorizationPhase.WAITING,now))}
  test("past code not exportable"){check(!QrPresentationPolicy.canExport(stamp(expiry=now-1),AuthorizationPhase.WAITING,now))}
  test("verifying and terminal states cannot export"){for(p in AuthorizationPhase.entries.filterNot(QrPresentationPolicy::active))check(!QrPresentationPolicy.canExport(stamp(),p,now))}
  test("success and failure code hidden"){for(p in listOf(AuthorizationPhase.CONFIRMED,AuthorizationPhase.EXPIRED,AuthorizationPhase.DENIED,AuthorizationPhase.CANCELLED,AuthorizationPhase.FAILED))check(!QrPresentationPolicy.canShow(p,20_000,now))}
  test("display expires even during slow polling"){check(!QrPresentationPolicy.canShow(AuthorizationPhase.WAITING,now,now))}
  test("countdown rounds up"){check(QrPresentationPolicy.remainingSeconds(now+1,now)==1L);check(QrPresentationPolicy.remainingSeconds(now+1000,now)==1L);check(QrPresentationPolicy.remainingSeconds(now+1001,now)==2L)}
  test("countdown zero after expiry"){check(QrPresentationPolicy.remainingSeconds(now-100,now)==0L)}
  test("fresh export guard valid"){check(QrExportGuard(stamp()){now}.valid())}
  test("expired guard rejected"){check(!QrExportGuard(stamp(expiry=now)){now}.valid())}
  test("revoked guard aborts save"){val g=QrExportGuard(stamp()){now};g.revoke();check(!g.valid());check(runCatching{g.requireValid()}.isFailure)}
  test("confirmation revokes in-flight save"){val g=QrExportGuard(stamp()){now};g.phase(AuthorizationPhase.CONFIRMED);check(!g.valid())}
  test("late waiting cannot revive old attempt"){val g=QrExportGuard(stamp()){now};g.phase(AuthorizationPhase.FAILED);g.phase(AuthorizationPhase.WAITING);check(!g.valid())}
  test("new attempt independent from old guard"){val old=QrExportGuard(stamp()){now};old.revoke();val new=QrExportGuard(stamp(id="new-code")){now};check(!old.valid()&&new.valid())}
  test("clock invalidates before a UI recomposition"){var t=now;val g=QrExportGuard(stamp()){t};check(g.valid());t=20_000;check(!g.valid())}
  test("threaded close visible to writer"){val g=QrExportGuard(stamp()){now};val t=Thread{g.revoke()};t.start();t.join();check(!g.valid())}
  println("Account access and QR export policy: $n checks passed. Not a device or live login test.")
 }
}
