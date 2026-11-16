package com.github.tvbox.osc.mobile.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.github.tvbox.osc.R
import com.github.tvbox.osc.util.SkinManager

/**
 * Compose 里读取「七套背景即主题」的颜色。
 * Compose 的 colorResource 只能读固定 @color，读不到 Theme 里的 ?attr，
 * 所以这里统一从当前 context 的 Theme 解析，切主题后随界面重建自动更新。
 *
 * token 与 res/values/themes_skin.xml 一一对应：
 * bg-page / bg-card / bg-float / bg-modal / text-1~4 / btn-bg / btn-fg / rating / rating-on-poster
 */
@Composable
fun skinColor(attrRes: Int, fallback: Int): Color {
    val context = LocalContext.current
    return remember(attrRes) { Color(SkinManager.resolve(context, attrRes, fallback)) }
}

/** 主色位（v1.7.29 起已中性化：等于 btn-bg），保留旧名兼容老代码 */
@Composable
fun skinAccent(): Color = skinColor(R.attr.skin_accent, SkinManager.ACCENT[SkinManager.get()])

@Composable
fun skinAccent2(): Color = skinColor(R.attr.skin_accent2, SkinManager.ACCENT2[SkinManager.get()])

/** 主色底上的文字色：亮底自动转黑字 */
@Composable
fun skinOnAccent(): Color {
    val context = LocalContext.current
    return remember { Color(SkinManager.onAccent(context)) }
}

@Composable
fun skinBgPage(): Color = skinColor(R.attr.skin_bg_page, 0xFF1A0507.toInt())

@Composable
fun skinBgCard(): Color = skinColor(R.attr.skin_bg_card, 0xFF260B0E.toInt())

@Composable
fun skinBgFloat(): Color = skinColor(R.attr.skin_bg_float, 0xFF341014.toInt())

@Composable
fun skinBgModal(): Color = skinColor(R.attr.skin_bg_modal, 0xFF43151A.toInt())

@Composable
fun skinText1(): Color = skinColor(R.attr.skin_text_1, 0xFFFFF1F0.toInt())

@Composable
fun skinText2(): Color = skinColor(R.attr.skin_text_2, 0xFFD8A9A6.toInt())

@Composable
fun skinText3(): Color = skinColor(R.attr.skin_text_3, 0xFFA87C79.toInt())

@Composable
fun skinBtnBg(): Color = skinColor(R.attr.skin_btn_bg, 0xFFFFFFFF.toInt())

@Composable
fun skinBtnFg(): Color = skinColor(R.attr.skin_btn_fg, 0xFF1A0507.toInt())

/** 彩色只允许出现在评分 / 进度条这两处极小面积 */
@Composable
fun skinRating(): Color = skinColor(R.attr.skin_rating, 0xFFF0C64B.toInt())

/** 细分割线 */
@Composable
fun skinLine(): Color = skinColor(R.attr.skin_line, 0x1AFFFFFF)

/** 卡片外框线 */
@Composable
fun skinFrame(): Color = skinColor(R.attr.skin_frame, 0xFF4A2022.toInt())
