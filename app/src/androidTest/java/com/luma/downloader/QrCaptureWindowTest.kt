package com.luma.downloader

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luma.downloader.data.UiSettings
import com.luma.downloader.data.appearance
import com.luma.downloader.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Window flag assertions, not screenshot bypass. Provided but not run without Android. */
@RunWith(AndroidJUnit4::class)
class QrCaptureWindowTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun qrCanCaptureWhileCookieEditorAndPreexistingPolicyStayProtected() {
        val shown=mutableStateOf(false)
        val secure=mutableStateOf(false)
        ui.setContent {
            val settings=remember{UiSettings().withValue("motion","reduce")}
            val overlay=remember{GlassOverlayState()}
            CompositionLocalProvider(LocalUiSettings provides settings,LocalGlassOverlay provides overlay) {
                LumaTheme(settings.appearance()) {
                    Box(Modifier.fillMaxSize()) {
                        if(shown.value)GlassAlertDialog(secure=secure.value,onDismissRequest={shown.value=false},title={Text("window-test")},confirmButton={Text("test")})
                        GlassOverlayHost(overlay,Modifier.fillMaxSize())
                    }
                }
            }
        }
        fun isSecure()=(ui.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)!=0
        ui.runOnIdle {assertFalse(isSecure());shown.value=true}
        ui.waitForIdle();ui.runOnIdle{assertFalse(isSecure());secure.value=true}
        ui.waitForIdle();ui.runOnIdle{assertTrue(isSecure());shown.value=false}
        ui.waitForIdle();ui.runOnIdle{assertFalse(isSecure());ui.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);secure.value=false;shown.value=true}
        ui.waitForIdle();ui.runOnIdle{assertTrue(isSecure());shown.value=false}
        ui.waitForIdle();ui.runOnIdle{assertTrue(isSecure());ui.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
    }
}
