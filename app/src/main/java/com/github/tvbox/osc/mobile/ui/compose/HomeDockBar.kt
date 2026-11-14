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

    /** 点了 dock 图标：0 首页 / 1 直播 / 2 搜索 / 3 发现 */
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
            highlightCategory = true,
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
 * v1.6.33 起所有页面都显示同一条完整导航栏（四项 + 分类段），
 * 点分类会跳回影视首页并定位到该分类；点四项自己完成跳转。
 */
fun bindDockOnly(activity: Activity?, view: android.view.View?, currentTab: Int) {
    if (activity == null || view !is ComposeView) return
    val composeView: ComposeView = view
    HomeDockState.updateCategoriesVisible(true)
    HomeDockState.updateSelectedDock(currentTab)
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        NavBar(
            highlightCategory = false,
            onCategorySelect = { index -> openHomeAtCategory(activity, index) },
            onDockClick = { tab ->
                if (tab != currentTab) {
                    HomeDockState.updateSelectedDock(tab)
                    openTab(activity, tab)
                }
            }
        )
    }
}

/** 子页面点了分类：直接回到影视首页，并让首页按 extra 定位到那一页 */
private fun openHomeAtCategory(activity: Activity, index: Int) {
    if (activity is MobileHomeActivity) {
        // 首页自己点分类走的是 onCategorySelected，不会进到这里
        return
    }
    try {
        val intent = Intent(activity, MobileHomeActivity::class.java)
        intent.putExtra("mobile_page_index", index)
        activity.startActivity(intent)
        activity.finish()
    } catch (t: Throwable) {
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
private fun NavBar(onCategorySelect: (Int) -> Unit, onDockClick: (Int) -> Unit, highlightCategory: Boolean) {
    val selectedDock = HomeDockState.selectedDock
    val selectedCategory = HomeDockState.selectedCategory
    val showSearch = HomeDockState.showSearch
    val showLabels = HomeDockState.showLabels
    val categoriesVisible = HomeDockState.categoriesVisible
    // 「首页」就是影视分类的第 0 页（带我的收藏 / 播放历史），分类段里不再重复出现这项
    val categories = if (categoriesVisible) HomeDockState.categories.drop(1) else emptyList()

    val fill = colorResource(R.color.shell_card_fill)
    val stroke = colorResource(R.color.shell_card_stroke)
    val primary = colorResource(R.color.shell_primary)
    val dim = colorResource(R.color.shell_text_dim)
    val shape = RoundedCornerShape(25.dp)

    val entries = if (showSearch) {
        listOf(
            DockEntry(TAB_VOD, "首页", R.drawable.icon_home),
            DockEntry(TAB_LIVE, "直播", R.drawable.hm_live),
            DockEntry(TAB_SEARCH, "搜索", R.drawable.hm_search),
            DockEntry(TAB_DISCOVER, "发现", R.drawable.ic_nav_discover)
        )
    } else {
        listOf(
            DockEntry(TAB_VOD, "首页", R.drawable.icon_home),
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
            // 四项与分类统一成同一套「图标 + 文字」单元，等宽等间距
            val itemWidth = if (showLabels) 70.dp else 42.dp
            if (categories.isEmpty()) {
                // 子页面：只留四个图标入口，平分整条栏
                Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                    entries.forEach { entry ->
                        NavItem(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            iconRes = entry.iconRes,
                            label = entry.label,
                            selected = selectedDock == entry.tab,
                            primary = primary,
                            dim = dim,
                            showLabel = showLabels,
                            onClick = { onDockClick(entry.tab) }
                        )
                    }
                }
            } else {
                // 首页：四个入口 + 全部分类（每项都带图标），整条可左右横滑；不再插竖分隔线
                LaunchedEffect(selectedCategory, categories.size) {
                    val target = entries.size + (selectedCategory - 1)
                    if (selectedCategory >= 1 && target < entries.size + categories.size) {
                        try {
                            listState.animateScrollToItem(target)
                        } catch (t: Throwable) {
                        }
                    }
                }
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(entries.size) { index ->
                        val entry = entries[index]
                        NavItem(
                            modifier = Modifier.width(itemWidth).fillMaxHeight(),
                            iconRes = entry.iconRes,
                            label = entry.label,
                            selected = selectedDock == entry.tab,
                            primary = primary,
                            dim = dim,
                            showLabel = showLabels,
                            onClick = { onDockClick(entry.tab) }
                        )
                    }
                    items(categories.size) { index ->
                        // 分类下标要补回被跳过的第 0 项
                        val realIndex = index + 1
                        NavItem(
                            modifier = Modifier.width(itemWidth).fillMaxHeight(),
                            iconRes = iconForCategory(categories[index]),
                            label = categories[index],
                            selected = highlightCategory && selectedCategory == realIndex && selectedDock == TAB_VOD,
                            primary = primary,
                            dim = dim,
                            showLabel = showLabels,
                            onClick = { onCategorySelect(realIndex) }
                        )
                    }
                }
            }
        }
    }
}

private data class DockEntry(val tab: Int, val label: String, val iconRes: Int)

/**
 * 分类名 → 图标。数据源里的分类名千变万化（电影 / 影片 / Movie / 4K电影……），
 * 这里按关键字兜底匹配，认不出的一律给「四宫格」通用图标，不会漏图标也不会崩。
 */
private fun iconForCategory(name: String): Int {
    val n = name ?: ""
    return when {
        // 「短剧」必须排在「剧集」前面，否则会被剧集先吃掉
        n.contains("短剧") || n.contains("短劇") || n.contains("Short", ignoreCase = true) ->
            R.drawable.ic_cat_short
        n.contains("综艺") || n.contains("綜藝") ||
            n.contains("Variety", ignoreCase = true) || n.contains("Show", ignoreCase = true) ->
            R.drawable.ic_cat_variety
        n.contains("动漫") || n.contains("動漫") || n.contains("动画") || n.contains("動畫") ||
            n.contains("Anime", ignoreCase = true) || n.contains("Animation", ignoreCase = true) ->
            R.drawable.ic_cat_anime
        n.contains("纪录") || n.contains("紀錄") || n.contains("Doc", ignoreCase = true) ->
            R.drawable.ic_cat_doc
        n.contains("体育") || n.contains("體育") || n.contains("球") || n.contains("赛事") ||
            n.contains("Sport", ignoreCase = true) ->
            R.drawable.ic_cat_sport
        n.contains("剧集") || n.contains("劇集") || n.contains("连续剧") || n.contains("电视剧") ||
            n.contains("TV", ignoreCase = true) || n.contains("Series", ignoreCase = true) ->
            R.drawable.ic_cat_tv
        n.contains("电影") || n.contains("電影") || n.contains("影") || n.contains("片") ||
            n.contains("Movie", ignoreCase = true) || n.contains("Film", ignoreCase = true) ->
            R.drawable.ic_cat_movie
        else -> R.drawable.ic_cat_other
    }
}

/** 导航栏的原子单元：图标 + 文字（前四项与分类 tab 共用，保证像素级一致的间距与选中态） */
@Composable
private fun NavItem(
    modifier: Modifier,
    iconRes: Int,
    label: String,
    selected: Boolean,
    primary: Color,
    dim: Color,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    // 图标与文字都用主题色染色：亮色主题是深灰/蓝，暗色主题是浅灰/亮蓝，自己跟着换
    val tint = if (selected) primary else dim
    Box(
        modifier = modifier.clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) primary.copy(alpha = 0.12f) else Color.Transparent)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(tint)
            )
            if (showLabel) {
                Spacer(Modifier.width(4.dp))
                BasicText(
                    text = label,
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

// 说明：原「分类条 + 四图标 dock 两行」已合并为单条 NavBar（v1.6.30）；
// v1.6.32 起四项与分类统一为等宽的 NavItem，全部带主题自适应图标，并去掉了中间的竖分隔线。

