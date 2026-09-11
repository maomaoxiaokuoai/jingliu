package com.luma.downloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.downloader.data.GlassRole

/** Root in-window portal. No native Popup, focus/window swap, or per-option material. */
@Composable fun SpringDropdownMenu(expanded:Boolean,onDismissRequest:()->Unit,title:String="选择",
    content:@Composable ColumnScope.()->Unit) {
    val host=LocalGlassOverlay.current
    val scope=rememberCoroutineScope()
    val locals by rememberUpdatedState(currentCompositionLocalContext)
    val currentContent by rememberUpdatedState(content)
    val currentTitle by rememberUpdatedState(title)
    val dismiss by rememberUpdatedState(onDismissRequest)
    val settings by rememberUpdatedState(LocalUiSettings.current)
    val mode by rememberUpdatedState(LocalAppearance.current.motion)
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val entry=remember(host,scope) { GlassOverlayEntry(host,scope,OverlayKind.MENU,{locals},
        {currentTitle},false,{settings},{mode},{dismiss()}) {
        GlassSurface(Modifier.fillMaxWidth().testTag("selection-menu-surface").semantics{paneTitle=currentTitle},
            radius=20.dp,role=GlassRole.MENU) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical=6.dp),content=currentContent)
        }
    } }
    // A zero-size child observes its anchor row's bounds; placement is computed in one measure pass.
    Box(Modifier.size(0.dp).onGloballyPositioned {coordinates ->
        val next=coordinates.parentLayoutCoordinates?.boundsInRoot()?:coordinates.boundsInRoot()
        if(anchor!=next)anchor=next
    })
    SideEffect {entry.anchor=anchor}
    LaunchedEffect(expanded,anchor.width>0f) {
        if(expanded&&anchor.width>0f)entry.open() else if(!expanded)entry.close()
    }
    DisposableEffect(entry) {onDispose{entry.dispose()}}
}

/** A single flat selection row. No Surface/Card, elevation, or independent glass background. */
@Composable fun SpringMenuItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
) {
    val palette = LocalLumaPalette.current
    val acceptsInput = LocalOverlayInput.current && enabled
    val select=LocalOverlaySelect.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val highlight = if (acceptsInput && (pressed || focused || hovered)) palette.ink.copy(alpha = .065f) else Color.Transparent
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp)
            .background(highlight)
            .selectable(selected = selected, enabled = acceptsInput, role = Role.RadioButton,
                interactionSource = interactions, indication = null, onClick = { if(select!=null)select(onClick)else onClick() })
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides palette.ink.copy(alpha = if (enabled) 1f else .4f)) {
            if (leadingIcon != null) Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { leadingIcon() }
            Box(Modifier.weight(1f)) { ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() } }
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable fun SpringMenuDivider() {
    Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(.5.dp).background(LocalLumaPalette.current.line.copy(alpha = .45f)))
}
