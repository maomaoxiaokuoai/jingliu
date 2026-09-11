package com.luma.downloader

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luma.core.CancelToken
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Chaquopy/lxml/parsel/upstream imports via the separate Android process.
 * No fake parser injected and no account or network needed. NOT executed in the source workspace.
 */
@RunWith(AndroidJUnit4::class)
class LocalParserDeviceTest {
    @Test fun packagedLibraryImportsInPrivateProcess() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val graph=AppGraph.get(context)
        graph.load()
        val result=graph.localParser.selfTest(CancelToken())
        assertTrue(result.contains("本地组件已载入"))
        assertTrue(result.contains("未测试平台联网"))
    }
}
