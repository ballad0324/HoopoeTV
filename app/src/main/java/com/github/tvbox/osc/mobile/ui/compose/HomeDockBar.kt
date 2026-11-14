@file:JvmName("MobileHomeDock")
@file:OptIn(ExperimentalFoundationApi::class)

package com.github.tvbox.osc.mobile.ui.compose

import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.layout.Spacer
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
import com.github.tvbox.osc.mobile.ui.activity.MobileDiscoverActivity
import com.github.tvbox.osc.mobile.ui.activity.MobileHomeActivity
import com.github.tvbox.osc.mobile.ui.activity.MobileLiveActivity
import com.github.tvbox.osc.mobile.ui.activity.MobileSearchActivity

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

    /** 是否显示分类段（只有首页显示；直播/搜索/发现/详情等页只留四个入口） */
    var categoriesVisible by mutableStateOf(true)
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
    fun updateCategoriesVisible(visible: Boolean) {
        categoriesVisible = visible
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

/** dock 四个入口的下标（与 MobileDockHelper 的 TAB_* 对齐） */
const val TAB_VOD = 0
const val TAB_LIVE = 1
const val TAB_SEARCH = 2
const val TAB_DISCOVER = 3
/** 设置页等不属于 dock 的页面：四个入口都不点亮 */
const val TAB_NONE = -1

/** 首页（Java）侧入口：四个图标入口 + 分类段 */
fun bindHomeDock(view: ComposeView, host: HomeDockHost) {
    HomeDockState.updateCategoriesVisible(true)
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent {
        NavBar(
            onCategorySelect = { index ->
                HomeDockState.updateSelectedCategory(index)
                HomeDockState.updateSelectedDock(TAB_VOD)
                host.onCategorySelected(index)
            },
            onDockClick = { tab ->
                HomeDockState.updateSelectedDock(tab)
                host.onDockClick(tab)
            }
        )
    }
}

/**
 * 子页面（直播 / 搜索 / 发现 / 详情 / 记录 / 设置等）侧入口：
 * 只留四个图标入口（隐藏分类段），点击跳转由这里自己完成，取代原 MobileDockHelper。
 */
fun bindDockOnly(activity: Activity?, view: android.view.View?, currentTab: Int) {
    if (activity == null || view !is ComposeView) return
    val composeView: ComposeView = view
    HomeDockState.updateCategoriesVisible(false)
    HomeDockState.updateSelectedDock(currentTab)
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        NavBar(
            onCategorySelect = {},
            onDockClick = { tab ->
                if (tab != currentTab) {
                    HomeDockState.updateSelectedDock(tab)
                    openTab(activity, tab)
                }
            }
        )
    }
}

/** 与 MobileDockHelper.open() 同一套跳转语义：同级切换后收起自己，首页留在栈底 */
private fun openTab(activity: Activity, tab: Int) {
    val cls: Class<*> = when (tab) {
        TAB_VOD -> MobileHomeActivity::class.java
        TAB_LIVE -> MobileLiveActivity::class.java
        TAB_SEARCH -> MobileSearchActivity::class.java
        TAB_DISCOVER -> MobileDiscoverActivity::class.java
        else -> return
    }
    try {
        activity.startActivity(Intent(activity, cls))
    } catch (t: Throwable) {
        return
    }
    if (activity !is MobileHomeActivity) activity.finish()
}

/** 一条玻璃胶囊导航栏：左边四个图标入口，右边分类 tab（整条可横滑） */
@Composable
private fun NavBar(onCategorySelect: (Int) -> Unit, onDockClick: (Int) -> Unit) {
    val selectedDock = HomeDockState.selectedDock
    val selectedCategory = HomeDockState.selectedCategory
    val showSearch = HomeDockState.showSearch
    val showLabels = HomeDockState.showLabels
    val categoriesVisible = HomeDockState.categoriesVisible
    // 「影视」就是首页那页，分类列表的第 0 项（首页）不再重复出现
    val categories = if (categoriesVisible) HomeDockState.categories.drop(1) else emptyList()

    val fill = colorResource(R.color.shell_card_fill)
    val stroke = colorResource(R.color.shell_card_stroke)
    val primary = colorResource(R.color.shell_primary)
    val dim = colorResource(R.color.shell_text_dim)
    val shape = RoundedCornerShape(25.dp)

    val entries = if (showSearch) {
        listOf(
            DockEntry(TAB_VOD, "影视", R.drawable.icon_home),
            DockEntry(TAB_LIVE, "直播", R.drawable.hm_live),
            DockEntry(TAB_SEARCH, "搜索", R.drawable.hm_search),
            DockEntry(TAB_DISCOVER, "发现", R.drawable.ic_nav_discover)
        )
    } else {
        listOf(
            DockEntry(TAB_VOD, "影视", R.drawable.icon_home),
            DockEntry(TAB_LIVE, "直播", R.drawable.hm_live),
            DockEntry(TAB_DISCOVER, "发现", R.drawable.ic_nav_discover)
        )
    }
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 12.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .shadow(10.dp, shape)
                .clip(shape)
                .background(fill)
                .border(1.dp, stroke, shape)
        ) {
            if (categories.isEmpty()) {
                // 子页面：只留四个图标入口，平分整条栏
                Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                    entries.forEach { entry ->
                        DockEntryItem(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            entry = entry,
                            selected = selectedDock == entry.tab,
                            primary = primary,
                            dim = dim,
                            showLabel = showLabels,
                            onClick = { onDockClick(entry.tab) }
                        )
                    }
                }
            } else {
                // 首页：四个入口 + 竖分隔线 + 分类 tab，整条可左右横滑
                LaunchedEffect(selectedCategory, categories.size) {
                    val target = entries.size + 1 + (selectedCategory - 1)
                    if (selectedCategory >= 1 && target <= entries.size + 1 + categories.size) {
                        try {
                            listState.animateScrollToItem(target)
                        } catch (t: Throwable) {
                        }
                    }
                }
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(entries.size) { index ->
                        val entry = entries[index]
                        DockEntryItem(
                            modifier = Modifier,
                            entry = entry,
                            selected = selectedDock == entry.tab,
                            primary = primary,
                            dim = dim,
                            showLabel = showLabels,
                            onClick = { onDockClick(entry.tab) }
                        )
                    }
                    item { VDivider(stroke) }
                    items(categories.size) { index ->
                        // 分类下标要补回被跳过的第 0 项
                        val realIndex = index + 1
                        CategoryItem(
                            name = categories[index],
                            selected = selectedCategory == realIndex && selectedDock == TAB_VOD,
                            primary = primary,
                            dim = dim,
                            onClick = { onCategorySelect(realIndex) }
                        )
                    }
                }
            }
        }
    }
}

private data class DockEntry(val tab: Int, val label: String, val iconRes: Int)

@Composable
private fun VDivider(stroke: Color) {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .width(1.dp)
            .height(22.dp)
            .background(stroke)
    )
}

/** 图标 + 文字入口（选中时垫一层淡蓝胶囊底） */
@Composable
private fun DockEntryItem(
    modifier: Modifier,
    entry: DockEntry,
    selected: Boolean,
    primary: Color,
    dim: Color,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) primary else dim
    Box(
        modifier = modifier.clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) primary.copy(alpha = 0.12f) else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(entry.iconRes),
                contentDescription = entry.label,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(tint)
            )
            if (showLabel) {
                Spacer(Modifier.width(5.dp))
                BasicText(
                    text = entry.label,
                    style = TextStyle(
                        color = tint,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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

// 说明：原「分类条 + 四图标 dock 两行」的结构已合并为上面的单条 NavBar（v1.6.30）。
