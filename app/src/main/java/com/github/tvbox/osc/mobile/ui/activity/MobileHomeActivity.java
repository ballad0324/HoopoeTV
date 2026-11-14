package com.github.tvbox.osc.mobile.ui.activity;

import com.github.tvbox.osc.mobile.ui.drawable.UiSkin;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.compose.ui.platform.ComposeView;
import androidx.viewpager.widget.ViewPager;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.mobile.setting.MobileSetting;
import com.github.tvbox.osc.mobile.ui.LiquidMotion;
import com.github.tvbox.osc.mobile.ui.MobileFavorites;
import com.github.tvbox.osc.mobile.ui.MobilePlayHistory;
import com.github.tvbox.osc.mobile.ui.activity.MobileDiscoverActivity;
import com.github.tvbox.osc.mobile.ui.adapter.MobileHistoryAdapter;
import com.github.tvbox.osc.mobile.ui.adapter.MobileRecordAdapter;
import com.github.tvbox.osc.mobile.ui.adapter.MobileRecordPosterAdapter;
import com.github.tvbox.osc.mobile.ui.adapter.MobileHomePagerAdapter;
import com.github.tvbox.osc.mobile.ui.adapter.MobileTypeAdapter;
import com.github.tvbox.osc.mobile.ui.adapter.MobileWeeklyAdapter;
import com.github.tvbox.osc.setting.CommonSetting;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.mobile.ui.fragment.MobileGridFragment;
import com.github.tvbox.osc.mobile.ui.compose.HomeDockHost;
import com.github.tvbox.osc.mobile.ui.compose.HomeDockState;
import com.github.tvbox.osc.mobile.ui.compose.MobileHomeDock;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 手机端首页：与 TV 端 HomeActivity 共用同一套内核（ApiConfig / SourceViewModel / GridFragment），
 * 只换外壳（toolbar + 横向分类 tab + 底部导航），不复用 TV 的左侧拨轮与横屏布局。
 */
public class MobileHomeActivity extends BaseActivity implements MobileGridFragment.HeaderBinder, HomeDockHost {

    private ViewPager pager;
    private ImageView btnTheme;
    private ImageView btnUiMode;
    private ImageView btnSetting;
    /** 底部导航栏（Compose 绘制：分类 tab + 四图标 dock） */
    private ComposeView cvDock;

    /** 首页顶部「继续观看」模块 */
    private LinearLayout cardHistory;
    private RecyclerView rvHistory;
    private TextView tvHistoryTitle;
    private TextView tvHistoryClear;
    /** 内容面板标题（随分类 tab 变化） */
    private TextView tvSectionTitle;
    private TextView tvSectionMore;
    private MobileRecordPosterAdapter historyAdapter;
    private TextView tvHistoryEmpty;

    /** 首页顶部「本周推荐」模块：横向大卡，数据取首页推荐列表前 10 条 */
    private LinearLayout cardFav;
    private RecyclerView rvFav;
    private TextView tvFavEmpty;
    private MobileRecordPosterAdapter favAdapter;
    private LinearLayout cardWeekly;
    private RecyclerView rvWeekly;
    private TextView btnWeeklyPlay;
    private MobileWeeklyAdapter weeklyAdapter;

    /** 首页内容面板（标题 + 分类列表）；首页那一页不显示它，其它分类页照常显示 */
    private LinearLayout panelContent;
    private LinearLayout llHomeHeader;
    /** 收藏/历史双卡的原始高度（懒取，按屏幕计算） */
    private int homeHeaderHeight = -1;

    /** 首页收起列表面板时占位的空白，用来把底部 dock 栏顶到屏幕底部 */
    private View panelSpacer;

    private MobileTypeAdapter typeAdapter;
    private MobileHomePagerAdapter pagerAdapter;
    private final List<Fragment> fragments = new ArrayList<>();

    private SourceViewModel sourceViewModel;
    /** 上次构建分类用的数据源 key，用于检测设置页里是否换了源 */
    private String lastHomeKey;
    /** 是否因为设置页改了接口地址而正在重载配置（用于给出「已重新加载」反馈） */
    private boolean cfgReloading;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home_mobile;
    }

    /** 手机端按 360dp 设计基准，避免继承 TV 的 1280 基准把界面放大 */
    @Override
    public float getSizeInDp() {
        return 360;
    }

    @Override
    protected void init() {
        // 上次跑挂了就弹窗回显堆栈（可一键复制）
        com.github.tvbox.osc.util.CrashLog.showLastIfAny(this);
        initView();
        initEvent();
        initObserver();
        loadConfig();
    }

    private void initView() {
        pager = findViewById(R.id.pager);
        btnTheme = findViewById(R.id.btnTheme);
        btnUiMode = findViewById(R.id.btnUiMode);
        btnSetting = findViewById(R.id.btnSetting);
        cvDock = findViewById(R.id.cvDock);

        // 收藏/历史/小编推荐标题已移进列表首项（item_home_header），这里只留容器
        panelContent = findViewById(R.id.panelContent);
        panelSpacer = findViewById(R.id.panelSpacer);

        // 液态玻璃（代码 new；XML 自定义标签会崩）
        UiSkin.card(findViewById(R.id.toolbar));
        UiSkin.card(panelContent);

        // 顶部 logo / 标题点击 = 数据源切换
        View.OnClickListener sourceClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSourceSwitcher();
            }
        };
        View logo = findViewById(R.id.logo);
        View title = findViewById(R.id.title);
        if (logo != null) logo.setOnClickListener(sourceClick);
        if (title != null) title.setOnClickListener(sourceClick);

        // 分类数据仍由 MobileTypeAdapter 持有（不再挂 RecyclerView，纯数据容器），
        // 渲染交给底部 Compose 分类条
        typeAdapter = new MobileTypeAdapter();

        pagerAdapter = new MobileHomePagerAdapter(getSupportFragmentManager(), fragments);
        pager.setAdapter(pagerAdapter);

        // 底部导航栏：Compose 重写版（分类 tab + 四图标 dock）
        if (cvDock != null) MobileHomeDock.bindHomeDock(cvDock, this);

        applyDisplaySettings();

        // Liquid Glass 动效：顶栏液态按压 + 顶栏玻璃周期性扫光（底部改为 Compose 自带按压反馈）
        LiquidMotion.bindPress(btnTheme, 0.88f);
        LiquidMotion.bindPress(btnUiMode, 0.88f);
        LiquidMotion.bindPress(btnSetting, 0.88f);
        // toolbar：顶 6dp / 高 56dp / 左右 10dp / 圆角 22dp，与 glass_card 完全对齐
        LiquidMotion.attachHeaderSheen(this, 6f, 56f, 10f, 22f);
    }

    /** 应用手机端专有显示开关（设置页改完返回首页即生效） */
    private void applyDisplaySettings() {
        refreshThemeButton();
        // 「搜索入口」开关控制底栏第三个图标显隐；「显示导航文字」控制图标下方文字
        HomeDockState.updateShowSearch(MobileSetting.isShowSearchBar());
        HomeDockState.updateShowLabels(MobileSetting.isShowNavLabel());
    }

    /** 顶栏明暗切换：深色 ↔ 明亮，立即生效（AppCompatDelegate 会重建界面） */
    private void toggleNightMode() {
        int cur = CommonSetting.getNightMode();
        int next = (cur == CommonSetting.NIGHT_DARK) ? CommonSetting.NIGHT_LIGHT : CommonSetting.NIGHT_DARK;
        CommonSetting.putNightMode(next);
        Toast.makeText(this, next == CommonSetting.NIGHT_DARK ? "已切换到深色模式" : "已切换到明亮模式",
                Toast.LENGTH_SHORT).show();
        refreshThemeButton();
    }

    /** 深色模式下图标金色高亮、明亮模式下灰色，让当前状态一眼可辨 */
    private void refreshThemeButton() {
        int color = getResources().getColor(
                CommonSetting.isNightUi() ? R.color.shell_gold : R.color.shell_text_dim);
        if (btnTheme != null) btnTheme.setColorFilter(color);
        if (btnUiMode != null) btnUiMode.setColorFilter(color);
        if (btnSetting != null) btnSetting.setColorFilter(color);
    }

    /** 右上角一键切到电视端：写 ui_mode 后清栈重启，由 HomeActivity 分流到 TV 首页 */
    private void switchToTvUi() {
        if (CommonSetting.getUiMode() == CommonSetting.UI_TV) return;
        CommonSetting.putUiMode(CommonSetting.UI_TV);
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyDisplaySettings();
        refreshHistory();
        // 从详情页收藏/取消收藏返回时，必须重读本机收藏，否则首页「我的收藏」永远停在旧数据
        refreshFav();
        // 从直播页/设置页返回时，底部导航选中态复位到「影视」，并恢复分类段
        // （直播/搜索/发现/详情等页会把分类段藏起来，只留四个入口）
        HomeDockState.updateCategoriesVisible(true);
        HomeDockState.updateSelectedDock(0);
        // 设置页改过「接口地址」：这里就地重载配置并重建首页，不再要求用户杀进程重启
        if (CommonSetting.consumeConfigDirty()) {
            lastHomeKey = null;
            cfgReloading = true;
            loadConfig(false);
            return;
        }
        checkSourceChanged();
    }

    /** 设置页里换了「数据源」：回到首页必须重新拉分类，否则一直停在上一个源的分类列表 */
    private void checkSourceChanged() {
        String key = currentKey();
        if (lastHomeKey == null) {
            lastHomeKey = key;
            return;
        }
        if (lastHomeKey.equals(key)) return;
        lastHomeKey = key;
        fragments.clear();
        pagerAdapter.notifyDataSetChanged();
        loadSort();
    }

    private void initEvent() {
        btnUiMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToTvUi();
            }
        });
        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleNightMode();
            }
        });
        btnSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 设置入口已移到右上角（明暗切换右侧）：公共项 → CommonSetting，手机专有项 → MobileSetting
                jumpActivity(MobileSettingActivity.class);
            }
        });

        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                // 分类条的选中态与自动滚进可视区都在 Compose 侧（HomeDockBar.kt）
                HomeDockState.updateSelectedCategory(position);
                HomeDockState.updateSelectedDock(0);
                updateSectionTitle(position);
                // 「本周推荐 / 历史播放」只属于首页那页，切到其它分类页要给回原来的纯列表
                applyHomeOnlyBlocks(position);
            }
        });
    }

    /** Compose 底栏回调：点了第 index 个分类 */
    @Override
    public void onCategorySelected(int index) {
        if (index >= 0 && index < fragments.size() && pager != null) {
            pager.setCurrentItem(index, false);
        }
    }

    /** Compose 底栏回调：点了 dock 图标（0 影视 / 1 直播 / 2 搜索 / 3 发现） */
    @Override
    public void onDockClick(int tab) {
        switch (tab) {
            case 0:
                if (pager != null && pager.getCurrentItem() != 0) pager.setCurrentItem(0, false);
                break;
            case 1:
                jumpActivity(MobileLiveActivity.class);
                break;
            case 2:
                jumpActivity(MobileSearchActivity.class);
                break;
            case 3:
                jumpActivity(MobileDiscoverActivity.class, new Bundle());
                break;
            default:
                break;
        }
    }

    /**
     * 首页列表首项（收藏/历史/小编推荐标题）的绑定入口。
     *
     * v1.6.27 起这块不再挂在 Activity 的固定区域上，而是作为网格的第一项（item_home_header）
     * 随列表一起滚动：滚动时是 RecyclerView 自己在移动内容，不会再出现「改容器高度把卡片裁掉一截」
     * 和「改高度→重排→反向 dy」造成的回弹。
     */
    @Override
    public void bindHeader(View root) {
        if (root == null) return;
        cardFav = root.findViewById(R.id.cardFav);
        rvFav = root.findViewById(R.id.rvFav);
        tvFavEmpty = root.findViewById(R.id.tvFavEmpty);
        cardHistory = root.findViewById(R.id.cardHistory);
        rvHistory = root.findViewById(R.id.rvHistory);
        tvHistoryEmpty = root.findViewById(R.id.tvHistoryEmpty);
        tvHistoryTitle = root.findViewById(R.id.tvHistoryTitle);
        tvSectionTitle = root.findViewById(R.id.tvSectionTitle);
        tvSectionMore = root.findViewById(R.id.tvSectionMore);

        if (favAdapter == null) {
            favAdapter = new MobileRecordPosterAdapter();
            rvFav.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            new PagerSnapHelper().attachToRecyclerView(rvFav);
            rvFav.setAdapter(favAdapter);
            favAdapter.setOnItemAction(new MobileRecordAdapter.OnItemAction() {
                @Override
                public void onClick(MobileRecordAdapter.Row row) {
                    openRecord(row);
                }

                @Override
                public void onLongClick(MobileRecordAdapter.Row row) {
                    confirmDelete(row, true);
                }
            });
        }
        if (historyAdapter == null) {
            historyAdapter = new MobileRecordPosterAdapter();
            rvHistory.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            new PagerSnapHelper().attachToRecyclerView(rvHistory);
            rvHistory.setAdapter(historyAdapter);
            historyAdapter.setOnItemAction(new MobileRecordAdapter.OnItemAction() {
                @Override
                public void onClick(MobileRecordAdapter.Row row) {
                    openRecord(row);
                }

                @Override
                public void onLongClick(MobileRecordAdapter.Row row) {
                    confirmDelete(row, false);
                }
            });
        }

        View favTitle = root.findViewById(R.id.tvFavTitle);
        if (favTitle instanceof TextView) ((TextView) favTitle).setText(tr("我的收藏", "Favorites"));
        View favMore = root.findViewById(R.id.tvFavMore);
        if (favMore != null) favMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jumpRecord(MobileRecordActivity.MODE_FAV);
            }
        });
        if (tvHistoryTitle != null) tvHistoryTitle.setText(tr("播放历史", "History"));
        View historyMore = root.findViewById(R.id.tvHistoryMore);
        if (historyMore != null) historyMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jumpRecord(MobileRecordActivity.MODE_HISTORY);
            }
        });
        // 「小编推荐」右侧「更多」：引导到发现页（专题 / 榜单 / 正在看）
        if (tvSectionMore != null) {
            tvSectionMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    jumpActivity(MobileDiscoverActivity.class, new Bundle());
                }
            });
        }
        if (tvSectionTitle != null) tvSectionTitle.setText(tr("小编推荐", "Editor's Picks"));

        refreshFav();
        refreshHistory();
    }

    private void applyHomeOnlyBlocks(int position) {
        // 首项只挂在首页那个 fragment 上，切分类页时天然不在列表里，这里只需要处理标题
        updateSectionTitle(position);
    }

    private void refreshHistory() {
        if (cardHistory == null || historyAdapter == null) return;
        List<MobileRecordAdapter.Row> rows = new ArrayList<>();
        for (MobilePlayHistory.Item it : MobilePlayHistory.load()) {
            MobileRecordAdapter.Row row = new MobileRecordAdapter.Row();
            row.name = it.name;
            row.pic = it.pic;
            row.vodId = it.vodId;
            row.sourceKey = it.sourceKey;
            row.flag = it.flag;
            row.sub = it.subtitle();
            rows.add(row);
            if (rows.size() >= 6) break;
        }
        historyAdapter.setNewData(rows);
        if (tvHistoryEmpty != null) {
            tvHistoryEmpty.setText(tr("还没有播放记录", "Nothing played yet"));
            tvHistoryEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (rvHistory != null) rvHistory.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void refreshFav() {
        if (cardFav == null || favAdapter == null) return;
        List<MobileRecordAdapter.Row> rows = new ArrayList<>();
        for (MobileFavorites.Item it : MobileFavorites.load()) {
            MobileRecordAdapter.Row row = new MobileRecordAdapter.Row();
            row.name = it.name;
            row.pic = it.pic;
            row.vodId = it.vodId;
            row.sourceKey = it.sourceKey;
            row.sub = (it.note == null || it.note.length() == 0) ? tr("已收藏", "Saved") : it.note;
            rows.add(row);
            if (rows.size() >= 6) break;
        }
        favAdapter.setNewData(rows);
        if (tvFavEmpty != null) {
            tvFavEmpty.setText(tr("还没有收藏", "No favorites yet"));
            tvFavEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (rvFav != null) rvFav.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void openRecord(MobileRecordAdapter.Row row) {
        if (row == null || TextUtils.isEmpty(row.vodId)) return;
        Bundle bundle = new Bundle();
        bundle.putString("id", row.vodId);
        bundle.putString("sourceKey", row.sourceKey);
        bundle.putString("title", row.name);
        jumpActivity(MobileDetailActivity.class, bundle);
    }

    private void jumpRecord(String mode) {
        Intent intent = new Intent(this, MobileRecordActivity.class);
        intent.putExtra("mode", mode);
        startActivity(intent);
    }

    /** 长按卡片：弹删除确认 */
    private void confirmDelete(final MobileRecordAdapter.Row row, final boolean fav) {
        if (row == null) return;
        new AlertDialog.Builder(this)
                .setTitle(tr("删除", "Delete"))
                .setMessage((fav ? tr("从我的收藏里移除", "Remove from favorites") : tr("从播放历史里移除", "Remove from history"))
                        + "「" + row.name + "」？")
                .setNegativeButton(tr("取消", "Cancel"), null)
                .setPositiveButton(tr("删除", "Delete"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (fav) {
                            MobileFavorites.remove(row.vodId, row.sourceKey);
                            refreshFav();
                        } else {
                            MobilePlayHistory.remove(row.vodId, row.sourceKey);
                            refreshHistory();
                        }
                    }
                })
                .show();
    }
    /** 点历史卡：回到详情页并自动从上次的线路/集数续播 */
    /** 本周推荐卡点击：直接进详情页 */
    private void openWeekly(Movie.Video video) {
        if (video == null) return;
        Bundle bundle = new Bundle();
        bundle.putString("id", video.id);
        bundle.putString("sourceKey", video.sourceKey);
        bundle.putString("title", video.name);
        jumpActivity(MobileDetailActivity.class, bundle);
    }

    /** 首页推荐页第一页加载完成后，把片单回填到「本周推荐」横向卡（去重、最多 10 条） */
    public void onHomeListLoaded(List<Movie.Video> list) {
        if (list == null || list.isEmpty() || weeklyAdapter == null) return;
        List<Movie.Video> picked = new ArrayList<>();
        for (Movie.Video v : list) {
            if (v == null) continue;
            boolean dup = false;
            for (Movie.Video p : picked) {
                if (p.id != null && v.id != null && p.id.equals(v.id)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) picked.add(v);
            if (picked.size() >= 20) break;
        }
        weeklyAdapter.setNewData(picked);
    }

    /** 顶部 logo / 标题点击：弹出当前接口里的站点列表，选一个立即换成数据源并重载首页 */
    private void showSourceSwitcher() {
        List<SourceBean> all = ApiConfig.get().getSourceBeanList();
        final List<SourceBean> shown = new ArrayList<>();
        if (all != null) {
            for (SourceBean bean : all) {
                if (bean != null && bean.getHide() != 1 && !TextUtils.isEmpty(bean.getName())) shown.add(bean);
            }
        }
        if (shown.isEmpty()) {
            Toast.makeText(this, tr("还没有可用数据源，请先到设置页填接口地址",
                    "No sources yet, set the API URL in Settings"), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[shown.size()];
        int checked = 0;
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        String homeKey = home == null ? null : home.getKey();
        for (int i = 0; i < shown.size(); i++) {
            names[i] = shown.get(i).getName();
            if (homeKey != null && homeKey.equals(shown.get(i).getKey())) checked = i;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(tr("切换数据源", "Switch data source"))
                .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        SourceBean bean = shown.get(which);
                        ApiConfig.get().setSourceBean(bean);
                        Toast.makeText(MobileHomeActivity.this,
                                tr("已切换到「", "Switched to ") + bean.getName() + "」",
                                Toast.LENGTH_SHORT).show();
                        // 立刻按新数据源重建分类与内容，不再等下次启动
                        lastHomeKey = null;
                        loadConfig(false);
                    }
                })
                .setNegativeButton(tr("取消", "Cancel"), null)
                .show();
    }

    private void openHistory(MobilePlayHistory.Item item) {
        Bundle bundle = new Bundle();
        bundle.putString("id", item.vodId);
        bundle.putString("sourceKey", item.sourceKey);
        bundle.putString("title", item.name);
        bundle.putString("histFlag", item.flag);
        bundle.putInt("histIndex", item.playIndex);
        bundle.putBoolean("autoPlay", true);
        jumpActivity(MobileDetailActivity.class, bundle);
    }

    private void updateSectionTitle(int position) {
        // 标题行已随收藏/历史一起进列表首项（只在首页那页存在），分类页不做处理
        if (tvSectionTitle == null) return;
        if (position != 0) return;
        tvSectionTitle.setText(tr("小编推荐", "Editor's Picks"));
    }

    private String sectionTitle(int position) {
        if (position == 0) return tr("小编推荐", "Editor's Picks");
        try {
            if (typeAdapter != null) {
                List<MovieSort.SortData> data = typeAdapter.getData();
                if (position >= 0 && position < data.size() && data.get(position) != null) {
                    String name = data.get(position).name;
                    if (!TextUtils.isEmpty(name)) return name;
                }
            }
        } catch (Throwable ignored) {
        }
        return tr("推荐", "For you");
    }

    private void initObserver() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(@Nullable AbsSortXml absSortXml) {
                buildPages(absSortXml);
            }
        });
        // 首页那页的列表面板是收起的，配一条自己发的首页取数，专门喂「本周推荐」
        sourceViewModel.listResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(@Nullable AbsXml absXml) {
                if (absXml != null && absXml.movie != null) {
                    onHomeListLoaded(absXml.movie.videoList);
                }
            }
        });
    }

    private void loadConfig() {
        loadConfig(true);
    }

    /**
     * @param useCache true 走本地缓存（正常启动）；false 强制网络重拉（设置页刚改了接口地址时用）
     */
    private void loadConfig(boolean useCache) {
        ApiConfig.get().loadConfig(useCache, new ApiConfig.LoadConfigCallback() {
            @Override
            public void success() {
                if (cfgReloading) {
                    cfgReloading = false;
                    Toast.makeText(MobileHomeActivity.this, "配置已重新加载", Toast.LENGTH_SHORT).show();
                }
                loadSort();
            }

            @Override
            public void retry() {
                if (cfgReloading) {
                    cfgReloading = false;
                    Toast.makeText(MobileHomeActivity.this, "配置已重新加载", Toast.LENGTH_SHORT).show();
                }
                loadSort();
            }

            @Override
            public void error(String msg) {
                // 未配置接口或加载失败时仍要出壳，避免白屏；同时把具体原因告诉用户
                cfgReloading = false;
                if (!TextUtils.isEmpty(msg)) {
                    Toast.makeText(MobileHomeActivity.this, "接口加载失败：" + msg, Toast.LENGTH_LONG).show();
                }
                loadSort();
            }
        }, this);
    }

    private void loadSort() {
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        String key = (home != null) ? home.getKey() : null;
        if (key == null || key.isEmpty()) {
            // 接口未配置/加载失败：只能出空壳。旧实现拿空串去查源，getSource 返回 null 后 .getType() 直接 NPE 闪退
            buildPages(null);
            showInterfaceTip();
            return;
        }
        sourceViewModel.getSort(key);
    }

    /** 接口为空/不可用时的明确提示，而不是静默给一屏空白 */
    private void showInterfaceTip() {
        Toast.makeText(this, "未配置接口或接口加载失败，请到「设置 → 接口」填写接口地址", Toast.LENGTH_LONG).show();
    }

    private void buildPages(AbsSortXml absXml) {
        lastHomeKey = currentKey();
        List<MovieSort.SortData> list;
        if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
            list = DefaultConfig.adjustSort(currentKey(), absXml.classes.sortList, true);
        } else {
            list = DefaultConfig.adjustSort(currentKey(), new ArrayList<MovieSort.SortData>(), true);
        }

        // 与 TV 端一致：去掉「应用」行，它在手机端不适用
        List<MovieSort.SortData> shown = new ArrayList<>();
        for (MovieSort.SortData data : list) {
            if (!SortAdapter.isAppRow(data)) shown.add(data);
        }

        typeAdapter.setNewData(shown);
        // 分类名灌进 Compose 底栏：底部那排可滑分类 tab 就是它
        List<String> names = new ArrayList<>();
        for (MovieSort.SortData data : shown) {
            String name = (data == null) ? null : data.name;
            names.add(TextUtils.isEmpty(name) ? "" : name);
        }
        if (names.isEmpty()) {
            names.add(tr("首页", "Home"));
        }
        HomeDockState.updateCategories(names);
        HomeDockState.updateSelectedCategory(0);
        HomeDockState.updateSelectedDock(0);
        fragments.clear();
        if (shown.isEmpty()) {
            // 没有分类时给一页空态，避免 pager 空适配器
            fragments.add(MobileGridFragment.newInstance(null));
        } else {
            for (MovieSort.SortData data : shown) {
                fragments.add(MobileGridFragment.newInstance(data));
            }
        }
        if (!fragments.isEmpty() && fragments.get(0) instanceof MobileGridFragment) {
            // 只有首页那一页挂收藏/历史首项，其它分类页保持纯列表
            ((MobileGridFragment) fragments.get(0)).setHeaderBinder(this);
        }
        pagerAdapter.notifyDataSetChanged();
        pager.setCurrentItem(0, false);
        updateSectionTitle(0);
        // 首页那页的列表面板是收起的，fragment 拿不到首屏数据，这里自己拉一次首页第一页喂「本周推荐」
        if (!shown.isEmpty()) {
            try {
                sourceViewModel.getList(shown.get(0), 1);
            } catch (Exception ignored) {
            }
        }
        applyHomeOnlyBlocks(0);
    }

    private String currentKey() {
        if (ApiConfig.get().getHomeSourceBean() != null && ApiConfig.get().getHomeSourceBean().getKey() != null) {
            return ApiConfig.get().getHomeSourceBean().getKey();
        }
        return "";
    }

    /** 中英双语（手机端界面文案） */
    private String tr(String zh, String en) {
        return CommonSetting.getLanguage() == 1 ? en : zh;
    }

    @Override
    public void onBackPressed() {
        // 手机端首页按返回 = 退到桌面，不做 TV 的 VOD 删除/筛选回退逻辑
        moveTaskToBack(true);
    }

    /** 冷启动第一帧保留清单里的 Theme.MobileSplash，之后重建走普通主题 */
    private static boolean splashThemeConsumed = false;

    @Override
    protected boolean keepManifestTheme() {
        return !splashThemeConsumed;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        splashThemeConsumed = true;
        super.onCreate(savedInstanceState);
        if (!CommonSetting.isMobileUi()) {
            // 防御：launcher 已挂在本页，用户把界面模式切回「电视端」时转交给 TV 首页，
            // 不能直接 finish，否则点桌面图标会退回桌面。
            Intent tvIntent = new Intent(this, com.github.tvbox.osc.ui.activity.HomeActivity.class);
            tvIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(tvIntent);
            finish();
        }
    }
}
