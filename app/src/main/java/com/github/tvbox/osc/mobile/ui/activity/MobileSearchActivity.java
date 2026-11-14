package com.github.tvbox.osc.mobile.ui.activity;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.cache.SearchHistory;
import com.github.tvbox.osc.data.SearchPresenter;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.mobile.ui.view.MobileDockHelper;
import com.github.tvbox.osc.mobile.discover.DiscoverItem;
import com.github.tvbox.osc.mobile.discover.MobileDiscoverApi;
import com.github.tvbox.osc.mobile.ui.adapter.MobileRankAdapter;
import com.github.tvbox.osc.mobile.ui.adapter.MobileSearchAdapter;
import com.github.tvbox.osc.mobile.ui.adapter.MobileSearchTagAdapter;
import com.github.tvbox.osc.mobile.ui.adapter.MobileSingleViewAdapter;
import com.github.tvbox.osc.setting.CommonSetting;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 手机端搜索页（沿用旧手机端项目的搜索交互，改竖屏触摸样式）：
 * 顶部输入框 → 空输入时展示「搜索历史 + 热门搜索」，输入中展示「猜你想搜」→
 * 提交后多源并发搜索，结果按来源站点聚合展示，点击进入手机端详情页。
 */
public class MobileSearchActivity extends BaseActivity {

    private EditText etSearch;
    private TextView tvBack;
    private TextView tvSearch;
    private TextView tvClearHistory;
    private TextView tvWordTitle;
    private TextView tvState;
    private RecyclerView rvHistory;
    private RecyclerView rvWord;
    private RecyclerView rvResult;
    private View searchHeaderView;
    private LinearLayout resultBox;
    private ProgressBar pbLoading;
    private LinearLayout llHistoryTitle;
    private HorizontalScrollView svFilterSource;
    private HorizontalScrollView svFilterType;
    private HorizontalScrollView svFilterYear;
    private HorizontalScrollView svFilterArea;
    private LinearLayout llFilterSource;
    private LinearLayout llFilterType;
    private LinearLayout llFilterYear;
    private LinearLayout llFilterArea;
    private RecyclerView rvBrowse;
    private LinearLayout llCatTabs;
    private TextView tvBrowseState;
    private MobileRankAdapter rankAdapter;
    /** 分类页签：名称 → 接口的 t 参数（0 表示不限定） */
    private static final String[] CAT_NAMES = {"全部", "电影", "电视剧", "动漫", "综艺", "纪录片", "短剧"};
    private static final int[] CAT_TYPES = {0, 1, 2, 4, 3, 8, 5};
    private int browseCat = 0;
    /** 浏览列表的请求代号，防止切换分类时旧回调覆盖新数据 */
    private int browseGen = 0;
    /** 整站分类浏览：已加载到第几页、服务端给的总页数与总数 */
    private int browsePage = 0;
    private int browsePageCount = 0;
    private int browseTotal = 0;
    private boolean browseLoading = false;
    private TextView tvBrowseMore;

    /** 本轮搜索的全部原始结果；筛选栏只决定列表显示哪一部分，不清空它 */
    private final List<Movie.Video> allResults = new ArrayList<>();
    /** 当前选中的筛选条件，null 表示「全部」；四维可叠加（交集） */
    private String filterSourceKey = null;
    private String filterType = null;
    private String filterYear = null;
    private String filterArea = null;
    /** chips 的「来源+类型」组合签名，用来判断是否需要重画 chips */
    private String lastChipSignature = "";
    private long lastChipBuildTime = 0;
    /** 一次搜索只允许收尾一次，防 JS 源空结果风暴反复触发全量重建 */
    private boolean finished = false;

    private MobileSearchTagAdapter historyAdapter;
    private MobileSearchTagAdapter wordAdapter;
    private MobileSearchAdapter resultAdapter;

    private SourceViewModel sourceViewModel;
    private final SearchPresenter searchPresenter = new SearchPresenter();

    /** 本次搜索还没回包的源数量；归零即收尾 */
    private final AtomicInteger runCount = new AtomicInteger(0);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String keyword = "";
    private String lastSuggest = "";

    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            // 个别源长时间不回包时兜底收尾，避免一直转圈
            if (runCount.get() > 0) {
                runCount.set(0);
                finishSearch();
            }
        }
    };

    private final Runnable suggestRunnable = new Runnable() {
        @Override
        public void run() {
            loadSuggest(lastSuggest);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_search_mobile;
    }

    /** 手机端按 360dp 设计基准 */
    @Override
    public float getSizeInDp() {
        return 360;
    }

    @Override
    protected void init() {
        MobileDockHelper.bind(this, MobileDockHelper.TAB_SEARCH);
        bindViews();
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        EventBus.getDefault().register(this);
        initLists();
        initEvents();
        loadHistory();
        loadHotWords();
        // 进页面立刻把筛选条渲染出来（来源行用接口里的可搜索站点填充）
        buildChips();
        // 分类页签 + 排行浏览列表（对齐「全部影片」式搜索首页）
        buildCatTabs();

        // 首页「站点推荐」等入口会带 title 直接进来：自动搜这个关键词并出结果，
        // 不再弹键盘等用户二次输入（与 TV 端 SearchActivity 读取 title 的行为对齐）
        String autoTitle = null;
        try {
            autoTitle = getIntent() == null ? null : getIntent().getStringExtra("title");
        } catch (Throwable ignored) {
        }
        if (!TextUtils.isEmpty(autoTitle)) {
            final String autoWord = autoTitle.trim();
            etSearch.setText(autoWord);
            etSearch.setSelection(etSearch.getText().length());
            etSearch.clearFocus();
            etSearch.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startSearch(autoWord);
                }
            }, 150);
            return;
        }


    }

    private void bindViews() {
        etSearch = findViewById(R.id.etSearch);
        tvBack = findViewById(R.id.tvBack);
        tvSearch = findViewById(R.id.tvSearch);
        tvState = findViewById(R.id.tvState);
        rvResult = findViewById(R.id.rvResult);
        resultBox = findViewById(R.id.resultBox);
        pbLoading = findViewById(R.id.pbLoading);
        svFilterSource = findViewById(R.id.svFilterSource);
        svFilterType = findViewById(R.id.svFilterType);
        llFilterSource = findViewById(R.id.llFilterSource);
        llFilterType = findViewById(R.id.llFilterType);
        svFilterYear = findViewById(R.id.svFilterYear);
        svFilterArea = findViewById(R.id.svFilterArea);
        llFilterYear = findViewById(R.id.llFilterYear);
        llFilterArea = findViewById(R.id.llFilterArea);
        rvBrowse = findViewById(R.id.rvBrowse);
        tvBrowseMore = findViewById(R.id.tvBrowseMore);

        // 历史 / 热词 / 分类页签放在 rvBrowse 的列表头里，整页由同一个 RecyclerView 滚动，
        // 不再用 ScrollView 套 RecyclerView（那样续页只会被裁掉，页脚显示 80 部却只画 4 行）
        searchHeaderView = getLayoutInflater().inflate(R.layout.view_search_header, null, false);
        tvClearHistory = searchHeaderView.findViewById(R.id.tvClearHistory);
        tvWordTitle = searchHeaderView.findViewById(R.id.tvWordTitle);
        rvHistory = searchHeaderView.findViewById(R.id.rvHistory);
        rvWord = searchHeaderView.findViewById(R.id.rvWord);
        llHistoryTitle = searchHeaderView.findViewById(R.id.llHistoryTitle);
        llCatTabs = searchHeaderView.findViewById(R.id.llCatTabs);
        tvBrowseState = searchHeaderView.findViewById(R.id.tvBrowseState);
    }
    private void initLists() {
        historyAdapter = new MobileSearchTagAdapter(true);
        historyAdapter.setOnTagClick(new MobileSearchTagAdapter.OnTagClick() {
            @Override
            public void onTagClick(String word) {
                setKeywordAndSearch(word);
            }
        });
        historyAdapter.setOnTagDelete(new MobileSearchTagAdapter.OnTagDelete() {
            @Override
            public void onTagDelete(String word) {
                deleteOneHistory(word);
            }
        });
        rvHistory.setLayoutManager(new GridLayoutManager(this, 3));
        rvHistory.setAdapter(historyAdapter);

        wordAdapter = new MobileSearchTagAdapter();
        wordAdapter.setOnTagClick(new MobileSearchTagAdapter.OnTagClick() {
            @Override
            public void onTagClick(String word) {
                setKeywordAndSearch(word);
            }
        });
        rvWord.setLayoutManager(new GridLayoutManager(this, 3));
        rvWord.setAdapter(wordAdapter);

        resultAdapter = new MobileSearchAdapter();
        resultAdapter.setOnItemClick(new MobileSearchAdapter.OnItemClick() {
            @Override
            public void onItemClick(Movie.Video video) {
                openVideo(video);
            }
        });
        rvResult.setLayoutManager(new LinearLayoutManager(this));
        rvResult.setAdapter(resultAdapter);

        rankAdapter = new MobileRankAdapter();
        rankAdapter.setOnItemClick(new MobileRankAdapter.OnItemClick() {
            @Override
            public void onItemClick(DiscoverItem item) {
                openDiscoverItem(item);
            }
        });
        rvBrowse.setLayoutManager(new LinearLayoutManager(this));
        // 列表头（历史/热词/页签）+ 排行列表共用同一个 RecyclerView
        rvBrowse.setAdapter(new ConcatAdapter(new MobileSingleViewAdapter(searchHeaderView), rankAdapter));

        // 整站分类浏览：滚到底自动续页
        tvBrowseMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadBrowseNext();
            }
        });
        rvBrowse.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int last = lm.findLastVisibleItemPosition();
                int total = recyclerView.getAdapter() == null ? 0 : recyclerView.getAdapter().getItemCount();
                if (total > 0 && last >= total - 4) loadBrowseNext();
            }
        });
    }

    private void initEvents() {
        tvBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSearch(etSearch.getText().toString());
            }
        });

        tvClearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    searchPresenter.clearSearchHistory();
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                loadHistory();
            }
        });

        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                    startSearch(v.getText().toString());
                    return true;
                }
                return false;
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = (s == null) ? "" : s.toString().trim();
                if (TextUtils.isEmpty(text)) {
                    // 输入清空：回到历史 + 热词
                    mainHandler.removeCallbacks(suggestRunnable);
                    showTipsArea();
                    tvWordTitle.setText("热门搜索");
                    loadHotWords();
                    return;
                }
                lastSuggest = text;
                mainHandler.removeCallbacks(suggestRunnable);
                mainHandler.postDelayed(suggestRunnable, 500);
            }
        });
    }

    /* ===================== 历史 / 热词 / 联想 ===================== */

    private void loadHistory() {
        List<String> words = new ArrayList<>();
        try {
            ArrayList<SearchHistory> list = searchPresenter.getSearchHistory();
            if (list != null) {
                for (SearchHistory history : list) {
                    if (history != null && !TextUtils.isEmpty(history.searchKeyWords)) {
                        words.add(history.searchKeyWords);
                    }
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        Collections.reverse(words);
        if (words.size() > 18) words = new ArrayList<>(words.subList(0, 18));
        historyAdapter.setNewData(words);
        boolean empty = words.isEmpty();
        llHistoryTitle.setVisibility(empty ? View.GONE : View.VISIBLE);
        rvHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** 热门搜索：旧的腾讯 hot_search 接口已 404，改用 360kan 榜单（旧手机端项目同款数据源） */
    private void loadHotWords() {
        OkGo.<String>get("https://api.web.360kan.com/v1/rank")
                .params("cat", 1)
                .headers("Referer", "https://www.360kan.com")
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            JsonArray data = JsonParser.parseString(response.body())
                                    .getAsJsonObject()
                                    .get("data").getAsJsonArray();
                            List<String> hots = new ArrayList<>();
                            for (int i = 0; i < data.size() && hots.size() < 9; i++) {
                                JsonElement element = data.get(i);
                                if (element == null || !element.isJsonObject()) continue;
                                JsonObject obj = element.getAsJsonObject();
                                if (!obj.has("title")) continue;
                                String title = obj.get("title").getAsString();
                                if (TextUtils.isEmpty(title)) continue;
                                hots.add(title.trim());
                            }
                            wordAdapter.setNewData(hots);
                            setWordSectionVisible(!hots.isEmpty());
                        } catch (Throwable th) {
                            th.printStackTrace();
                            setWordSectionVisible(false);
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        setWordSectionVisible(false);
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }

    /** 热词/联想没有数据时连标题一起隐藏，避免出现空标题 */
    private void setWordSectionVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        if (tvWordTitle != null) tvWordTitle.setVisibility(v);
        if (rvWord != null) rvWord.setVisibility(v);
    }

    /** 输入联想（「猜你想搜」），接口不可用时静默保持原样 */
    private void loadSuggest(String key) {
        if (TextUtils.isEmpty(key) || TextUtils.equals(key, keyword)) return;
        OkGo.<String>get("https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box")
                .params("format", "json")
                .params("page_num", 0)
                .params("page_size", 30)
                .params("key", key)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            ArrayList<String> hots = new ArrayList<>();
                            JsonObject firstGroup = JsonParser.parseString(response.body())
                                    .getAsJsonObject()
                                    .get("data").getAsJsonObject()
                                    .get("search_data").getAsJsonObject()
                                    .get("vecGroupData").getAsJsonArray()
                                    .get(0).getAsJsonObject();
                            if (!firstGroup.has("group_data") || !firstGroup.get("group_data").isJsonArray()) {
                                return;
                            }
                            JsonArray groupDataArr = firstGroup.get("group_data").getAsJsonArray();
                            for (JsonElement element : groupDataArr) {
                                if (element == null || !element.isJsonObject()) continue;
                                JsonObject groupData = element.getAsJsonObject();
                                if (!groupData.has("dtReportInfo")) continue;
                                JsonObject reportData = groupData.getAsJsonObject("dtReportInfo");
                                if (!reportData.has("reportData")) continue;
                                JsonObject text = reportData.getAsJsonObject("reportData");
                                if (!text.has("keyword_txt")) continue;
                                String word = text.get("keyword_txt").getAsString();
                                if (!TextUtils.isEmpty(word)) hots.add(word.trim());
                            }
                            if (hots.isEmpty()) return;
                            tvWordTitle.setText("猜你想搜");
                            wordAdapter.setNewData(hots);
                            setWordSectionVisible(true);
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }

    private void setKeywordAndSearch(String word) {
        etSearch.setText(word);
        etSearch.setSelection(etSearch.getText().length());
        startSearch(word);
    }

    /* ===================== 搜索 ===================== */

    private void startSearch(String key) {
        if (key == null) key = "";
        key = key.trim();
        if (TextUtils.isEmpty(key)) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show();
            return;
        }
        keyword = key;
        saveHistory(key);
        hideKeyboard();
        etSearch.clearFocus();

        mainHandler.removeCallbacks(timeoutRunnable);
        try {
            sourceViewModel.shutdownNow();
        } catch (Throwable ignored) {
        }

        switchToResult();
        // 新一轮搜索：清空上一轮的汇总结果与筛选条件
        allResults.clear();
        filterSourceKey = null;
        filterType = null;
        filterYear = null;
        filterArea = null;
        lastChipSignature = "";
        resultAdapter.setNewData(null);
        buildChips();
        showLoadingState();

        List<SourceBean> all = null;
        try {
            all = ApiConfig.get().getSourceBeanList();
        } catch (Throwable th) {
            th.printStackTrace();
        }
        if (all == null || all.isEmpty()) {
            showState("还没有可用的影视源，请先到「设置 → 数据源」填接口地址");
            return;
        }

        HashMap<String, String> checked = SearchHelper.getSourcesForSearch();
        ArrayList<String> keys = new ArrayList<>();
        // 聚合搜索关闭时只搜首页当前源，开启时按「搜索源勾选」全量并发
        boolean agg = CommonSetting.isAggSearch();
        SourceBean home = null;
        if (!agg) {
            try {
                home = ApiConfig.get().getHomeSourceBean();
            } catch (Throwable ignored) {
            }
        }
        for (SourceBean bean : all) {
            if (bean == null || TextUtils.isEmpty(bean.getKey()) || !bean.isSearchable()) continue;
            if (!agg) {
                if (home == null || TextUtils.isEmpty(home.getKey())) continue;
                if (!bean.getKey().equals(home.getKey())) continue;
                keys.add(bean.getKey());
                break;
            }
            if (checked != null && !checked.containsKey(bean.getKey())) continue;
            keys.add(bean.getKey());
        }
        if (keys.isEmpty()) {
            showState("没有可用的搜索源，请到「设置 → 数据源」检查接口，或重新勾选搜索源");
            return;
        }

        runCount.set(keys.size());
        finished = false;
        try {
            sourceViewModel.initExecutor();
        } catch (Throwable th) {
            th.printStackTrace();
        }
        for (String key0 : keys) {
            final String sourceKey = key0;
            final String wd = keyword;
            sourceViewModel.execute(new Runnable() {
                @Override
                public void run() {
                    sourceViewModel.getSearch(sourceKey, wd);
                }
            });
        }
        mainHandler.postDelayed(timeoutRunnable, 12000);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSearchResult(RefreshEvent event) {
        if (event == null || event.type != RefreshEvent.TYPE_SEARCH_RESULT) return;
        AbsXml absXml = null;
        if (event.obj != null && event.obj instanceof AbsXml) absXml = (AbsXml) event.obj;
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null
                && !absXml.movie.videoList.isEmpty()) {
            List<Movie.Video> fresh = new ArrayList<>(absXml.movie.videoList);
            allResults.addAll(fresh);
            // 只把符合当前筛选条件的增量结果追加进列表，避免每个源回包都重灌全量
            List<Movie.Video> shown = new ArrayList<>();
            for (Movie.Video v : fresh) {
                if (matchesFilter(v)) shown.add(v);
            }
            if (!shown.isEmpty()) resultAdapter.addData(shown);
            rebuildChipsIfNeeded();
            hideLoading();
        }
        int left = runCount.decrementAndGet();
        if (left <= 0) {
            runCount.set(0);
            // JS 源会连续上报几十条空结果，若不置位保护，每条都会触发一次
            // finishSearch() 的全量重建，主线程直接被压死
            if (!finished) {
                finishSearch();
            }
        }
    }

    private void finishSearch() {
        if (finished) return;
        finished = true;
        mainHandler.removeCallbacks(timeoutRunnable);
        mainHandler.removeCallbacks(rebuildChipsRun);
        if (resultAdapter.getData().isEmpty() && allResults.isEmpty()) {
            showState("没有找到「" + keyword + "」相关内容，换个关键词试试");
        } else {
            rebuildFilters();
            hideLoading();
        }
    }

    /* ===================== 结果筛选（来源 / 类型） ===================== */

    /**
     * 多源聚合搜索的结果可能几十上百条，这里照 TV 端「分类筛选条」的做法给出多维度 chips：
     * 来源 / 类型 / 年份 / 地区，四维可叠加（同时选中则取交集）。
     * 选项来自本轮搜索结果里真实存在的值（每个源都会返回 type/year/area），
     * 某个维度只有一个取值时该行不显示，避免只出现一个「全部」的空行。
     */
    private void buildChips() {
        if (llFilterSource == null || llFilterType == null
                || llFilterYear == null || llFilterArea == null) return;

        LinkedHashMap<String, Integer> sources = new LinkedHashMap<>();
        LinkedHashMap<String, String> sourceKeyBySite = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> types = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> years = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> areas = new LinkedHashMap<>();
        for (Movie.Video v : allResults) {
            if (v == null) continue;
            String site = siteNameOf(v);
            bump(sources, site);
            if (!sourceKeyBySite.containsKey(site)) sourceKeyBySite.put(site, v.sourceKey);
            bump(types, trimToEmpty(v.type));
            bump(years, yearOf(v));
            bump(areas, trimToEmpty(v.area));
        }

        // 未搜索时用接口里可搜索的站点把来源行填满，页面一进来就能看到四行筛选条
        if (allResults.isEmpty()) {
            try {
                List<SourceBean> beans = ApiConfig.get().getSourceBeanList();
                if (beans != null) {
                    for (SourceBean bean : beans) {
                        if (bean == null || TextUtils.isEmpty(bean.getKey())) continue;
                        if (!bean.isSearchable()) continue;
                        String name = TextUtils.isEmpty(bean.getName()) ? bean.getKey() : bean.getName();
                        if (!sources.containsKey(name)) {
                            sources.put(name, 0);
                            sourceKeyBySite.put(name, bean.getKey());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 筛选条常显：四个维度固定在顶部，不随结果多少增删，避免布局跳动
        svFilterSource.setVisibility(View.VISIBLE);
        svFilterType.setVisibility(View.VISIBLE);
        svFilterYear.setVisibility(View.VISIBLE);
        svFilterArea.setVisibility(View.VISIBLE);

        /* ---------- 来源 ---------- */
        llFilterSource.removeAllViews();
        addChip(llFilterSource, allResults.isEmpty() ? "全部来源" : ("全部来源 · " + allResults.size()),
                filterSourceKey == null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        filterSourceKey = null;
                        rebuildFilters();
                    }
                });
        for (Map.Entry<String, Integer> e : sources.entrySet()) {
            final String site = e.getKey();
            String rawKey = sourceKeyBySite.get(site);
            final String key = rawKey == null ? "" : rawKey;
            int count = e.getValue() == null ? 0 : e.getValue();
            addChip(llFilterSource, count > 0 ? (site + " · " + count) : site,
                    key.equals(filterSourceKey == null ? "" : filterSourceKey),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            filterSourceKey = key;
                            rebuildFilters();
                        }
                    });
        }

        /* ---------- 类型 ---------- */
        llFilterType.removeAllViews();
        addChip(llFilterType, "全部类型", filterType == null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filterType = null;
                rebuildFilters();
            }
        });
        for (Map.Entry<String, Integer> e : types.entrySet()) {
            final String type = e.getKey();
            addChip(llFilterType, type + " · " + e.getValue(), type.equals(filterType),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            filterType = type;
                            rebuildFilters();
                        }
                    });
        }

        /* ---------- 年份（新→旧） ---------- */
        llFilterYear.removeAllViews();
        addChip(llFilterYear, "全部年份", filterYear == null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filterYear = null;
                rebuildFilters();
            }
        });
        List<String> yearKeys = new ArrayList<>(years.keySet());
        Collections.sort(yearKeys, Collections.<String>reverseOrder());
        for (final String year : yearKeys) {
            Integer count = years.get(year);
            addChip(llFilterYear, year + " · " + (count == null ? 1 : count), year.equals(filterYear),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            filterYear = year;
                            rebuildFilters();
                        }
                    });
        }

        /* ---------- 地区 ---------- */
        llFilterArea.removeAllViews();
        addChip(llFilterArea, "全部地区", filterArea == null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filterArea = null;
                rebuildFilters();
            }
        });
        for (Map.Entry<String, Integer> e : areas.entrySet()) {
            final String area = e.getKey();
            addChip(llFilterArea, area + " · " + e.getValue(), area.equals(filterArea),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            filterArea = area;
                            rebuildFilters();
                        }
                    });
        }
    }

    /** 计数 +1；空值直接跳过，避免出现「未知 · N」这种噪音 chip */
    private void bump(LinkedHashMap<String, Integer> map, String key) {
        if (TextUtils.isEmpty(key)) return;
        Integer c = map.get(key);
        map.put(key, c == null ? 1 : c + 1);
    }

    private String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /** year 是 int，0 视为没有年份（不参与筛选，避免出现「0 · N」这样的 chip） */
    private String yearOf(Movie.Video v) {
        return (v == null || v.year <= 0) ? "" : String.valueOf(v.year);
    }

    /** 用户点 chip 或搜索收尾时调用：重画 chips 并按条件全量刷新列表 */
    private void rebuildFilters() {
        buildChips();
        applyFilter();
    }

    /**
     * 搜索过程中每个源都会回一次包。若每次回包都重画 chips + 重灌整个列表，
     * 几十个源就会把主线程压垮（实测会被系统 Force finishing）。
     * 这里只在「来源/类型集合」真的变化时才重画 chips，列表用增量追加。
     */
    private void rebuildChipsIfNeeded() {
        String sig = signatureOf();
        if (sig.equals(lastChipSignature)) return;
        long now = System.currentTimeMillis();
        // 多源回包很密集，chips 重建限制在 500ms 一次，避免主线程被连续绘制压住
        if (now - lastChipBuildTime < 500) {
            mainHandler.removeCallbacks(rebuildChipsRun);
            mainHandler.postDelayed(rebuildChipsRun, 500);
            return;
        }
        lastChipSignature = sig;
        lastChipBuildTime = now;
        buildChips();
    }

    private final Runnable rebuildChipsRun = new Runnable() {
        @Override
        public void run() {
            rebuildChipsIfNeeded();
        }
    };

    private String signatureOf() {
        List<String> parts = new ArrayList<>();
        for (Movie.Video v : allResults) {
            if (v == null) continue;
            String p = siteNameOf(v) + "#" + trimToEmpty(v.type)
                    + "#" + yearOf(v) + "#" + trimToEmpty(v.area);
            if (!parts.contains(p)) parts.add(p);
        }
        Collections.sort(parts);
        return parts.toString();
    }

    private boolean matchesFilter(Movie.Video v) {
        if (v == null) return false;
        if (filterSourceKey != null
                && !filterSourceKey.equals(v.sourceKey == null ? "" : v.sourceKey)) return false;
        if (filterType != null && !filterType.equals(trimToEmpty(v.type))) return false;
        if (filterYear != null && !filterYear.equals(yearOf(v))) return false;
        return filterArea == null || filterArea.equals(trimToEmpty(v.area));
    }

    private void addChip(LinearLayout parent, String label, boolean selected,
                         View.OnClickListener listener) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12);
        int h = dp(30);
        chip.setHeight(h);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), 0, dp(12), 0);
        chip.setBackgroundResource(R.drawable.shape_filter_chip);
        chip.setSelected(selected);
        chip.setTextColor(selected ? 0xFFFFFFFF : getResources().getColor(R.color.shell_text_dim));
        chip.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, h);
        lp.setMargins(0, 0, dp(8), 0);
        parent.addView(chip, lp);
    }

    /** 按当前筛选条件过滤 allResults 后灌给列表；筛空时给出可操作的提示 */
    private void applyFilter() {
        List<Movie.Video> out = new ArrayList<>();
        for (Movie.Video v : allResults) {
            if (matchesFilter(v)) out.add(v);
        }
        resultAdapter.setNewData(out);
        if (out.isEmpty()) {
            showState("当前筛选下没有结果，点「全部来源 / 全部类型 / 全部年份 / 全部地区」逐个放宽");
        } else {
            pbLoading.setVisibility(View.GONE);
            tvState.setVisibility(View.GONE);
            rvResult.setVisibility(View.VISIBLE);
        }
    }

    /** chips 上显示的站点名：优先接口里的站点名，查不到就退回 key */
    private String siteNameOf(Movie.Video v) {
        String key = v == null ? null : v.sourceKey;
        if (TextUtils.isEmpty(key)) return "未知来源";
        SourceBean bean = ApiConfig.get().getSource(key);
        if (bean != null && !TextUtils.isEmpty(bean.getName())) return bean.getName();
        return key;
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private void saveHistory(String key) {
        try {
            if (!searchPresenter.keywordsExist(key)) searchPresenter.addKeyWordsTodb(key);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /* ===================== 分类页签 + 排行浏览 ===================== */

    /** 分类页签：单个 TextView + 自绘底部短下划线，避免多层嵌套把文字挤成一字宽 */
    private void buildCatTabs() {
        if (llCatTabs == null) return;
        llCatTabs.removeAllViews();
        int colorOff = getResources().getColor(R.color.shell_text_dim);
        for (int i = 0; i < CAT_NAMES.length; i++) {
            final int idx = i;
            TextView tv = new TextView(this);
            tv.setText(CAT_NAMES[i]);
            tv.setTextSize(14);
            tv.setSingleLine(true);
            tv.setGravity(Gravity.CENTER);
            tv.setIncludeFontPadding(false);
            tv.setPadding(dp(8), dp(6), dp(8), dp(12));
            tv.setTextColor(colorOff);
            tv.setBackground(new TabUnderline(0x00000000, dp(22), dp(2)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(3), 0, dp(3), 0);
            tv.setLayoutParams(lp);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectCat(idx);
                }
            });
            llCatTabs.addView(tv);
        }
        selectCat(browseCat);
    }

    private void selectCat(int idx) {
        browseCat = Math.max(0, Math.min(idx, CAT_NAMES.length - 1));
        if (llCatTabs != null) {
            int colorOn = getResources().getColor(R.color.shell_secondary);
            int colorOff = getResources().getColor(R.color.shell_text_dim);
            int lineOn = getResources().getColor(R.color.shell_secondary);
            for (int i = 0; i < llCatTabs.getChildCount(); i++) {
                View child = llCatTabs.getChildAt(i);
                if (!(child instanceof TextView)) continue;
                TextView tv = (TextView) child;
                boolean on = i == browseCat;
                tv.setTextColor(on ? colorOn : colorOff);
                tv.setTypeface(null, on ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
                tv.setBackground(new TabUnderline(on ? lineOn : 0x00000000, dp(22), dp(2)));
            }
        }
        loadBrowse();
    }

    /** 页签底部那根短横线：在 bounds 底部居中画指定宽高的色块 */
    private class TabUnderline extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint();
        private final int lineW;
        private final int lineH;

        TabUnderline(int color, int w, int h) {
            paint.setColor(color);
            paint.setAntiAlias(true);
            lineW = w;
            lineH = h;
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float left = b.left + (b.width() - lineW) / 2f;
            canvas.drawRect(left, b.bottom - lineH, left + lineW, b.bottom, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
        /** 切分类：清空列表、从第 1 页重建，之后随滚动续页直到服务端 pagecount */
    private void loadBrowse() {
        if (rankAdapter == null) return;
        browseGen++;
        browsePage = 0;
        browsePageCount = 0;
        browseTotal = 0;
        browseLoading = false;
        rankAdapter.setCatLabel(CAT_NAMES[browseCat]);
        rankAdapter.setNewData(null);
        if (tvBrowseState != null) {
            tvBrowseState.setVisibility(View.GONE);
            tvBrowseState.setText("");
        }
        if (tvBrowseMore != null) {
            tvBrowseMore.setVisibility(View.VISIBLE);
            tvBrowseMore.setText("加载中…");
        }
        // 先连拉 2 页垫底，首屏不至于只有 20 条
        loadBrowseNext();
        loadBrowseNext();
    }

    /** 整站分类浏览：按接口分页续拉下一页，走完 pagecount 为止 */
    private void loadBrowseNext() {
        if (rankAdapter == null || browseLoading) return;
        if (browsePageCount > 0 && browsePage >= browsePageCount) {
            updateBrowseFooter();
            return;
        }
        browseLoading = true;
        final int gen = browseGen;
        final int type = CAT_TYPES[browseCat];
        final int nextPage = browsePage + 1;
        if (tvBrowseMore != null && rankAdapter.getDataSize() > 0) tvBrowseMore.setText("加载更多…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final MobileDiscoverApi.Paged paged = MobileDiscoverApi.fetchWithMeta(type, nextPage, null);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != browseGen || rankAdapter == null) return;
                        browseLoading = false;
                        if (paged == null) {
                            updateBrowseFooter();
                            return;
                        }
                        if (paged.pageCount > 0) browsePageCount = paged.pageCount;
                        if (paged.total > 0) browseTotal = paged.total;
                        if (paged.items != null && !paged.items.isEmpty()) {
                            browsePage = nextPage;
                            rankAdapter.addData(paged.items);
                        } else if (nextPage == 1) {
                            browsePageCount = browsePageCount > 0 ? browsePageCount : 1;
                        }
                        updateBrowseFooter();
                    }
                });
            }
        }, "search-browse").start();
    }

    /** 底部续页提示：加载中 / 上拉继续 / 已到底 */
    private void updateBrowseFooter() {
        if (tvBrowseMore == null || rankAdapter == null) return;
        int loaded = rankAdapter.getDataSize();
        if (loaded == 0) {
            tvBrowseMore.setVisibility(View.GONE);
            if (tvBrowseState != null) {
                tvBrowseState.setVisibility(View.VISIBLE);
                tvBrowseState.setText("当前分类没有取到影片，换个分类或直接搜索试试");
            }
            return;
        }
        if (tvBrowseState != null && tvBrowseState.getVisibility() == View.VISIBLE) {
            tvBrowseState.setVisibility(View.GONE);
        }
        tvBrowseMore.setVisibility(View.VISIBLE);
        boolean end = browsePageCount > 0 && browsePage >= browsePageCount;
        if (end) {
            tvBrowseMore.setClickable(false);
            tvBrowseMore.setText("已加载全部 " + (browseTotal > 0 ? browseTotal : loaded) + " 部");
        } else {
            tvBrowseMore.setClickable(true);
            tvBrowseMore.setText(browseLoading ? "加载中…" : "上拉继续加载 · 已 " + loaded + " 部");
        }
    }
private void openDiscoverItem(DiscoverItem item) {
        if (item == null || TextUtils.isEmpty(item.id)) {
            Toast.makeText(this, "该条目缺少详情 id，无法打开", Toast.LENGTH_SHORT).show();
            return;
        }
        String key = "";
        try {
            SourceBean sb = ApiConfig.get().getHomeSourceBean();
            if (sb != null) key = sb.getKey();
        } catch (Throwable ignored) {
        }
        Bundle bundle = new Bundle();
        bundle.putString("id", item.id);
        bundle.putString("sourceKey", key);
        bundle.putString("title", item.name);
        jumpActivity(MobileDetailActivity.class, bundle);
    }

    /** 删掉一条搜索历史并回写数据库（库里按插入顺序存，重写时保持倒序） */
    private void deleteOneHistory(String word) {
        try {
            historyAdapter.removeWord(word);
            searchPresenter.clearSearchHistory();
            List<String> left = historyAdapter.getData();
            for (int i = left.size() - 1; i >= 0; i--) searchPresenter.addKeyWordsTodb(left.get(i));
        } catch (Throwable th) {
            th.printStackTrace();
        }
        boolean empty = historyAdapter.getData().isEmpty();
        llHistoryTitle.setVisibility(empty ? View.GONE : View.VISIBLE);
        rvHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /* ===================== 区域切换 ===================== */

    private void switchToResult() {
        rvBrowse.setVisibility(View.GONE);
        if (tvBrowseMore != null) tvBrowseMore.setVisibility(View.GONE);
        resultBox.setVisibility(View.VISIBLE);
    }

    private void showTipsArea() {
        rvBrowse.setVisibility(View.VISIBLE);
        updateBrowseFooter();
        resultBox.setVisibility(View.GONE);
    }

    private void showLoadingState() {
        resultBox.setVisibility(View.VISIBLE);
        pbLoading.setVisibility(View.VISIBLE);
        tvState.setVisibility(View.GONE);
        rvResult.setVisibility(View.INVISIBLE);
    }

    private void hideLoading() {
        pbLoading.setVisibility(View.GONE);
        tvState.setVisibility(View.GONE);
        rvResult.setVisibility(View.VISIBLE);
    }

    private void showState(String text) {
        pbLoading.setVisibility(View.GONE);
        rvResult.setVisibility(View.INVISIBLE);
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(text);
    }

    /* ===================== 打开详情 ===================== */

    private void openVideo(Movie.Video video) {
        if (video == null) return;
        if (video.tag != null && (video.tag.equals("folder") || video.tag.equals("cover"))) {
            Toast.makeText(this, "该结果是文件夹结构，手机端暂不支持进入", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(video.id)) {
            Toast.makeText(this, "该结果缺少详情 id，无法打开", Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString("id", video.id);
        bundle.putString("sourceKey", video.sourceKey);
        bundle.putString("title", video.name);
        jumpActivity(MobileDetailActivity.class, bundle);
    }

    /* ===================== 键盘 ===================== */

    private void showKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && etSearch != null) {
                imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && etSearch != null) {
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        try {
            EventBus.getDefault().unregister(this);
        } catch (Throwable ignored) {
        }
        try {
            OkGo.getInstance().cancelTag("search");
            if (sourceViewModel != null) {
                sourceViewModel.shutdownNow();
                sourceViewModel.destroyExecutor();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        super.onDestroy();
    }
}
