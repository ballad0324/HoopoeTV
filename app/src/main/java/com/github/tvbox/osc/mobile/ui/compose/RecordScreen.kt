@file:JvmName("MobileRecordScreen")
@file:OptIn(ExperimentalFoundationApi::class)

package com.github.tvbox.osc.mobile.ui.compose

import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.mobile.ui.MobileFavorites
import com.github.tvbox.osc.mobile.ui.MobilePlayHistory
import com.github.tvbox.osc.util.ImgUtil
import kotlin.math.max

/**
 * 历史 / 收藏夹页的一行数据。字段与老 [com.github.tvbox.osc.mobile.ui.adapter.MobileRecordCardAdapter.Row] 一致，
 * 但这里是 Kotlin 侧的不可变快照，供 Compose 列表直接消费。
 */
data class RecordRow(
    val vodId: String,
    val sourceKey: String,
    val name: String,
    val pic: String,
    val badge: String,
    /** 播放进度百分比，-1 = 收藏夹 / 没有进度 */
    val percent: Int
)

/**
 * Compose 页面回调给 Activity 的动作。Activity 负责跳详情、弹删除确认、清空存储，
 * Compose 只负责界面与状态。
 */
interface RecordHost {
    fun onBack()
    fun onOpenRecord(row: RecordRow)
    fun onDeleteRecord(row: RecordRow, fav: Boolean)
    fun onClearRecords(fav: Boolean)
}

/**
 * 页面刷新信号：Activity 在 onResume / 删除 / 清空之后调 [bump]，
 * Compose 里的 remember 以它为 key，自动重读 Hawk 里的记录。
 */
object RecordUiState {
    var version by mutableIntStateOf(0)
        private set

    @JvmStatic
    fun bump() {
        version++
    }
}

/**
 * 从 Activity（Java）侧调用的入口：把 Compose 内容挂到 ComposeView 上。
 */
fun bindRecordScreen(view: ComposeView, initialFav: Boolean, host: RecordHost) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent {
        RecordScreen(initialFav = initialFav, host = host)
    }
}

@Composable
private fun RecordScreen(initialFav: Boolean, host: RecordHost) {
    var fav by remember { mutableStateOf(initialFav) }
    var deleteKey by remember { mutableStateOf<String?>(null) }
    val rows = remember(fav, RecordUiState.version) { loadRows(fav) }

    // 切换分段时把删除钮收起来
    LaunchedEffect(fav) { deleteKey = null }

    val textColor = skinText1()
    val dimColor = skinText2()

    Column(Modifier.fillMaxSize()) {
        // 顶部：返回 + 居中分段胶囊
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(40.dp)
                    .clickable { host.onBack() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.v_back),
                    contentDescription = "返回",
                    modifier = Modifier.size(24.dp)
                )
            }
            Capsule(
                fav = fav,
                onSwitch = { target -> if (target != fav) fav = target },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 分区标题 + 清空
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShellText(
                text = if (fav) "我的收藏" else "继续观看",
                sizeSp = 19,
                bold = true,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            ShellText(
                text = "清空",
                sizeSp = 13,
                bold = false,
                color = dimColor,
                modifier = Modifier.clickable { host.onClearRecords(fav) }.padding(6.dp)
            )
        }

        if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(36.dp), contentAlignment = Alignment.Center) {
                ShellText("还没有内容", 13, false, dimColor)
            }
        } else {
            val widthDp = LocalConfiguration.current.screenWidthDp
            val sidePad = max(16, (widthDp - 336) / 2).dp
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = sidePad, top = 4.dp, end = sidePad, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(items = rows, key = { it.vodId + "|" + it.sourceKey }) { row ->
                    val key = row.vodId + "|" + row.sourceKey
                    RecordCard(
                        row = row,
                        showDelete = deleteKey == key,
                        onOpen = { host.onOpenRecord(row) },
                        onLongPress = { deleteKey = key },
                        onDelete = { host.onDeleteRecord(row, fav) },
                        onDismissDelete = { deleteKey = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun Capsule(fav: Boolean, onSwitch: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val trackFill = skinBgCard()
    val trackStroke = skinLine()
    val onFill = skinBtnBg()
    val textColor = skinText1()
    val dimColor = skinText2()

    Box(
        modifier.width(200.dp).height(42.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(trackFill)
            .border(1.dp, trackStroke, RoundedCornerShape(24.dp))
            .padding(4.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            CapsuleTab("历史", !fav, onFill, textColor, dimColor, Modifier.weight(1f)) { onSwitch(false) }
            CapsuleTab("收藏夹", fav, onFill, textColor, dimColor, Modifier.weight(1f)) { onSwitch(true) }
        }
    }
}

@Composable
private fun CapsuleTab(
    text: String,
    selected: Boolean,
    onFill: Color,
    textColor: Color,
    dimColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(22.dp))
            .background(if (selected) onFill else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        ShellText(text, 14, true, if (selected) skinOnAccent() else dimColor)
    }
}

@Composable
private fun RecordCard(
    row: RecordRow,
    showDelete: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onDismissDelete: () -> Unit
) {
    val coverBg = skinBgFloat()
    val textColor = skinText1()

    Column(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (showDelete) onDismissDelete() else onOpen() },
            onLongClick = { if (!showDelete) onLongPress() }
        )
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .background(coverBg)
        ) {
            Cover(row.pic, Modifier.fillMaxSize())
            if (row.percent >= 0) {
                ProgressBar(
                    percent = row.percent,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(start = 10.dp, end = 10.dp, bottom = 7.dp)
                )
            }
            if (showDelete) {
                DeleteButton(
                    onDelete = onDelete,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                )
            }
        }
        ShellText(
            text = if (row.name.isBlank()) "未命名" else row.name,
            sizeSp = 13,
            bold = true,
            color = textColor,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        SourceBadge(
            text = if (row.badge.isBlank()) "本机" else row.badge,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** 封面：沿用工程里现成的 ImgUtil（Glide）加载，不给试点页额外引图片库 */
@Composable
private fun Cover(pic: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { iv ->
            if (pic.isBlank()) {
                iv.setImageResource(R.drawable.img_loading_placeholder)
            } else {
                ImgUtil.load(pic, iv, 0)
            }
        }
    )
}

@Composable
private fun ProgressBar(percent: Int, modifier: Modifier = Modifier) {
    // 低进度（1%）时按百分比算只有一两个像素，看着像颗蓝点：
    // 这里给填充条兜一个 10dp 的最小宽度，保证任何进度都能看出是一条进度条
    val pct = percent.coerceIn(0, 100)
    Box(
        modifier.fillMaxWidth().height(4.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(skinLine())
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val barWidth = maxWidth * (pct / 100f)
            val fixedWidth = if (barWidth < 10.dp) 10.dp else barWidth
            Box(
                Modifier.width(fixedWidth).fillMaxHeight()
                    .background(skinRating())
            )
        }
    }
}

@Composable
private fun DeleteButton(onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xCCE5484D))
            .clickable { onDelete() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.icon_delete),
            contentDescription = "删除",
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SourceBadge(text: String, modifier: Modifier = Modifier) {
    val stroke = skinLine()
    val dimColor = skinText2()
    Box(
        modifier.border(1.dp, stroke, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        ShellText(text, 10, false, dimColor, maxLines = 1)
    }
}

/** 极简文本：不引 material，直接用基础文本 + 手写 TextStyle */
@Composable
private fun ShellText(
    text: String,
    sizeSp: Int,
    bold: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

// ---------------- 数据 ----------------

private fun loadRows(fav: Boolean): List<RecordRow> {
    return try {
        if (fav) {
            MobileFavorites.load().map { it ->
                RecordRow(
                    vodId = it.vodId ?: "",
                    sourceKey = it.sourceKey ?: "",
                    name = it.name ?: "",
                    pic = it.pic ?: "",
                    badge = sourceLabel(it.sourceKey),
                    percent = -1
                )
            }
        } else {
            MobilePlayHistory.load().map { it ->
                val pct = if (it.duration > 0 && it.progress > 0)
                    (it.progress * 100 / it.duration).toInt() else -1
                RecordRow(
                    vodId = it.vodId ?: "",
                    sourceKey = it.sourceKey ?: "",
                    name = it.name ?: "",
                    pic = it.pic ?: "",
                    badge = sourceLabel(it.sourceKey),
                    percent = pct
                )
            }
        }
    } catch (t: Throwable) {
        emptyList()
    }
}

/** 片源 key → 显示名（拿不到就退回 key 本身） */
private fun sourceLabel(key: String?): String {
    if (key.isNullOrEmpty()) return "本机"
    return try {
        val sb = ApiConfig.get().getSource(key)
        val name = sb?.name
        if (!name.isNullOrEmpty()) name else key
    } catch (t: Throwable) {
        key
    }
}
