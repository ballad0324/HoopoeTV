package com.github.tvbox.osc.ui.activity;

import android.Manifest;
import android.content.Context;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.github.tvbox.osc.BuildConfig;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.setting.CommonSetting;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.HomePageAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.fragment.UserFragment;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.FixedSpeedScroller;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;
import com.github.tvbox.osc.ui.tv.widget.ViewObj;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeActivity extends BaseActivity {

    // takagen99: Added to allow read string
    private static Resources res;

    private View currentView;
    private LinearLayout topLayout;
    private LinearLayout contentLayout;
    private TextView tvName;
    private ImageView tvWifi;
    private ImageView btnUiMode;
    private ImageView tvFind;
    private ImageView tvStyle;
    private ImageView tvDraw;
    private ImageView tvMenu;
    private TextView tvDate;
    private TvRecyclerView mGridView;
    private TvRecyclerView mGridViewLeft;
    private NoScrollViewPager mViewPager;
    private SourceViewModel sourceViewModel;
    private SortAdapter sortAdapter;
    private HomePageAdapter pageAdapter;
    private final List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean isDownOrUp = false;
    private boolean sortChange = false;
    private int currentSelected = 0;
    private int sortFocused = 0;
    // 上一次左栏布局应用时间：用于区分“用户在导航上选中”与“启动时自动落焦”
    private long lastLayoutApplyTime = 0L;
    public View sortFocusView = null;
    private final Handler mHandler = new Handler();
    private long mExitTime = 0;
    private final Runnable mRunnable = new Runnable() {
        @SuppressLint({"DefaultLocale", "SetTextI18n"})
        @Override
        public void run() {
            Date date = new Date();
            @SuppressLint("SimpleDateFormat")
			//修改时间分隔符
            SimpleDateFormat timeFormat = new SimpleDateFormat(getString(R.string.hm_date1) + " | " + getString(R.string.hm_date2));
            tvDate.setText(timeFormat.format(date));
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    boolean useCacheConfig = false;

    /**
     * 界面模式分流点：手机端模式下不加载 TV 首页布局（连 setContentView 都跳过），直接转给手机端首页。
     * 判断读的是 CommonSetting 的运行时偏好，不读 BuildConfig，所以能在设置页里随时切换。
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        boolean mobileUi = CommonSetting.isMobileUi();
        android.util.Log.i("HOOPOE_UI", "onCreate: uiMode=" + CommonSetting.getUiMode()
                + " isMobileUi=" + mobileUi + " buildMobile=" + BuildConfig.UI_MOBILE);
        if (mobileUi) {
            // 必须赶在 BaseActivity 设置 TV 主题之前定下启动图主题
            setTheme(R.style.Theme_MobileSplash);
        }
        // 关键：任何分支都要调 super.onCreate。旧实现重定向后直接 return，导致 SuperNotCalledException 闪退。
        // BaseActivity 内部按 shouldInitContent() 决定是否加载 TV 首页布局与 init()。
        super.onCreate(savedInstanceState);
        if (mobileUi) {
            if (!redirectToMobileHome()) {
                // 手机端首页类缺失（仅 TV 变体会发生）：回退加载 TV 首页，保证不出白屏
                setContentView(getLayoutResID());
                init();
            }
        }
    }

    @Override
    protected boolean shouldInitContent() {
        return !CommonSetting.isMobileUi();
    }

    @Override
    protected void init() {
        // takagen99: Added to allow read string
        res = getResources();

        EventBus.getDefault().register(this);
        ControlManager.get().startServer();
        App.startWebserver();
        initView();
        initViewModel();
        useCacheConfig = false;
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            useCacheConfig = bundle.getBoolean("useCache", false);
        }
        initData();
    }

    // takagen99: Added to allow read string
    public static Resources getRes() {
        return res;
    }

    /**
     * 手机端入口重定向：用 Class.forName 避免 main 源集对 mobile 源集的编译期依赖，
     * TV 变体里该包不存在，异常即回退到 TV 首页逻辑。
     */
    private boolean redirectToMobileHome() {
        try {
            Intent intent = new Intent(this, Class.forName("com.github.tvbox.osc.mobile.ui.activity.MobileHomeActivity"));
            startActivity(intent);
            finish();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 首页与分类内容页统一为：分类导航在左、内容在右。
     * 首页顶部保留「应用/直播/收藏/文件/历史/推送」横排（由 UserFragment 承载）。
     */
    private boolean categoryLayoutApplied = false;
    // 上一次翻页的目标页，用来决定内容向上滑还是向下滑
    private int lastTransitionPage = 0;
    private boolean categoryLayoutGridMode = true;
    // 布局切换期间为 true，用于屏蔽 RecyclerView 重绑导致的伪选中回调
    private boolean layoutSwitching = false; // build28 marker

    private TvRecyclerView navView() {
        return categoryLayoutGridMode ? this.mGridViewLeft : this.mGridView;
    }

    /**
     * 位置换算：分类列表里不再放「首页」（首页在顶部应用栏第一格），
     * 适配器位置 0 = 第一个分类（豆瓣热门电影），对应内容页 1；
     * 内容页 0 永远是首页。
     */
    private boolean isAppRow(int adapterPos) {
        if (this.sortAdapter == null || adapterPos < 0 || adapterPos >= this.sortAdapter.getItemCount()) {
            return false;
        }
        return SortAdapter.isAppRow(this.sortAdapter.getItem(adapterPos));
    }

    private int adapterPosOfPage(int page) {
        return page <= 0 ? 0 : page - 1;
    }

    private int pageOfAdapterPos(int adapterPos) {
        return adapterPos < 0 ? 0 : adapterPos + 1;
    }

    private MovieSort.SortData sortDataOfPage(int page) {
        if (this.sortAdapter == null || page <= 0) {
            return null;
        }
        int adapterPos = adapterPosOfPage(page);
        if (adapterPos >= this.sortAdapter.getItemCount()) {
            return null;
        }
        return this.sortAdapter.getItem(adapterPos);
    }

    // 分类列表里去掉「首页」那一项（它已经放到顶部应用栏第一格）
    private List<MovieSort.SortData> withoutHomeRow(List<MovieSort.SortData> src) {
        List<MovieSort.SortData> out = new ArrayList<>();
        if (src != null) {
            for (MovieSort.SortData data : src) {
                if (data == null) {
                    continue;
                }
                if ("my0".equals(data.id)) {
                    continue;
                }
                out.add(data);
            }
        }
        return out;
    }

    /**
     * 顶部「首页」格：回到第 0 页（首页内容），左栏保持显示但不高亮任何分类。
     */
    private void goHomePage() {
        try {
            mHandler.removeCallbacks(mDataRunnable);
            sortFocused = 0;
            currentSelected = 0;
            changeTop(false);
            if (mViewPager != null && mViewPager.getAdapter() != null) {
                mViewPager.setCurrentItem(0, false);
            }
            playPageTransition();
            if (navView() != null) {
                navView().clearFocus();
                navView().setSelectedPosition(-1);
            }
        } catch (Throwable ignored) {
            // 切换失败保持当前页
        }
    }

    /**
     * 统一的切页入口：左栏「选中」和「单击」都走这里，保证点/选任意分类右侧立刻显示该分类内容。
     * 不做延时、不依赖选中态回调，避免“点前几个导航不生效”。
     */
    private void switchToPage(final int page, final boolean animate) {
        if (page < 0 || page >= fragments.size() || mViewPager == null) {
            return;
        }
        // 已经就在这一页，只动过渡动画，不重复切
        if (page == currentSelected && mViewPager.getCurrentItem() == page) {
            if (animate) {
                playPageTransition();
            }
            return;
        }
        mHandler.removeCallbacks(mDataRunnable);
        sortFocused = page;
        currentSelected = page;
        // 顶栏保持显示，避免进入分类页时整页向上跳动
        changeTop(false);
        try {
            mViewPager.setCurrentItem(page, false);
        } catch (Throwable ignored) {
            // 保持当前页
        }
        if (animate) {
            playPageTransition();
        }
        // 视图刚切换时 page 可能被重新测量吞掉，一帧后再兜底一次
        mViewPager.post(() -> {
            try {
                if (currentSelected == page && mViewPager.getAdapter() != null
                        && mViewPager.getCurrentItem() != page) {
                    mViewPager.setCurrentItem(page, false);
                }
            } catch (Throwable ignored) {
                // 保持当前页
            }
        });
    }

    private void applyCategoryLayout(final boolean gridMode) {
        if (this.mGridView == null || this.mGridViewLeft == null) {
            return;
        }
        // 统一走“左栏竖排 + 右侧内容”布局：首页与分类页都不再用顶部横排导航
        this.mGridView.post(() -> applyCategoryLayoutNow(true));
    }

    private void applyCategoryLayoutNow(boolean gridMode) {
        if (this.mGridView == null || this.mGridViewLeft == null || this.mViewPager == null) {
            return;
        }
        // 状态没变化就不重复改，避免来回抖动
        if (categoryLayoutApplied && categoryLayoutGridMode == gridMode) {
            return;
        }
        try {
            layoutSwitching = true;
            lastLayoutApplyTime = System.currentTimeMillis();
            final TvRecyclerView target = gridMode ? this.mGridViewLeft : this.mGridView;
            // 只切显隐：首页顶部横排导航在分类页整体隐藏，左栏竖排导航在分类页显示
            this.mGridView.setVisibility(gridMode ? View.GONE : View.VISIBLE);
            this.mGridViewLeft.setVisibility(gridMode ? View.VISIBLE : View.GONE);
            categoryLayoutGridMode = gridMode;
            SortAdapter.verticalMode = gridMode;
            if (this.sortAdapter != null) {
                this.mGridView.setAdapter(target == this.mGridView ? this.sortAdapter : null);
                this.mGridViewLeft.setAdapter(target == this.mGridViewLeft ? this.sortAdapter : null);
            }
            final int pageKeep = this.sortFocused >= 0 ? this.sortFocused : 0;
            final int keep = adapterPosOfPage(pageKeep);
            if (pageKeep <= 0) {
                // 首页：左栏不标记选中项（否则第一个分类看着已选中、右侧却是首页内容）
                target.setSelectedPosition(-1);
            } else {
                target.setSelection(keep);
                target.setSelectedPosition(keep);
            }
            // 顶部导航隐藏/左栏显示会改变 ViewPager 可用宽度，必须让它重新测量；
            // 否则页面子视图仍按旧宽度绘制，溢出到左侧导航下面（表现为左栏与卡片互相叠压）
            this.mViewPager.requestLayout();
            this.mViewPager.post(() -> {
                try {
                    HomeActivity.this.mViewPager.requestLayout();
                    if (HomeActivity.this.mViewPager.getAdapter() != null) {
                        HomeActivity.this.mViewPager.setCurrentItem(pageKeep, false);
                    }
                } catch (Throwable ignored) {
                    // 重新测量失败不影响后续操作
                }
            });
            target.post(() -> {
                try {
                    if (pageKeep <= 0) {
                        // 在首页时焦点交给顶部「首页」格，避免左栏抢焦点直接把页面切到第一个分类
                        View home = findViewById(R.id.tvHome);
                        if (home != null) {
                            home.requestFocus();
                        }
                        return;
                    }
                    View keepView = target.getLayoutManager() == null ? null
                            : target.getLayoutManager().findViewByPosition(keep);
                    if (keepView != null) {
                        HomeActivity.this.sortFocusView = keepView;
                        keepView.requestFocus();
                    }
                } catch (Throwable ignored) {
                    // 焦点恢复失败不影响布局
                }
            });
            // 无论首页还是分类页，守卫都要复位；否则 onItemSelected 一直被挡掉，
            // 表现为“点左栏分类右侧不切页”。放在这个 post 之外，避免上面 return 把它跳过。
            target.postDelayed(() -> layoutSwitching = false, 150);
            categoryLayoutApplied = true;
        } catch (Throwable ignored) {
            layoutSwitching = false;
        }
    }

    /**
     * 页面切换过渡：右侧内容像一张纸一样平滑上下滑动 + 淡入，左栏一起淡入。
     * 切到更靠后的分类时内容从下往上滑入，切回上面的分类时从上往下滑入。
     */
    private void playPageTransition() {
        try {
            final float offset = AutoSizeUtils.mm2px(this.mContext, 25f);
            float dir = 1.0f;
            if (mViewPager != null && mViewPager.getAdapter() != null) {
                int target = currentSelected;
                dir = target >= lastTransitionPage ? 1.0f : -1.0f;
                lastTransitionPage = target;
            }
            this.mViewPager.setAlpha(0.4f);
            this.mViewPager.setTranslationY(offset * dir);
            this.mViewPager.animate().alpha(1.0f).translationY(0f)
                    .setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
            if (this.mGridViewLeft != null && this.mGridViewLeft.getVisibility() == View.VISIBLE) {
                this.mGridViewLeft.setAlpha(0.4f);
                this.mGridViewLeft.animate().alpha(1.0f)
                        .setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
            }
        } catch (Throwable ignored) {
            // 动画失败不影响切换
        }
    }

    /**
     * 两块导航控件共用同一套选中/点击/边界按键逻辑。
     */
    private void setupNavView(final TvRecyclerView nav) {
        nav.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (layoutSwitching) {
                    return;
                }
                if (isAppRow(position)) {
                    return;
                }
                if (view != null && !HomeActivity.this.isDownOrUp) {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(false);
                    textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF_70));
                    textView.invalidate();
                    view.findViewById(R.id.tvFilter).setVisibility(View.GONE);
                }
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (layoutSwitching) {
                    return;
                }
                // 「应用」是独立入口行，不参与分类选中逻辑
                if (isAppRow(position)) {
                    return;
                }
                if (view != null) {
                    HomeActivity.this.currentView = view;
                    HomeActivity.this.isDownOrUp = false;
                    HomeActivity.this.sortChange = true;
                    view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(250).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(true);
                    textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF));
                    textView.invalidate();
                    if (position == -1) {
                        position = 0;
                        nav.setSelection(0);
                    }
                    position = pageOfAdapterPos(position);
                    if (position < 0) {
                        return;
                    }
                    if (position < sortAdapter.getItemCount()) {
                        MovieSort.SortData sortData = sortDataOfPage(position);
                        // 分类检索图标已取消：筛选条在分类页底部常驻显示
                        if (null != sortData && !sortData.filters.isEmpty()) {
                            // 不再显示筛选图标，避免右侧多出内边距
                        }
                    }
                    HomeActivity.this.sortFocusView = view;
                    HomeActivity.this.sortFocused = position;
                    // 选中即切换：焦点落到哪个分类，右侧立刻显示该分类内容。
                    // 启动后 350ms 内属于布局自动落焦，不触发切换，避免一进首页就跳到第一个分类。
                    if (System.currentTimeMillis() - lastLayoutApplyTime > 350) {
                        switchToPage(position, true);
                    }
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                final int page = pageOfAdapterPos(position);
                if (page < 0 || page >= fragments.size()) {
                    return;
                }
                // 第一次点（当前不在该分类）→ 立即切到该分类并展示它的内容
                if (page != currentSelected || mViewPager.getCurrentItem() != page) {
                    switchToPage(page, true);
                    // 单击只负责切页；再点一下同一个分类才展开底部分类条
                    return;
                }
                if (itemView != null && currentSelected == page && page < fragments.size()) {
                    BaseLazyFragment baseLazyFragment = fragments.get(page);
                    if (baseLazyFragment instanceof GridFragment) {
                        MovieSort.SortData d = sortDataOfPage(page);
                        if (d != null && !d.filters.isEmpty()) {
                            // 点当前分类标签：展开/收起底部筛选条（默认收起）
                            ((GridFragment) baseLazyFragment).showFilter();
                        }
                    }
                }
            }
        });
        nav.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            public boolean onInBorderKeyEvent(int direction, View view) {
                if (sortFocused < 0 || sortFocused >= fragments.size()) {
                    return false;
                }
                if (direction == View.FOCUS_UP) {
                    BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                    if ((baseLazyFragment instanceof GridFragment)) {// 弹出筛选
                        ((GridFragment) baseLazyFragment).forceRefresh();
                    }
                }
                if (direction != View.FOCUS_DOWN) {
                    return false;
                }
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (!(baseLazyFragment instanceof GridFragment)) {
                    return false;
                }
                return !((GridFragment) baseLazyFragment).isLoad();
            }
        });
    }

    private void initView() {
        this.topLayout = findViewById(R.id.topLayout);
        this.tvName = findViewById(R.id.tvName);
        this.tvWifi = findViewById(R.id.tvWifi);
        this.btnUiMode = findViewById(R.id.btnUiMode);
        this.tvFind = findViewById(R.id.tvFind);
        this.tvStyle = findViewById(R.id.tvStyle);
        this.tvDraw = findViewById(R.id.tvDrawer);
        this.tvMenu = findViewById(R.id.tvMenu);
        this.tvDate = findViewById(R.id.tvDate);
        this.contentLayout = findViewById(R.id.contentLayout);
        this.mGridView = findViewById(R.id.mGridViewCategory);
        this.mGridViewLeft = findViewById(R.id.mGridViewCategoryLeft);
        this.mViewPager = findViewById(R.id.mViewPager);
        this.sortAdapter = new SortAdapter();
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        this.mGridView.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 10.0f));
        this.mGridViewLeft.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        this.mGridViewLeft.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 0.0f));
        this.mGridView.setAdapter(this.sortAdapter);
        setupNavView(this.mGridView);
        setupNavView(this.mGridViewLeft);
        sortAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                if (layoutSwitching) {
                    return;
                }
                final TvRecyclerView nav = navView();
                nav.post(() -> {
                    if (layoutSwitching || nav.getLayoutManager() == null) {
                        return;
                    }
                    // 首页（第 0 页）时左栏不高亮任何分类，避免“第一个分类看着已选中、右侧还是首页”
                    if (currentSelected <= 0) {
                        nav.setSelectedPosition(-1);
                        return;
                    }
                    nav.setSelectedPosition(adapterPosOfPage(currentSelected));
                });
            }
        });
        // 左上角源名称：点击弹出可选视频源列表，选完自动关闭并重新加载
        tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                List<SourceBean> sites = new ArrayList<>();
                for (SourceBean sb : ApiConfig.get().getSourceBeanList()) {
                    if (sb.getHide() == 0) sites.add(sb);
                }
                if (sites.isEmpty()) {
                    Toast.makeText(mContext, "当前没有可选视频源", Toast.LENGTH_SHORT).show();
                    return;
                }
                SelectDialog<SourceBean> dialog = new SelectDialog<>(HomeActivity.this);
                int spanCount = (int) Math.floor(sites.size() / 10);
                if (spanCount <= 1) spanCount = 1;
                if (spanCount >= 3) spanCount = 3;
                TvRecyclerView tvRecyclerView = dialog.findViewById(R.id.list);
                tvRecyclerView.setLayoutManager(new V7GridLayoutManager(dialog.getContext(), spanCount));
                ConstraintLayout cl_root = dialog.findViewById(R.id.cl_root);
                ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
                if (spanCount != 1) {
                    clp.width = AutoSizeUtils.mm2px(dialog.getContext(), 400 + 260 * (spanCount - 1));
                }
                dialog.setTip(getString(R.string.dia_source));
                dialog.setAdapter(tvRecyclerView, new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
                    @Override
                    public void click(SourceBean value, int pos) {
                        ApiConfig.get().setSourceBean(value);
                        dialog.dismiss();
                        reloadHome();
                    }

                    @Override
                    public String getDisplay(SourceBean val) {
                        return val.getName();
                    }
                }, new DiffUtil.ItemCallback<SourceBean>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                        return oldItem == newItem;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                        return oldItem.getKey().equals(newItem.getKey());
                    }
                }, sites, Math.max(sites.indexOf(ApiConfig.get().getHomeSourceBean()), 0));
                dialog.show();
            }
        });
        tvName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });
        // Button : UI Mode >> 切换到手机端
        btnUiMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (CommonSetting.getUiMode() == CommonSetting.UI_MOBILE) return;
                CommonSetting.putUiMode(CommonSetting.UI_MOBILE);
                Intent intent = new Intent(HomeActivity.this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
        // Button : Wifi >> Go into Android Wifi Settings -------------
        tvWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                }catch (Exception ignored){
                }
            }
        });
        // Button : Search --------------------------------------------
        tvFind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                jumpActivity(SearchActivity.class);
            }
        });
        // Button : Style --------------------------------------------
        // 样式切换按钮已下线：首页资源列表固定为网格（多行）显示
        try {
            tvStyle.setVisibility(View.GONE);
            Hawk.put(HawkConfig.HOME_REC_STYLE, true);
        } catch (Throwable ignored) {
            // 忽略
        }
        tvStyle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Hawk.put(HawkConfig.HOME_REC_STYLE, true);
                    UserFragment.tvHotListForGrid.setVisibility(View.VISIBLE);
                    UserFragment.tvHotListForLine.setVisibility(View.GONE);
                } catch (Exception ex) {
                }
            }
        });
        // Button : 顶部应用栏（直播/收藏/文件/历史/推送）+ 左侧「应用」
        View.OnClickListener appBarClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.tvLive) {
                    jumpActivity(LivePlayActivity.class);
                } else if (id == R.id.tvFavorite) {
                    jumpActivity(CollectActivity.class);
                } else if (id == R.id.tvDrive) {
                    jumpActivity(DriveActivity.class);
                } else if (id == R.id.tvHistory) {
                    jumpActivity(HistoryActivity.class);
                } else if (id == R.id.tvPush) {
                    jumpActivity(PushActivity.class);
                }
            }
        };
        int[] appBarIds = new int[]{R.id.tvLive, R.id.tvFavorite, R.id.tvDrive,
                R.id.tvHistory, R.id.tvPush};
        for (int appBarId : appBarIds) {
            View appBarItem = findViewById(appBarId);
            if (appBarItem != null) {
                appBarItem.setOnClickListener(appBarClick);
            }
        }
        // 顶部「首页」格 + 左侧「应用」块
        View tvHome = findViewById(R.id.tvHome);
        if (tvHome != null) {
            tvHome.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    goHomePage();
                }
            });
        }
        View tvApps = findViewById(R.id.tvApps);
        if (tvApps != null) {
            tvApps.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    jumpActivity(AppsActivity.class);
                }
            });
        }
        // 左侧分类行右侧的小按钮：点一下展开底部分类条，再点关闭
        SortAdapter.filterClick = new SortAdapter.OnFilterClick() {
            @Override
            public void onFilterClick(MovieSort.SortData item) {
                if (sortAdapter == null || item == null) {
                    return;
                }
                int pos = sortAdapter.getData().indexOf(item);
                if (pos < 0) {
                    return;
                }
                final int page = pageOfAdapterPos(pos);
                if (page < 0 || page >= fragments.size()) {
                    return;
                }
                if (page != currentSelected || mViewPager.getCurrentItem() != page) {
                    switchToPage(page, true);
                }
                mViewPager.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (page >= fragments.size()) {
                            return;
                        }
                        BaseLazyFragment f = fragments.get(page);
                        if (f instanceof GridFragment) {
                            ((GridFragment) f).toggleFilterBar();
                        }
                    }
                }, 220);
            }
        };
        // Button : Drawer >> To go into App Drawer -------------------
        tvDraw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                jumpActivity(AppsActivity.class);
            }
        });
        // Button : Settings >> To go into Settings --------------------
        tvMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                jumpActivity(SettingActivity.class);
            }
        });
        // Button : Settings >> To go into App Settings ----------------
        tvMenu.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
                return true;
            }
        });
        // Button : Date >> Go into Android Date Settings --------------
        tvDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
            }
        });
        setLoadSir(this.contentLayout);
        //mHandler.postDelayed(mFindFocus, 250);
    }
    //站点切换
    public static void homeRecf() {
        int homeRec = Hawk.get(HawkConfig.HOME_REC, -1);
        int limit = 2;
        if (homeRec == limit) homeRec = -1;
        homeRec++;
        Hawk.put(HawkConfig.HOME_REC, homeRec);
    }
    
    public static boolean reHome(Context appContext) {
        Intent intent = new Intent(appContext, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        appContext.startActivity(intent);
        return true;
    }

    private boolean skipNextUpdate = false;	
    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                if (skipNextUpdate) {
                    skipNextUpdate = false;
                    return;
                }
                showSuccess();
                if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                    sortAdapter.setNewData(withoutHomeRow(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), absXml.classes.sortList, true)));
                } else {
                    sortAdapter.setNewData(withoutHomeRow(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true)));
                }
                initViewPager(absXml);
                // takagen99 : Switch to show / hide source title
                SourceBean home = ApiConfig.get().getHomeSourceBean();
                if (HomeShow) {
                    if (home != null && home.getName() != null && !home.getName().isEmpty()) tvName.setText(home.getName());
                        tvName.clearAnimation();
                }
            }
        });
    }

    private boolean dataInitOk = false;
    private boolean jarInitOk = false;

    // takagen99 : Switch to show / hide source title
    boolean HomeShow = Hawk.get(HawkConfig.HOME_SHOW_SOURCE, false);

    // takagen99 : Check if network is available
    boolean isNetworkAvailable() {
        ConnectivityManager cm
                = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }

    private void initData() {
        // takagen99: If network available, check connected Wifi or Lan
        if (isNetworkAvailable()) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI) {
                tvWifi.setImageDrawable(res.getDrawable(R.drawable.hm_wifi));
            } else if (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_MOBILE) {
                tvWifi.setImageDrawable(res.getDrawable(R.drawable.hm_mobile));
            } else if (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_ETHERNET) {
                tvWifi.setImageDrawable(res.getDrawable(R.drawable.hm_lan));
            }
        }

        // takagen99: Set Style either Grid or Line
        if (Hawk.get(HawkConfig.HOME_REC_STYLE, true)) {
            tvStyle.setImageResource(R.drawable.hm_up_down);
        } else {
            tvStyle.setImageResource(R.drawable.hm_left_right);
        }

        navView().requestFocus();

        if (dataInitOk && jarInitOk) {
            sourceViewModel.getSort(ApiConfig.get().getHomeSourceBean().getKey());
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LOG.e("有");
            } else {
                LOG.e("无");
            }
            if (Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, false)) {
                jumpActivity(LivePlayActivity.class);
            }         
            return;
        }
        tvNameAnimation();
        showLoading();
        if (dataInitOk && !jarInitOk) {
            if (!ApiConfig.get().getSpider().isEmpty()) {
                ApiConfig.get().loadJar(useCacheConfig, ApiConfig.get().getSpider(), new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        jarInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!useCacheConfig) {
                                    Toast.makeText(HomeActivity.this, getString(R.string.hm_ok), Toast.LENGTH_SHORT).show();
                                }
                                initData();
                            }
                        }, 50);
                    }

                    @Override
                    public void retry() {

                    }

                    @Override
                    public void error(String msg) {
                        jarInitOk = true;
                        dataInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if ("".equals(msg))
                                    Toast.makeText(HomeActivity.this, getString(R.string.hm_notok), Toast.LENGTH_SHORT).show();
                                else
                                    Toast.makeText(HomeActivity.this, msg, Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        },50);
                    }
                });
            }
            return;
        }
        ApiConfig.get().loadConfig(useCacheConfig, new ApiConfig.LoadConfigCallback() {
            TipDialog dialog = null;

            @Override
            public void retry() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        initData();
                    }
                });
            }

            @Override
            public void success() {
                dataInitOk = true;
                if (ApiConfig.get().getSpider().isEmpty()) {
                    jarInitOk = true;
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initData();
                    }
                }, 50);
            }

            @Override
            public void error(String msg) {
                if (msg.equalsIgnoreCase("-1")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dataInitOk = true;
                            jarInitOk = true;
                            initData();
                        }
                    });
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog == null)
                            dialog = new TipDialog(HomeActivity.this, msg, getString(R.string.hm_retry), getString(R.string.hm_cancel), new TipDialog.OnListener() {
                                @Override
                                public void left() {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            initData();
                                            dialog.hide();
                                        }
                                    });
                                }

                                @Override
                                public void right() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            initData();
                                            dialog.hide();
                                        }
                                    });
                                }

                                @Override
                                public void cancel() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            initData();
                                            dialog.hide();
                                        }
                                    });
                                }
                            });
                        if (!dialog.isShowing())
                            dialog.show();
                    }
                });
            }
        }, this);
    }

    private void initViewPager(AbsSortXml absXml) {
        if (sortAdapter.getData().size() > 0) {
            // 首页固定为第 0 页（分类列表里已去掉「首页」项）
            if (Hawk.get(HawkConfig.HOME_REC, 0) == 1 && absXml != null && absXml.videoList != null && absXml.videoList.size() > 0) {
                fragments.add(UserFragment.newInstance(absXml.videoList));
            } else {
                fragments.add(UserFragment.newInstance(null));
            }
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (SortAdapter.isAppRow(data)) {
                    continue;
                }
                fragments.add(GridFragment.newInstance(data));
            }
            pageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
            try {
                Field field = ViewPager.class.getDeclaredField("mScroller");
                field.setAccessible(true);
                FixedSpeedScroller scroller = new FixedSpeedScroller(mContext, new AccelerateInterpolator());
                field.set(mViewPager, scroller);
                scroller.setmDuration(300);
            } catch (Exception e) {
            }
            mViewPager.setPageTransformer(true, new DefaultTransformer());
            mViewPager.setAdapter(pageAdapter);
            mViewPager.setCurrentItem(currentSelected, false);
            applyCategoryLayout(currentSelected != 0);
        }
    }

    @Override
    public void onBackPressed() {
        //打断加载
        if(isLoading()){
            refreshEmpty();
            return;
        }
        // 如果处于 VOD 删除模式，则退出该模式并刷新界面
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false;
            UserFragment.homeHotVodAdapter.notifyDataSetChanged();
            return;
        }

        // 检查 fragments 状态
        if (this.fragments.size() <= 0 || this.sortFocused >= this.fragments.size() || this.sortFocused < 0) {
            doExit();
            return;
        }

        BaseLazyFragment baseLazyFragment = this.fragments.get(this.sortFocused);
        if (baseLazyFragment instanceof GridFragment) {
            // 底部分类条展开时，返回键先关条
            if (((GridFragment) baseLazyFragment).closeFilterBarIfVisible()) {
                return;
            }
        }
        // 任意页面：连按两次返回键退出 APP
        doExit();
    }

    private void doExit() {
        // 如果两次返回间隔小于 2000 毫秒，则退出应用
        if (System.currentTimeMillis() - mExitTime < 2000) {
            AppManager.getInstance().finishAllActivity();
            EventBus.getDefault().unregister(this);
            ControlManager.get().stopServer();
            finish();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } else {
            // 否则仅提示用户，再按一次退出应用
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, getString(R.string.hm_exit), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // takagen99 : Switch to show / hide source title
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (Hawk.get(HawkConfig.HOME_SHOW_SOURCE, false)) {
            if (home != null && home.getName() != null && !home.getName().isEmpty()) {
                tvName.setText(home.getName());
                tvName.clearAnimation();
            }
        } else {
            tvName.setText(R.string.app_name);
        }

        // 搜索与设置图标固定显示在 wifi 之后的同一栏，不再受按钮位置开关控制
        tvFind.setVisibility(View.VISIBLE);
        tvMenu.setVisibility(View.VISIBLE);
        mHandler.post(mRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.get().getSource("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", (String) event.obj);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                HomeActivity.this.startActivity(newIntent);
            }
        } else if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            if (currentView != null) {
//                showFilterIcon((int) event.obj);
            }
        }
    }

    private void showFilterIcon(int count) {
        boolean activated = count > 0;
        currentView.findViewById(R.id.tvFilter).setVisibility(View.VISIBLE);
        ImageView imgView = currentView.findViewById(R.id.tvFilter);
        imgView.setColorFilter(activated ? this.getThemeColor() : Color.WHITE);
    }

    private final Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                if (sortFocused != currentSelected) {
                    currentSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                    // 顶栏保持显示，避免进入分类页时整页向上跳动
                    changeTop(false);
                    applyCategoryLayout(sortFocused != 0);
                    playPageTransition();
                    // 布局刚切换过宽度会变，等一帧再确认当前页，防止切换动作被重测量吞掉（点前几个导航不生效）
                    final int target = sortFocused;
                    mViewPager.post(() -> {
                        try {
                            if (currentSelected == target && mViewPager.getAdapter() != null
                                    && mViewPager.getCurrentItem() != target) {
                                mViewPager.setCurrentItem(target, false);
                            }
                        } catch (Throwable ignored) {
                            // 保持当前页
                        }
                    });
                }
            }
        }
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (topHide < 0)
            return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                showSiteSwitch();
            }
//            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
//                if () {
//
//                }
//            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {

        }
        return super.dispatchKeyEvent(event);
    }

    byte topHide = 0;

    private void changeTop(boolean hide) {
        ViewObj viewObj = new ViewObj(topLayout, (ViewGroup.MarginLayoutParams) topLayout.getLayoutParams());
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                topHide = (byte) (hide ? 1 : 0);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        // Hide Top =======================================================
        if (hide && topHide == 0) {
            animatorSet.playTogether(ObjectAnimator.ofObject(viewObj, "marginTop", new IntEvaluator(),
                            Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 20.0f)),
                            Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 0.0f))),
                    ObjectAnimator.ofObject(viewObj, "height", new IntEvaluator(),
                            Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 50.0f)),
                            Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 1.0f))),
                    ObjectAnimator.ofFloat(this.topLayout, "alpha", 1.0f, 0.0f));
            animatorSet.setDuration(250);
            animatorSet.start();
            tvName.setFocusable(false);
            btnUiMode.setFocusable(false);
            tvWifi.setFocusable(false);
            tvFind.setFocusable(false);            tvStyle.setFocusable(false);
            tvDraw.setFocusable(false);
            tvMenu.setFocusable(false);
            return;
        }
        // Show Top =======================================================
        if (!hide && topHide == 1) {
            animatorSet.playTogether(ObjectAnimator.ofObject(viewObj, "marginTop", new IntEvaluator(),
                            Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 0.0f)),
                            Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 20.0f))),
                    ObjectAnimator.ofObject(viewObj, "height", new IntEvaluator(),
                            Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 1.0f)),
                            Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 50.0f))),
                    ObjectAnimator.ofFloat(this.topLayout, "alpha", 0.0f, 1.0f));
            animatorSet.setDuration(250);
            animatorSet.start();
            tvName.setFocusable(true);
            btnUiMode.setFocusable(true);
            tvWifi.setFocusable(true);
            tvFind.setFocusable(true);
            tvStyle.setFocusable(true);
            tvDraw.setFocusable(true);
            tvMenu.setFocusable(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        if (CommonSetting.isMobileUi()) {
            // 手机端 UI 下 HomeActivity 只是入口中转：重定向后立即 finish，
            // 若这里照旧 appExit(0) 会把整个进程连 MobileHomeActivity 一起杀掉，
            // 表现就是「点桌面图标一闪退回桌面」。此处直接交棒，不停服务、不杀进程。
            return;
        }
        AppManager.getInstance().appExit(0);
        ControlManager.get().stopServer();
    }

    // Site Switch on Home Button
    void showSiteSwitch() {
        List<SourceBean> sites = new ArrayList<>();
        for (SourceBean sb : ApiConfig.get().getSourceBeanList()) {
            if (sb.getHide() == 0) sites.add(sb);
        }
        if (sites.size() > 0) {
            SelectDialog<SourceBean> dialog = new SelectDialog<>(HomeActivity.this);

            // Multi Column Selection
            int spanCount = (int) Math.floor(sites.size() / 10);
            if (spanCount <= 1) spanCount = 1;
            if (spanCount >= 3) spanCount = 3;

            TvRecyclerView tvRecyclerView = dialog.findViewById(R.id.list);
            tvRecyclerView.setLayoutManager(new V7GridLayoutManager(dialog.getContext(), spanCount));
            ConstraintLayout cl_root = dialog.findViewById(R.id.cl_root);
            ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
            if (spanCount != 1) {
                clp.width = AutoSizeUtils.mm2px(dialog.getContext(), 400 + 260 * (spanCount - 1));
            }

            dialog.setTip(getString(R.string.dia_source));
            dialog.setAdapter(tvRecyclerView, new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
                @Override
                public void click(SourceBean value, int pos) {
                    ApiConfig.get().setSourceBean(value);
                    reloadHome();
                }

                @Override
                public String getDisplay(SourceBean val) {
                    return val.getName();
                }
            }, new DiffUtil.ItemCallback<SourceBean>() {
                @Override
                public boolean areItemsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                    return oldItem == newItem;
                }

                @Override
                public boolean areContentsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                    return oldItem.getKey().equals(newItem.getKey());
                }
            }, sites, sites.indexOf(ApiConfig.get().getHomeSourceBean()));
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
//                    if (homeSourceKey != null && !homeSourceKey.equals(Hawk.get(HawkConfig.HOME_API, ""))) {
//                        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
//                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                        Bundle bundle = new Bundle();
//                        bundle.putBoolean("useCache", true);
//                        intent.putExtras(bundle);
//                        HomeActivity.this.startActivity(intent);
//                    }
                }
            });
            dialog.show();
        }
    }

    void reloadHome() {
        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        HomeActivity.this.startActivity(intent);
    }

    private void refreshEmpty() {
        skipNextUpdate=true;
        showSuccess();
        sortAdapter.setNewData(withoutHomeRow(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true)));
        initViewPager(null);
        tvName.clearAnimation();
    }

    private void tvNameAnimation()
    {
        AlphaAnimation blinkAnimation = new AlphaAnimation(0.0f, 1.0f);
        blinkAnimation.setDuration(500);
        blinkAnimation.setStartOffset(20);
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(Animation.INFINITE);
        tvName.startAnimation(blinkAnimation);
    }
//    public void onClick(View v) {
//        FastClickCheckUtil.check(v);
//        if (v.getId() == R.id.tvFind) {
//            jumpActivity(SearchActivity.class);
//        } else if (v.getId() == R.id.tvMenu) {
//            jumpActivity(SettingActivity.class);
//        }
//    }

}
