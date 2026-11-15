package com.github.tvbox.osc.mobile.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.github.tvbox.osc.R
import com.github.tvbox.osc.util.SkinManager

/**
 * Compose 里读取「七套色系主题」的颜色。
 * Compose 的 colorResource 只能读固定 @color，读不到 Theme 里的 ?attr，
 * 所以这里统一从当前 context 的 Theme 解析，切主题后随界面重建自动更新。
 */
@Composable
fun skinColor(attrRes: Int, fallback: Int): Color {
    val context = LocalContext.current
    return remember(attrRes) { Color(SkinManager.resolve(context, attrRes, fallback)) }
}

@Composable
fun skinAccent(): Color = skinColor(R.attr.skin_accent, SkinManager.ACCENT[SkinManager.get()])

@Composable
fun skinAccent2(): Color = skinColor(R.attr.skin_accent2, SkinManager.ACCENT2[SkinManager.get()])

/** 主色底上的文字色：小黄黄/樱花粉这类亮底自动转黑字 */
@Composable
fun skinOnAccent(): Color {
    val context = LocalContext.current
    return remember { Color(SkinManager.onAccent(context)) }
}
