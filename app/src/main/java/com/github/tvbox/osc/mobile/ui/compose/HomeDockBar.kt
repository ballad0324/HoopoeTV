@file:JvmName("MobileHomeDock")
@file:OptIn(ExperimentalFoundationApi::class)

package com.github.tvbox.osc.mobile.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.R

/**
 * 首页底部导航栏的全局状态（Compose 侧）。
 * Activity 改分类/切 tab 时调这里的 setter，界面自动重组。
 */
object HomeDockState {
    /** 分类名列表（首页、电影、剧集……；由 buildPages 灌入） */
    var categories by mutableStateOf<List<String>>(emptyList())
        private set

    /** 当前选中的分类下标（= ViewPager 当前页） */
    var selectedCategory by mutableIntStateOf(0)
        private set

    /** 当前高亮的 dock 图标：0 影视 / 1 直播 / 2 搜索 / 3 发现 */
    var selectedDock by mutableIntStateOf(0)
        private set

    /** 设置页「搜索入口」开关：控制第三个图标显隐 */
    var showSearch by mutableStateOf(true)
        private set

    /** 设置页「显示导航文字」开关 */
    var showLabels by mutableStateOf(true)
        private set

    @JvmStatic
    fun updateCategories(names: List<String>) {
        categories = names
    }

    @JvmStatic
    fun updateSelectedCategory(index: Int) {
        selectedCategory = index
    }

    @JvmStatic
    fun updateSelectedDock(index: Int) {
        selectedDock = index
    }

    @JvmStatic
    fun updateShowSearch(show: Boolean) {
        showSearch = show
    }

    @JvmStatic
    fun updateShowLabels(show: Boolean) {
        showLabels = show
    }
}

/** Compose 底栏回传给 Activity 的两个动作 */
interface HomeDockHost {
    /** 点了第 index 个分类（对应 ViewPager 页） */
    fun onCategorySelected(index: Int)

    /** 点了 dock 图标：0 影视 / 1 直播 / 2 搜索 / 3 发现 */
    fun onDockClick(tab: Int)
}

/** Activity（Java）侧入口 */
fun bindHomeDock(view: ComposeView, host: HomeDockHost) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent { HomeDock(host) }
}

@Composable
private fun HomeDock(host: HomeDockHost) {
    val categories = HomeDockState.categories
    val selectedCategory = HomeDockState.selectedCategory
    val selectedDock = HomeDockState.selectedDock
    val showSearch = HomeDockState.showSearch
    val showLabels = HomeDockState.showLabels

    Column(Modifier.fillMaxWidth()) {
        if (categories.isNotEmpty()) {
            CategoryStrip(
                categories = categories,
                selected = selectedCategory,
                onSelect = { index ->
                    HomeDockState.updateSelectedCategory(index)
                    HomeDockState.updateSelectedDock(0)
                    host.onCategorySelected(index)
                }
            )
        }
        DockRow(
            selectedDock = selectedDock,
            showSearch = showSearch,
            showLabels = showLabels,
            onDockClick = { tab ->
                HomeDockState.updateSelectedDock(tab)
                host.onDockClick(tab)
            }
        )
    }
}

/** 分类条：横向可滑，选中项蓝色文字 + 下方短蓝条（原顶部那排挪到底部） */
@Composable
private fun CategoryStrip(categories: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val track = colorResource(R.color.shell_surface_high)
    val stroke = colorResource(R.color.shell_card_stroke)
    val primary = colorResource(R.color.shell_primary)
    val dim = colorResource(R.color.shell_text_dim)
    val listState = rememberLazyListState()

    // 切换分类时把选中项滚进可视区（原来靠 rvType.smoothScrollToPosition）
    LaunchedEffect(selected) {
        if (selected in categories.indices) {
            try {
                listState.animateScrollToItem(selected)
            } catch (t: Throwable) {
                // 忽略滚动异常，不影响点击
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(track)
            .border(1.dp, stroke, RoundedCornerShape(21.dp)),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        itemsIndexed(items = categories, key = { _, name -> name }) { index, name ->
            CategoryItem(
                name = name,
                selected = index == selected,
                primary = primary,
                dim = dim,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun CategoryItem(name: String, selected: Boolean, primary: Color, dim: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = name,
            style = TextStyle(
                color = if (selected) primary else dim,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .width(16.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) primary else Color.Transparent)
        )
    }
}

/** 四图标 dock：玻璃胶囊，选中蓝、未选中灰（对齐原 view_mobile_dock 的观感）。
 *  图标数量多时整排可左右横滑：≤4 个平分宽度，>4 个固定 76dp 一格并可滑动。 */
@Composable
private fun DockRow(
    selectedDock: Int,
    showSearch: Boolean,
    showLabels: Boolean,
    onDockClick: (Int) -> Unit
) {
    val fill = colorResource(R.color.shell_card_fill)
    val stroke = colorResource(R.color.shell_card_stroke)
    val primary = colorResource(R.color.shell_primary)
    val dim = colorResource(R.color.shell_text_dim)
    val shape = RoundedCornerShape(29.dp)

    // dock 项：0 影视 / 1 直播 / 2 搜索（可被设置隐藏）/ 3 发现
    val items = if (showSearch) {
        listOf(
            DockEntry(0, "影视", R.drawable.icon_home),
            DockEntry(1, "直播", R.drawable.hm_live),
            DockEntry(2, "搜索", R.drawable.hm_search),
            DockEntry(3, "发现", R.drawable.ic_nav_discover)
        )
    } else {
        listOf(
            DockEntry(0, "影视", R.drawable.icon_home),
            DockEntry(1, "直播", R.drawable.hm_live),
            DockEntry(3, "发现", R.drawable.ic_nav_discover)
        )
    }
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxWidth().padding(start = 34.dp, end = 34.dp, top = 10.dp, bottom = 12.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .shadow(10.dp, shape)
                .clip(shape)
                .background(fill)
                .border(1.dp, stroke, shape)
        ) {
            // ≤4 个时平分整条胶囊；多于 4 个时每格 76dp，整排可横滑
            val itemWidth = if (items.size <= 4) maxWidth / items.size.toFloat() else 76.dp
            LazyRow(state = listState, modifier = Modifier.fillMaxSize()) {
                items(items.size) { index ->
                    val entry = items[index]
                    DockItem(
                        modifier = Modifier.width(itemWidth).fillMaxHeight(),
                        label = entry.label,
                        iconRes = entry.iconRes,
                        selected = selectedDock == entry.tab,
                        primary = primary,
                        dim = dim,
                        showLabel = showLabels
                    ) {
                        onDockClick(entry.tab)
                    }
                }
            }
        }
    }
}

private data class DockEntry(val tab: Int, val label: String, val iconRes: Int)

@Composable
private fun DockItem(
    modifier: Modifier,
    label: String,
    iconRes: Int,
    selected: Boolean,
    primary: Color,
    dim: Color,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) primary else dim
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(21.dp),
            colorFilter = ColorFilter.tint(tint)
        )
        if (showLabel) {
            BasicText(
                text = label,
                modifier = Modifier.padding(top = 3.dp),
                style = TextStyle(color = tint, fontSize = 10.sp, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
