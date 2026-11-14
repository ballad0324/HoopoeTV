package com.github.tvbox.osc.mobile.ui.activity;

import com.github.tvbox.osc.mobile.ui.drawable.UiSkin;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.mobile.discover.DiscoverItem;
import com.github.tvbox.osc.mobile.discover.MobileDiscoverApi;
import com.github.tvbox.osc.mobile.ui.view.MobileDockHelper;
import com.github.tvbox.osc.util.ImgUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 手机端「发现」页（原底部 dock 第四位的设置入口已移到右上角）：
 * ① 上映表 —— 按年份筛电影，按上映日期分组，每组三列海报；
 * ② 专题   —— 若干合辑，每辑一行海报，可「查看更多」就地展开；
 * ③ 排行榜 —— 分类 + 日榜/周榜/月榜/总榜，名次列表带热度。
 *
 * 数据全部来自当前数据源的 MacCMS 兼容接口（ac=videolist），端上现算分组与排序，
 * 不依赖服务端新增接口。点任一张卡进手机端详情页。
 */
public class MobileDiscoverActivity extends BaseActivity {

    private static final int TAB_SHOWTIME = 0;
    private static final int TAB_TOPIC = 1;
    private static final int TAB_RANK = 2;

    private static final int RANK_PERIOD_DAY = 0;
    private static final int RANK_PERIOD_WEEK = 1;
    private static final int RANK_PERIOD_MONTH = 2;
    private static final int RANK_PERIOD_ALL = 3;

    /** 同一份列表短时间重复打开时不再发请求（进程内缓存） */
    private static final Map<String, List<DiscoverItem>> CACHE = new ConcurrentHashMap<>();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    private TextView tabShowtime, tabTopic, tabRank;
    private ScrollView svShowtime, svTopic, svRank;
    private LinearLayout llShowtime, llTopic, llRank, llYearChips, llRankType, llRankPeriod;
    private TextView tvEmpty;

    private int currentTab = TAB_SHOWTIME;
    private String yearSel = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
    private int rankTypeSel = 0;
    private int rankPeriodSel = RANK_PERIOD_DAY;

    private boolean showtimeLoaded = false;
    private boolean topicLoaded = false;
    private boolean rankLoaded = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_discover_mobile;
    }

    @Override
    public float getSizeInDp() {
        return 360;
    }

    @Override
    protected void init() {
        tabShowtime = findViewById(R.id.tabShowtime);
        tabTopic = findViewById(R.id.tabTopic);
        tabRank = findViewById(R.id.tabRank);
        svShowtime = findViewById(R.id.svShowtime);
        svTopic = findViewById(R.id.svTopic);
        svRank = findViewById(R.id.svRank);
        llShowtime = findViewById(R.id.llShowtime);
        llTopic = findViewById(R.id.llTopic);
        llRank = findViewById(R.id.llRank);
        llYearChips = findViewById(R.id.llYearChips);
        llRankType = findViewById(R.id.llRankType);
        llRankPeriod = findViewById(R.id.llRankPeriod);
        tvEmpty = findViewById(R.id.tvDiscoverEmpty);

        com.github.tvbox.osc.mobile.ui.compose.MobileHomeDock.bindDockOnly(this, findViewById(R.id.cvDock), com.github.tvbox.osc.mobile.ui.compose.MobileHomeDock.TAB_DISCOVER);

        tabShowtime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(TAB_SHOWTIME);
            }
        });
        tabTopic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(TAB_TOPIC);
            }
        });
        tabRank.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(TAB_RANK);
            }
        });

        ImageView search = findViewById(R.id.btnDiscoverSearch);
        if (search != null) {
            search.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    jumpActivity(MobileSearchActivity.class);
                }
            });
        }

        buildYearChips();
        buildRankTypeChips();
        buildRankPeriodChips();

        // switchTab 内部会在「未加载过」时触发对应分段的取数，这里不再重复调用
        tvEmpty.setText("加载中…");
        tvEmpty.setVisibility(View.VISIBLE);
        switchTab(TAB_SHOWTIME);
    }

    // ---------------------------------------------------------------- 分段与胶囊

    private void switchTab(int tab) {
        currentTab = tab;
        svShowtime.setVisibility(tab == TAB_SHOWTIME ? View.VISIBLE : View.GONE);
        svTopic.setVisibility(tab == TAB_TOPIC ? View.VISIBLE : View.GONE);
        svRank.setVisibility(tab == TAB_RANK ? View.VISIBLE : View.GONE);

        paintTab(tabShowtime, tab == TAB_SHOWTIME);
        paintTab(tabTopic, tab == TAB_TOPIC);
        paintTab(tabRank, tab == TAB_RANK);

        if (tab == TAB_TOPIC) {
            if (!topicLoaded) loadTopics();
        } else if (tab == TAB_RANK) {
            if (!rankLoaded) loadRank();
        } else if (!showtimeLoaded) {
            loadShowtime();
        }
        updateEmpty();
    }

    private void paintTab(TextView tv, boolean on) {
        if (tv == null) return;
        tv.setTextColor(getResources().getColor(on ? R.color.shell_primary : R.color.shell_text_dim));
        tv.getPaint().setFakeBoldText(on);
        tv.invalidate();
    }

    private void buildYearChips() {
        llYearChips.removeAllViews();
        int y = Calendar.getInstance().get(Calendar.YEAR);
        addChip(llYearChips, String.valueOf(y), true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                yearSel = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
                markChips(llYearChips, v);
                showtimeLoaded = false;
                loadShowtime();
            }
        });
        for (int i = 1; i <= 3; i++) {
            final String yy = String.valueOf(y - i);
            addChip(llYearChips, yy, false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    yearSel = yy;
                    markChips(llYearChips, v);
                    showtimeLoaded = false;
                    loadShowtime();
                }
            });
        }
    }

    private void buildRankTypeChips() {
        llRankType.removeAllViews();
        addRankTypeChip("全部", 0, true);
        addRankTypeChip("电影", MobileDiscoverApi.TYPE_MOVIE, false);
        addRankTypeChip("剧集", MobileDiscoverApi.TYPE_SERIES, false);
        addRankTypeChip("综艺", MobileDiscoverApi.TYPE_SHOW, false);
        addRankTypeChip("动漫", MobileDiscoverApi.TYPE_ANIME, false);
        addRankTypeChip("短剧", MobileDiscoverApi.TYPE_SHORT, false);
        addRankTypeChip("纪录片", MobileDiscoverApi.TYPE_DOC, false);
    }

    private void addRankTypeChip(String label, final int type, boolean on) {
        addChip(llRankType, label, on, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rankTypeSel = type;
                markChips(llRankType, v);
                rankLoaded = false;
                loadRank();
            }
        });
    }

    private void buildRankPeriodChips() {
        llRankPeriod.removeAllViews();
        addRankPeriodChip("日榜", RANK_PERIOD_DAY, true);
        addRankPeriodChip("周榜", RANK_PERIOD_WEEK, false);
        addRankPeriodChip("月榜", RANK_PERIOD_MONTH, false);
        addRankPeriodChip("总榜", RANK_PERIOD_ALL, false);
    }

    private void addRankPeriodChip(String label, final int period, boolean on) {
        addChip(llRankPeriod, label, on, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rankPeriodSel = period;
                markChips(llRankPeriod, v);
                // 排序只在本地做，不需要重新拉数据
                loadRankLocal();
            }
        });
    }

    private TextView addChip(LinearLayout parent, String text, boolean on, View.OnClickListener click) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setPadding(dp(12), dp(6), dp(12), dp(6));
        tv.setBackgroundResource(R.drawable.selector_discover_chip);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(getResources().getColor(on ? R.color.shell_primary : R.color.shell_text_dim));
        tv.setSelected(on);
        tv.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        parent.addView(tv, lp);
        return tv;
    }

    /** 同一组胶囊里只有一个选中 */
    private void markChips(LinearLayout group, View selected) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            boolean on = child == selected;
            child.setSelected(on);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(getResources().getColor(
                        on ? R.color.shell_primary : R.color.shell_text_dim));
            }
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateEmpty() {
        if (tvEmpty == null) return;
        boolean empty = (currentTab == TAB_SHOWTIME && llShowtime.getChildCount() == 0)
                || (currentTab == TAB_TOPIC && llTopic.getChildCount() == 0)
                || (currentTab == TAB_RANK && llRank.getChildCount() == 0);
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty && tvEmpty.getText().length() == 0) tvEmpty.setText("加载中…");
    }

    // ---------------------------------------------------------------- ① 上映表

    private void loadShowtime() {
        final String year = yearSel;
        llShowtime.removeAllViews();
        tvEmpty.setText("加载中…");
        tvEmpty.setVisibility(View.VISIBLE);
        pool.execute(new Runnable() {
            @Override
            public void run() {
                List<DiscoverItem> items = new ArrayList<>();
                for (int page = 1; page <= 5; page++) {
                    // 上映表取全站（不过 t 过滤），这样每天能有剧集/动漫/综艺一起上榜，密度才够
                    List<DiscoverItem> one = cached(0, page, year);
                    if (one.isEmpty()) break;
                    items.addAll(one);
                }
                // 按上映日期分组（同一天的排一起），日期新的在上面
                final LinkedHashMap<String, List<DiscoverItem>> groups = groupByDate(items);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        renderShowtime(groups);
                        showtimeLoaded = true;
                    }
                });
            }
        });
    }

    private LinkedHashMap<String, List<DiscoverItem>> groupByDate(List<DiscoverItem> items) {
        LinkedHashMap<String, List<DiscoverItem>> groups = new LinkedHashMap<>();
        ArrayList<DiscoverItem> dated = new ArrayList<>();
        ArrayList<DiscoverItem> undated = new ArrayList<>();
        for (DiscoverItem it : items) {
            String d = MobileDiscoverApi.pubDate(it);
            if (d.isEmpty()) undated.add(it);
            else dated.add(it);
        }
        Collections.sort(dated, new Comparator<DiscoverItem>() {
            @Override
            public int compare(DiscoverItem a, DiscoverItem b) {
                return MobileDiscoverApi.pubDate(b).compareTo(MobileDiscoverApi.pubDate(a));
            }
        });
        for (DiscoverItem it : dated) {
            String d = MobileDiscoverApi.pubDate(it);
            List<DiscoverItem> bucket = groups.get(d);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groups.put(d, bucket);
            }
            bucket.add(it);
        }
        if (!undated.isEmpty()) groups.put("", undated);
        return groups;
    }

    private void renderShowtime(LinkedHashMap<String, List<DiscoverItem>> groups) {
        llShowtime.removeAllViews();
        if (groups.isEmpty()) {
            tvEmpty.setText("这个年份暂时没有取到片单，换个年份试试");
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -1);
        String yesterday = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(cal.getTime());

        for (Map.Entry<String, List<DiscoverItem>> e : groups.entrySet()) {
            String date = e.getKey();
            List<DiscoverItem> list = e.getValue();
            llShowtime.addView(buildGroupHeader(labelOf(date, today, yesterday), list.size()));
            addThreeColumns(llShowtime, list);
        }
    }

    private String labelOf(String date, String today, String yesterday) {
        if (date == null || date.isEmpty()) return "上映日期未标注";
        if (date.equals(today)) return "今天";
        if (date.equals(yesterday)) return "昨天";
        try {
            String[] p = date.split("-");
            return Integer.parseInt(p[1]) + "月" + Integer.parseInt(p[2]) + "日";
        } catch (Throwable th) {
            return date;
        }
    }

    private View buildGroupHeader(String label, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);

        View bar = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(getResources().getColor(R.color.shell_primary));
        gd.setCornerRadius(dp(2));
        bar.setBackground(gd);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(3), dp(16));
        barLp.rightMargin = dp(8);
        row.addView(bar, barLp);

        TextView tv = new TextView(this);
        tv.setText(label + " [" + count + "部]");
        tv.setTextSize(14);
        tv.setTextColor(getResources().getColor(R.color.shell_text));
        tv.getPaint().setFakeBoldText(true);
        row.addView(tv);
        return row;
    }

    // ---------------------------------------------------------------- ② 专题

    private void loadTopics() {
        llTopic.removeAllViews();
        tvEmpty.setText("加载中…");
        tvEmpty.setVisibility(View.VISIBLE);
        pool.execute(new Runnable() {
            @Override
            public void run() {
                int year = Calendar.getInstance().get(Calendar.YEAR);
                int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
                final List<Object[]> sections = new ArrayList<>();
                // 每项：{标题, 类型id, 年份过滤, 排序方式}
                sections.add(new Object[]{year + "年" + month + "月新片", MobileDiscoverApi.TYPE_MOVIE, String.valueOf(year), 0});
                sections.add(new Object[]{"高分剧集", MobileDiscoverApi.TYPE_SERIES, "", 1});
                sections.add(new Object[]{"热播短剧", MobileDiscoverApi.TYPE_SHORT, "", 0});
                sections.add(new Object[]{"人气动漫", MobileDiscoverApi.TYPE_ANIME, "", 0});
                sections.add(new Object[]{"精品综艺", MobileDiscoverApi.TYPE_SHOW, "", 0});
                sections.add(new Object[]{"纪录片精选", MobileDiscoverApi.TYPE_DOC, "", 0});

                final List<Object[]> done = new ArrayList<>();
                for (Object[] s : sections) {
                    int type = (Integer) s[1];
                    String y = (String) s[2];
                    List<DiscoverItem> pool = new ArrayList<>();
                    for (int page = 1; page <= 3; page++) {
                        List<DiscoverItem> one = cached(type, page, y);
                        if (one.isEmpty()) break;
                        pool.addAll(one);
                    }
                    if ((Integer) s[3] == 1) {
                        Collections.sort(pool, new Comparator<DiscoverItem>() {
                            @Override
                            public int compare(DiscoverItem a, DiscoverItem b) {
                                return Double.compare(b.score, a.score);
                            }
                        });
                    }
                    if (!pool.isEmpty()) done.add(new Object[]{s[0], pool});
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        renderTopics(done);
                        topicLoaded = true;
                    }
                });
            }
        });
    }

    private void renderTopics(List<Object[]> sections) {
        llTopic.removeAllViews();
        if (sections.isEmpty()) {
            tvEmpty.setText("暂时没取到专题内容");
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        for (Object[] s : sections) {
            String title = (String) s[0];
            @SuppressWarnings("unchecked")
            final List<DiscoverItem> items = (List<DiscoverItem>) s[1];

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.topMargin = dp(14);
            hlp.bottomMargin = dp(6);
            header.setLayoutParams(hlp);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(title);
            tvTitle.setTextSize(16);
            tvTitle.setTextColor(getResources().getColor(R.color.shell_primary));
            tvTitle.getPaint().setFakeBoldText(true);
            header.addView(tvTitle, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            final TextView tvMore = new TextView(this);
            tvMore.setText("查看更多 ›");
            tvMore.setTextSize(11);
            tvMore.setTextColor(getResources().getColor(R.color.shell_text_dim));
            tvMore.setPadding(dp(8), dp(4), dp(2), dp(4));
            header.addView(tvMore);
            llTopic.addView(header);

            final LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            llTopic.addView(body);
            addHorizontalRow(body, items, 6);

            if (items.size() <= 6) {
                tvMore.setVisibility(View.GONE);
            } else {
                tvMore.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // 就地展开：把剩下的都按每行 6 个铺出来
                        body.removeAllViews();
                        addPosterRows(body, items, 6);
                        tvMore.setVisibility(View.GONE);
                    }
                });
            }
        }
    }

    /** 多行铺开（每行 perRow 个） */
    private void addPosterRows(LinearLayout parent, List<DiscoverItem> items, int perRow) {
        for (int i = 0; i < items.size(); i += perRow) {
            addHorizontalRow(parent, items.subList(i, Math.min(items.size(), i + perRow)), perRow);
        }
    }

    /** 横向一排（可左右滑），每卡固定宽度 */
    private void addHorizontalRow(LinearLayout parent, List<DiscoverItem> items, int perRow) {
        int total = Math.min(items.size(), perRow);
        if (total == 0) return;
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < total; i++) {
            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(106),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(10);
            wrapper.setLayoutParams(lp);
            wrapper.addView(buildPosterCard(items.get(i)));
            row.addView(wrapper);
        }
        parent.addView(hsv);
    }

    // ---------------------------------------------------------------- ③ 排行榜

    private void loadRank() {
        llRank.removeAllViews();
        tvEmpty.setText("加载中…");
        tvEmpty.setVisibility(View.VISIBLE);
        final int type = rankTypeSel;
        pool.execute(new Runnable() {
            @Override
            public void run() {
                List<DiscoverItem> pool = new ArrayList<>();
                for (int page = 1; page <= 2; page++) {
                    List<DiscoverItem> one = cached(type, page, "");
                    if (one.isEmpty()) break;
                    pool.addAll(one);
                }
                final List<DiscoverItem> fpool = pool;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        rankPool = fpool;
                        rankLoaded = true;
                        loadRankLocal();
                    }
                });
            }
        });
    }

    private List<DiscoverItem> rankPool = new ArrayList<>();

    private void loadRankLocal() {
        if (rankPool.isEmpty()) {
            tvEmpty.setText("没有取到榜单数据，检查数据源是否可用");
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        List<DiscoverItem> sorted = new ArrayList<>(rankPool);
        final int period = rankPeriodSel;
        Collections.sort(sorted, new Comparator<DiscoverItem>() {
            @Override
            public int compare(DiscoverItem a, DiscoverItem b) {
                return Long.compare(b.hitsOf(period), a.hitsOf(period));
            }
        });
        llRank.removeAllViews();
        tvEmpty.setVisibility(View.GONE);
        int shown = Math.min(sorted.size(), 20);
        for (int i = 0; i < shown; i++) {
            llRank.addView(buildRankRow(i + 1, sorted.get(i), period));
        }
    }

    // ---------------------------------------------------------------- 卡片构造

    private View buildPosterCard(final DiscoverItem it) {
        View card = getLayoutInflater().inflate(R.layout.item_discover_poster, null, false);
        ImageView iv = card.findViewById(R.id.ivPoster);
        TextView tvName = card.findViewById(R.id.tvName);
        TextView tvMeta = card.findViewById(R.id.tvMeta);
        TextView tvBadge = card.findViewById(R.id.tvBadge);
        TextView tvScore = card.findViewById(R.id.tvScore);

        tvName.setText(it.name);
        tvMeta.setText(it.meta());
        if (it.remarks != null && !it.remarks.trim().isEmpty()) {
            tvBadge.setText(it.remarks.trim());
            tvBadge.setVisibility(View.VISIBLE);
        }
        if (it.score > 0) {
            tvScore.setText(String.format(Locale.CHINA, "%.1f", it.score));
            tvScore.setVisibility(View.VISIBLE);
        }
        if (it.pic != null && !it.pic.trim().isEmpty()) {
            ImgUtil.load(it.pic.trim(), iv, 8);
        }
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDetail(it);
            }
        });
        return card;
    }

    /** 三列网格：每行最多 3 张，卡宽度平均分配 */
    private void addThreeColumns(LinearLayout parent, List<DiscoverItem> items) {
        for (int i = 0; i < items.size(); i += 3) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(10);
            row.setLayoutParams(rlp);
            for (int c = 0; c < 3; c++) {
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                if (c < 2) lp.rightMargin = dp(8);
                cell.setLayoutParams(lp);
                int idx = i + c;
                if (idx < items.size()) {
                    cell.addView(buildPosterCard(items.get(idx)));
                }
                row.addView(cell);
            }
            parent.addView(row);
        }
    }

    private View buildRankRow(int no, final DiscoverItem it, int period) {
        View row = getLayoutInflater().inflate(R.layout.item_discover_rank, llRank, false);
        UiSkin.row(row);
        TextView tvNo = row.findViewById(R.id.tvRankNo);
        ImageView iv = row.findViewById(R.id.ivRankPoster);
        TextView tvName = row.findViewById(R.id.tvRankName);
        TextView tvTags = row.findViewById(R.id.tvRankTags);
        TextView tvDesc = row.findViewById(R.id.tvRankDesc);
        TextView tvHits = row.findViewById(R.id.tvRankHits);

        tvNo.setText(String.valueOf(no));
        int color;
        if (no == 1) color = getResources().getColor(R.color.shell_gold);
        else if (no == 2) color = getResources().getColor(R.color.shell_secondary);
        else if (no == 3) color = getResources().getColor(R.color.shell_badge_hd);
        else color = getResources().getColor(R.color.shell_text_dim);
        if (no <= 3) {
            // 前三名做成色块徽章，参考图就是这个样子
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(color);
            gd.setCornerRadius(dp(4));
            tvNo.setBackground(gd);
            tvNo.setTextColor(Color.WHITE);
        } else {
            tvNo.setTextColor(color);
        }

        tvName.setText(it.name);
        StringBuilder tags = new StringBuilder();
        if (it.cls != null && !it.cls.trim().isEmpty()) tags.append(it.cls.trim());
        if (!it.meta().isEmpty()) {
            if (tags.length() > 0) tags.append(" · ");
            tags.append(it.meta());
        }
        if (it.score > 0) {
            if (tags.length() > 0) tags.append(" · ");
            tags.append(String.format(Locale.CHINA, "评分 %.1f", it.score));
        }
        tvTags.setText(tags.toString());

        String desc = it.blurb != null && !it.blurb.trim().isEmpty() ? it.blurb.trim() : it.content;
        tvDesc.setText(desc == null ? "" : desc.trim());

        long h = it.hitsOf(period);
        tvHits.setText(formatHits(h) + " 热度");

        if (it.pic != null && !it.pic.trim().isEmpty()) {
            ImgUtil.load(it.pic.trim(), iv, 6);
        }
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDetail(it);
            }
        });
        return row;
    }

    private String formatHits(long h) {
        if (h >= 10000) return String.format(Locale.CHINA, "%.1f万", h / 10000.0);
        return String.valueOf(h);
    }

    // ---------------------------------------------------------------- 取数与跳转

    private List<DiscoverItem> cached(int type, int page, String year) {
        String key = type + "|" + page + "|" + (year == null ? "" : year);
        List<DiscoverItem> hit = CACHE.get(key);
        if (hit != null) return hit;
        List<DiscoverItem> one = MobileDiscoverApi.fetch(type, page, year);
        if (!one.isEmpty()) CACHE.put(key, one);
        return one;
    }

    private String currentSourceKey() {
        try {
            if (ApiConfig.get().getHomeSourceBean() != null) {
                String k = ApiConfig.get().getHomeSourceBean().getKey();
                if (k != null) return k;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private void openDetail(DiscoverItem it) {
        Bundle bundle = new Bundle();
        bundle.putString("id", it.id);
        bundle.putString("sourceKey", currentSourceKey());
        bundle.putString("title", it.name);
        jumpActivity(MobileDetailActivity.class, bundle);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            pool.shutdownNow();
        } catch (Throwable ignored) {
        }
    }
}
