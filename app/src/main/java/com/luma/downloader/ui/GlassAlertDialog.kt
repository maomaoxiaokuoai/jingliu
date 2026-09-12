package com.luma.downloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luma.downloader.data.GlassRole

@Composable fun GlassAlertDialog(onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,
    title:(@Composable ()->Unit)?=null,text:(@Composable ()->Unit)?=null,
    dismissButton:(@Composable ()->Unit)?=null,secure:Boolean=false) {
    val host=LocalGlassOverlay.current;val scope=rememberCoroutineScope();val locals by rememberUpdatedState(currentCompositionLocalContext)
    val dismiss by rememberUpdatedState(onDismissRequest)
    val titleContent by rememberUpdatedState(title);val body by rememberUpdatedState(text)
    val confirm by rememberUpdatedState(confirmButton);val cancel by rememberUpdatedState(dismissButton)
    val settings by rememberUpdatedState(LocalUiSettings.current);val mode by rememberUpdatedState(LocalAppearance.current.motion)
    val entry=remember(host,scope,secure) {GlassOverlayEntry(host,scope,OverlayKind.DIALOG,{locals},{"对话框"},secure,{settings},{mode},{dismiss()}) {
        val interactions=remember{MutableInteractionSource()}
        val motion = com.luma.downloader.ui.optics.LocalGlassMotion.current
        GlassSurface(Modifier.fillMaxWidth().clickable(interactionSource=interactions,indication=null){},radius=28.dp,role=GlassRole.MENU,
            pressProgress = { (1f - motion().coerceIn(0f, 2f).coerceAtMost(1f)) * 0.16f },
            motionSample = { motion() }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                titleContent?.let{ProvideTextStyle(MaterialTheme.typography.titleLarge){it()}}
                body?.let{ProvideTextStyle(MaterialTheme.typography.bodyLarge){it()}}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                    cancel?.invoke();Spacer(Modifier.width(8.dp));confirm()
                }
            }
        }
    }}
    LaunchedEffect(entry){entry.open()}
    DisposableEffect(entry){onDispose{entry.dispose()}}
}
