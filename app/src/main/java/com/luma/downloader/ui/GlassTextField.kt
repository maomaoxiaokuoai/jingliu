package com.luma.downloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.luma.downloader.data.GlassRole

/** Real editable text field with native IME/cursor/selection; no outlined Material rectangle. */
@Composable fun GlassTextField(
    value:String, onValueChange:(String)->Unit, placeholder:String,
    modifier:Modifier=Modifier, singleLine:Boolean=false, minLines:Int=1, maxLines:Int=4,
    leadingIcon:(@Composable ()->Unit)?=null, trailingIcon:(@Composable ()->Unit)?=null,
    imeAction:ImeAction=ImeAction.Default, onSubmit:()->Unit={},
    visualTransformation:VisualTransformation=VisualTransformation.None,
) {
    val p=LocalLumaPalette.current
    var focused by remember{mutableStateOf(false)}
    val focus=LocalFocusManager.current
    GlassSurface(modifier.fillMaxWidth(),radius=if(singleLine)18.dp else 20.dp,role=GlassRole.FIELD,
        tint=if(focused)p.accent.copy(alpha=.045f)else Color.Unspecified,
        fallback=p.group,pressed=if(focused).22f else 0f) {
        TextField(value=value,onValueChange=onValueChange,
            modifier=Modifier.fillMaxWidth().onFocusChanged{focused=it.isFocused},
            singleLine=singleLine,minLines=minLines,maxLines=maxLines,visualTransformation=visualTransformation,
            placeholder={Text(placeholder,color=p.muted)},leadingIcon=leadingIcon,trailingIcon=trailingIcon,
            keyboardOptions=KeyboardOptions(imeAction=imeAction),
            keyboardActions=KeyboardActions(onSearch={onSubmit();focus.clearFocus()},onGo={onSubmit();focus.clearFocus()},onDone={onSubmit();focus.clearFocus()}),
            colors=TextFieldDefaults.colors(
                focusedTextColor=p.ink,unfocusedTextColor=p.ink,cursorColor=p.accent,
                focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,
                disabledContainerColor=Color.Transparent,errorContainerColor=Color.Transparent,
                focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,
                disabledIndicatorColor=Color.Transparent,errorIndicatorColor=Color.Transparent,
                focusedLeadingIconColor=p.muted,unfocusedLeadingIconColor=p.muted,
                focusedTrailingIconColor=p.muted,unfocusedTrailingIconColor=p.muted,
            ),
        )
    }
}

@Composable fun GlassSearchField(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier) {
    val clearIcon:(@Composable ()->Unit)? = if(value.isEmpty())null else { { GlassIconButton(onClick={onValueChange("")}){Icon(Icons.Outlined.Cancel,"清空搜索")} } }
    GlassTextField(value,onValueChange,"搜索本机文件",modifier,singleLine=true,maxLines=1,
        leadingIcon={Icon(Icons.Outlined.Search,null)},
        trailingIcon=clearIcon,
        imeAction=ImeAction.Search)
}
