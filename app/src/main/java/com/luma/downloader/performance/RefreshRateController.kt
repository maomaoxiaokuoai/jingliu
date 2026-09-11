package com.luma.downloader.performance

import android.app.Activity
import android.os.Build
import android.hardware.display.DisplayManager
import android.view.Display

/** Requests a same-resolution mode <=120 Hz. A system request, not a promise of 120 fps. */
object RefreshRateController {
 fun apply(activity:Activity) {
  val display=if(Build.VERSION.SDK_INT>=30)activity.display else activity.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
  val active=display?.mode?:return
  val best=display.supportedModes.filter{it.physicalWidth==active.physicalWidth&&it.physicalHeight==active.physicalHeight&&it.refreshRate<=120.5f}.maxByOrNull{it.refreshRate}?:return
  val attrs=activity.window.attributes
  if(attrs.preferredDisplayModeId!=best.modeId){attrs.preferredDisplayModeId=best.modeId;activity.window.attributes=attrs}
 }
}
