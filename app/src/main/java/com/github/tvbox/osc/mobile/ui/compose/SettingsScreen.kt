@file:JvmName("MobileSettings")

package com.github.tvbox.osc.mobile.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.mobile.ui.adapter.MobileSettingAdapter

/**
 * 设置页列表的 Compose 版渲染层。
 *
 * 数据与所有业务逻辑（读写偏好、弹对话框、备份、云端备份）仍在 Java（MobileSettingActivity +
 * MobileSettingAdapter.Row），这里只把 Row 画成卡片、把点击/输入/选项/开关原样回调给 Row 上挂的监听器，
 * 这样迁移不会碰到任何设置项的原有行为。
 */
object SettingsState {
    var rows by mutableStateOf<List<MobileSettingAdapter.Row>>(emptyList())
        private set

    fun pushRows(list: List<MobileSettingAdapter.Row>) {
        rows = list
    }
}

/** Java 侧入口：把重建好的行数据推给 Compose（静态方法挂在 MobileSettings 门面类上） */
fun updateRows(list: List<MobileSettingAdapter.Row>) {
    SettingsState.pushRows(list)
}

/** 普通设置行（点一下执行 action）回传给 Activity */
interface SettingsHost {
    fun onRowClick(row: MobileSettingAdapter.Row?)
}

fun bindSettings(view: ComposeView, host: SettingsHost) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent { SettingsList(host) }
}

@Composable
private fun SettingsList(host: SettingsHost) {
    val rows = SettingsState.rows
    val fill = skinBgCard()
    val stroke = skinLine()
    val text = skinText1()
    val dim = skinText2()
    val primary = skinText1()
    val fieldBg = skinBgFloat()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows) { row ->
            when (row.type) {
                MobileSettingAdapter.TYPE_HEADER -> HeaderRow(row.title ?: "", dim)
                MobileSettingAdapter.TYPE_ITEM -> ItemRow(row, host, fill, stroke, text, dim)
                MobileSettingAdapter.TYPE_INPUT -> InputRow(row, fill, stroke, text, dim, primary, fieldBg)
                MobileSettingAdapter.TYPE_SWITCH -> SwitchRow(row, fill, stroke, text, dim, primary)
                MobileSettingAdapter.TYPE_CAPSULE -> CapsuleRow(row, fill, stroke, text, dim, primary)
                else -> Spacer(Modifier.height(0.dp))
            }
        }
    }
}

@Composable
private fun HeaderRow(title: String, dim: Color) {
    BasicText(
        text = title,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
        style = TextStyle(color = dim, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun cardShape() = RoundedCornerShape(16.dp)

@Composable
private fun ItemRow(
    row: MobileSettingAdapter.Row,
    host: SettingsHost,
    fill: Color,
    stroke: Color,
    text: Color,
    dim: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape())
            .background(fill)
            .border(1.dp, stroke, cardShape())
            .clickable {
                if (row.action != null) host.onRowClick(row)
            }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = row.title ?: "",
            modifier = Modifier.weight(1f),
            style = TextStyle(color = text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!row.value.isNullOrEmpty()) {
            BasicText(
                text = row.value ?: "",
                modifier = Modifier.padding(start = 10.dp),
                style = TextStyle(color = dim, fontSize = 12.sp, textAlign = TextAlign.End),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        BasicText(
            text = "›",
            modifier = Modifier.padding(start = 6.dp),
            style = TextStyle(color = dim, fontSize = 15.sp)
        )
    }
}

@Composable
private fun InputRow(
    row: MobileSettingAdapter.Row,
    fill: Color,
    stroke: Color,
    text: Color,
    dim: Color,
    primary: Color,
    fieldBg: Color
) {
    // 每次 refreshRows 都会重建 Row 对象，remember(row) 随之重置为最新值
    var value by remember(row) { mutableStateOf(row.value ?: "") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape())
            .background(fill)
            .border(1.dp, stroke, cardShape())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        BasicText(
            text = row.title ?: "",
            style = TextStyle(color = text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!row.hint.isNullOrEmpty()) {
            BasicText(
                text = row.hint ?: "",
                modifier = Modifier.padding(top = 3.dp),
                style = TextStyle(color = dim, fontSize = 11.sp, lineHeight = 15.sp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(fieldBg)
                    .border(1.dp, stroke, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty() && !row.hintInput.isNullOrEmpty()) {
                    BasicText(
                        text = row.hintInput ?: "",
                        style = TextStyle(color = dim.copy(alpha = 0.7f), fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = text, fontSize = 12.sp),
                    cursorBrush = SolidColor(primary)
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .height(38.dp)
                    .width(76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(primary)
                    .clickable {
                        row.onInput?.onInput(row, value.trim())
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = row.actionText ?: "立即更改",
                    style = TextStyle(
                        color = skinOnAccent(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    row: MobileSettingAdapter.Row,
    fill: Color,
    stroke: Color,
    text: Color,
    dim: Color,
    primary: Color
) {
    var checked by remember(row) { mutableStateOf(row.checked) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape())
            .background(fill)
            .border(1.dp, stroke, cardShape())
            .clickable {
                checked = !checked
                row.checked = checked
                row.onSwitch?.onSwitch(row, checked)
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                text = row.title ?: "",
                style = TextStyle(color = text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!row.hint.isNullOrEmpty()) {
                BasicText(
                    text = row.hint ?: "",
                    modifier = Modifier.padding(top = 3.dp),
                    style = TextStyle(color = dim, fontSize = 11.sp, lineHeight = 15.sp)
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .width(42.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (checked) primary else dim.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 3.dp)
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(skinOnAccent())
            )
        }
    }
}

@Composable
private fun CapsuleRow(
    row: MobileSettingAdapter.Row,
    fill: Color,
    stroke: Color,
    text: Color,
    dim: Color,
    primary: Color
) {
    val options = row.options
    if (options == null || options.isEmpty()) {
        Spacer(Modifier.height(0.dp))
    } else {
        var selected by remember(row) { mutableIntStateOf(row.selected) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape())
                .background(fill)
                .border(1.dp, stroke, cardShape())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
        BasicText(
            text = row.title ?: "",
            style = TextStyle(color = text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!row.hint.isNullOrEmpty()) {
            BasicText(
                text = row.hint ?: "",
                modifier = Modifier.padding(top = 3.dp),
                style = TextStyle(color = dim, fontSize = 11.sp, lineHeight = 15.sp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, label ->
                val on = index == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (on) primary else Color.Transparent)
                        .border(1.dp, if (on) primary else stroke, RoundedCornerShape(17.dp))
                        .clickable {
                            selected = index
                            row.selected = index
                            row.onOption?.onOption(row, index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = label,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = TextStyle(
                            color = if (on) skinOnAccent() else dim,
                            fontSize = if (options.size > 4) 10.sp else 12.sp,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        }
    }
}
