package com.luma.downloader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luma.downloader.data.UiSettings
import com.luma.downloader.data.appearance
import com.luma.downloader.ui.*
import com.luma.downloader.ui.optics.rememberGlassSource
import com.luma.downloader.ui.optics.captureGlass
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** In-window lifecycle/rendering device tests. Supplied, NOT executed in this environment.
 * They do not establish pixel-perfect/native-iOS appearance or frame-time guarantees. */
@RunWith(AndroidJUnit4::class)
class MenuRenderingTest {
    @get:Rule val ui = createComposeRule()
    private val backdrop = mutableStateOf(Color.Red)
    private val open = mutableStateOf(false)
    private val selection = mutableIntStateOf(0)
    private var calls = 0
    private var sourceAreas:()->Int = {0}

    private fun content(dark: Boolean = false, options: Int = 3) {
        val s = UiSettings().withValue("theme", if (dark) "dark" else "light").withValue("glassMenus","false")
        ui.setContent {
            val source = rememberGlassSource()
            val overlays=remember{GlassOverlayState()}
            SideEffect {sourceAreas={source.areas.size}}
            CompositionLocalProvider(LocalUiSettings provides s, LocalContentHaze provides source, LocalBackdropHaze provides source, LocalGlassOverlay provides overlays) {
                LumaTheme(s.appearance()) {
                    Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(backdrop.value).captureGlass(source).hideBehindOverlay(overlays.visible)) {
                        Box(Modifier.padding(start = 24.dp, top = 92.dp)) {
                            Button(onClick = { open.value = true }, modifier = Modifier.testTag("menu-anchor")) { Text("打开选择") }
                            SpringDropdownMenu(open.value, { open.value = false }, title = "测试选项") {
                                repeat(options) { index ->
                                    SpringMenuItem(selected = selection.intValue == index, onClick = {
                                        calls++; selection.intValue = index; open.value = false
                                    }, modifier = Modifier.testTag("menu-option-$index")) { Text("选项 $index") }
                                    if (index != options - 1) SpringMenuDivider()
                                }
                            }
                        }
                    }
                    GlassOverlayHost(overlays,Modifier.matchParentSize())
                    }
                }
            }
        }
        ui.onNodeWithTag("menu-anchor").performClick()
        ui.waitForIdle()
    }

    private fun opaqueAgainstDifferentBackdrops(dark: Boolean) {
        content(dark)
        val a = ui.onNodeWithTag("selection-menu-surface").captureToImage()
        ui.runOnIdle { backdrop.value = Color.Blue }
        ui.waitForIdle()
        val b = ui.onNodeWithTag("selection-menu-surface").captureToImage()
        assertEquals(a.width, b.width); assertEquals(a.height, b.height)
        val pa = a.toPixelMap(); val pb = b.toPixelMap()
        // Interior only: rounded corners/shadows legitimately expose the activity behind them.
        for (y in 24 until a.height - 24 step 5) for (x in 24 until a.width - 24 step 5) {
            val ca = pa[x, y]; val cb = pb[x, y]
            assertEquals(ca.red, cb.red, .015f); assertEquals(ca.green, cb.green, .015f); assertEquals(ca.blue, cb.blue, .015f)
        }
    }
    @Test fun lightFallbackMenuDoesNotShowActivityRowsThroughIt() = opaqueAgainstDifferentBackdrops(false)
    @Test fun darkFallbackMenuDoesNotShowActivityRowsThroughIt() = opaqueAgainstDifferentBackdrops(true)
    @Test fun selectionCommitsOnceAndCloses() {
        content()
        ui.onNodeWithTag("menu-option-2").performClick(); ui.waitForIdle()
        assertEquals(2, selection.intValue); assertEquals(1, calls)
        ui.onNodeWithTag("selection-menu-surface").assertDoesNotExist()
    }
    @Test fun longMenuScrollsToTheLastChoice() {
        content(options = 40)
        ui.onNodeWithTag("menu-option-39").performScrollTo().performClick(); ui.waitForIdle()
        assertEquals(39, selection.intValue)
    }
    @Test fun reversingDismissalDoesNotUnmountTheNewMenu() {
        content()
        ui.mainClock.autoAdvance = false
        ui.runOnIdle { open.value = false }
        ui.mainClock.advanceTimeBy(32)
        ui.runOnIdle { open.value = true }
        ui.mainClock.autoAdvance = true; ui.waitForIdle()
        ui.onNodeWithTag("selection-menu-surface").assertIsDisplayed()
        ui.onNodeWithTag("menu-option-1").performClick(); ui.waitForIdle()
        assertEquals(1, calls)
    }
    @Test fun menuDoesNotCreateAnotherSemanticsWindow() {
        content();ui.onAllNodes(isRoot()).assertCountEquals(1)
        ui.onNodeWithTag("glass-overlay-host").assertIsDisplayed()
    }
    @Test fun rootSamplingPlaneIsNotRecreatedByMenus() {
        content();ui.runOnIdle{assertEquals(1,sourceAreas())}
        repeat(4) {
            ui.onNodeWithTag("menu-option-1").performClick();ui.waitForIdle()
            ui.runOnIdle{assertEquals(1,sourceAreas())}
            ui.onNodeWithTag("menu-anchor").performClick();ui.waitForIdle()
            ui.runOnIdle{assertEquals(1,sourceAreas())}
        }
    }
    @Test fun selectionIsNotCommittedWhileOldMenuIsVisible() {
        content();ui.mainClock.autoAdvance=false
        ui.onNodeWithTag("menu-option-2").performClick()
        ui.runOnIdle{assertEquals(0,calls)}
        ui.mainClock.advanceTimeBy(48)
        ui.runOnIdle{assertEquals(0,calls)}
        ui.mainClock.autoAdvance=true;ui.waitForIdle()
        ui.runOnIdle{assertEquals(1,calls);assertEquals(2,selection.intValue)}
    }

}
