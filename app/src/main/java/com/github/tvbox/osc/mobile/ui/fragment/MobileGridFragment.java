package com.github.tvbox.osc.mobile.ui.fragment;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.mobile.ui.activity.MobileDetailActivity;
import com.github.tvbox.osc.mobile.ui.activity.MobileSearchActivity;
import com.github.tvbox.osc.mobile.ui.adapter.MobileVodAdapter;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

import java.util.ArrayList;
import java.util.Map;

/**
 * 手机端分类内容页。
 *
 * 数据层与 TV 的 GridFragment 完全一致（同一个 SourceViewModel.getList / AbsXml 回调 / 翻页规则），
 * 差别只在呈现：3 列触摸网格 + 自己的卡片布局，不再用 TvRecyclerView 的焦点选中。
 * 筛选条（类型/地区/排序/年代）暂未在手机端实现，见进度文档 Phase4 待办。
 */
public class MobileGridFragment extends BaseLazyFragment {

    /** 列表首项的绑定回调：宿主（首页）拿到头部 View 后往里面填数据和挂点击 */
    public interface HeaderBinder {
        void bindHeader(View headerView);
    }

    private RecyclerView rvVod;
    private ProgressBar pbLoading;
    private TextView tvEmpty;
    /** 顶部左侧：当前分类名 */
    private TextView tvCatName;
    /** 顶部右侧：筛选胶囊开关（轨道 + 圆钮 + 文案） */
    private LinearLayout llFilterSwitch;
    private FrameLayout swTrack;
    private View swThumb;
    private TextView tvFilterLabel;
    private TextView tvSortInfo;
    private LinearLayout llFilterBar;
    /** 筛选开关当前是否打开（默认关闭） */
    private boolean filterSwitchOn = false;

    private MobileVodAdapter adapter;
    private SourceViewModel sourceViewModel;

    /** 首页专用：列表首项（收藏/历史/小编推荐标题），随列表一起滚动 */
    private HeaderBinder headerBinder;
    private View headerView;

    private MovieSort.SortData sortData;
    private int page = 1;
    private int maxPage = 0;
    private boolean loadingMore = false;

    public static MobileGridFragment newInstance(MovieSort.SortData sortData) {
        MobileGridFragment fragment = new MobileGridFragment();
        Bundle args = new Bundle();
        args.putSerializable("sortData", sortData);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_mobile_grid;
    }

    @Override
    public float getSizeInDp() {
        return 360;
    }

    @Override
    protected void init() {
        rvVod = findViewById(R.id.rvVod);
        pbLoading = findViewById(R.id.pbLoading);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvCatName = findViewById(R.id.tvCatName);
        llFilterSwitch = findViewById(R.id.llFilterSwitch);
        swTrack = findViewById(R.id.swTrack);
        swThumb = findViewById(R.id.swThumb);
        tvFilterLabel = findViewById(R.id.tvFilterLabel);
        tvSortInfo = findViewById(R.id.tvSortInfo);
        llFilterBar = findViewById(R.id.llFilterBar);

        if (getArguments() != null) {
            sortData = (MovieSort.SortData) getArguments().getSerializable("sortData");
        }

        // 左上角写当前分类名：首页那页 sortData 为 null，统一显示「首页」
        if (tvCatName != null) {
            String name = (sortData == null || sortData.name == null || sortData.name.isEmpty())
                    ? "首页" : sortData.name;
            tvCatName.setText(name);
        }

        if (llFilterSwitch != null) {
            llFilterSwitch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    filterSwitchOn = !filterSwitchOn;
                    applyFilterSwitch(filterSwitchOn, true);
                    llFilterBar.setVisibility(filterSwitchOn ? View.VISIBLE : View.GONE);
                }
            });
            applyFilterSwitch(false, false);
        }

        buildFilterBar();

        adapter = new MobileVodAdapter();
        // 2 列：手机端一行两张海报（按用户要求从 3 列改回 2 列）
        GridLayoutManager manager = new GridLayoutManager(mContext, 2);
        rvVod.setLayoutManager(manager);
        rvVod.setAdapter(adapter);

        adapter.setOnItemClick(new MobileVodAdapter.OnItemClick() {
            @Override
            public void onItemClick(Movie.Video video) {
                openVideo(video);
            }
        });
        adapter.setOnItemLongClick(new MobileVodAdapter.OnItemLongClick() {
            @Override
            public void onItemLongClick(Movie.Video video) {
                if (video == null) return;
                Bundle bundle = new Bundle();
                bundle.putString("id", video.id);
                bundle.putString("sourceKey", video.sourceKey);
                bundle.putString("title", video.name);
                // 长按搜索同名资源：必须走手机端搜索页，不能再用 TV 的 SearchActivity
                jumpActivity(MobileSearchActivity.class, bundle);
            }
        });

        rvVod.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@Nullable RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loadingMore || adapter.getData() == null || adapter.getData().isEmpty()) return;
                GridLayoutManager lm = (GridLayoutManager) rvVod.getLayoutManager();
                if (lm == null) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                if (lastVisible >= adapter.getItemCount() - 6) {
                    if (maxPage != 0 && page > maxPage) return;
                    loadMore();
                }
            }
        });

        applyHeader();
        initViewModel();
        loadFirstPage();
    }

    /** 首页调用：给它挂一个列表首项（收藏/历史/小编推荐标题），随列表滚动自然收起 */
    public void setHeaderBinder(HeaderBinder binder) {
        this.headerBinder = binder;
        if (rvVod != null) applyHeader();
    }

    private void applyHeader() {
        if (rvVod == null || adapter == null || headerBinder == null || headerView != null) return;
        // 用 rvVod 作 parent 让首项拿到 RecyclerView.LayoutParams，避免宽度只包住内容
        headerView = LayoutInflater.from(mContext).inflate(R.layout.item_home_header, rvVod, false);
        headerBinder.bindHeader(headerView);
        adapter.setHeaderView(headerView);
        if (rvVod.getLayoutManager() instanceof GridLayoutManager) {
            final GridLayoutManager lm = (GridLayoutManager) rvVod.getLayoutManager();
            lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    // 首项横跨整行，视频卡仍按 2 列
                    return (position == 0 && adapter.hasHeader()) ? lm.getSpanCount() : 1;
                }
            });
        }
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.listResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(@Nullable AbsXml absXml) {
                loadingMore = false;
                if (absXml != null && absXml.movie != null
                        && absXml.movie.videoList != null && !absXml.movie.videoList.isEmpty()) {
                    if (page == 1) {
                        adapter.setNewData(absXml.movie.videoList);
                        // 首页「本周推荐」复用第一页片单：让宿主 Activity 先拿一份
                        if (getActivity() instanceof com.github.tvbox.osc.mobile.ui.activity.MobileHomeActivity) {
                            ((com.github.tvbox.osc.mobile.ui.activity.MobileHomeActivity) getActivity())
                                    .onHomeListLoaded(absXml.movie.videoList);
                        }
                    } else {
                        adapter.addData(absXml.movie.videoList);
                    }
                    showContent();
                    page++;
                    maxPage = absXml.movie.pagecount;
                } else {
                    if (page == 1) {
                        showEmptyState();
                    } else {
                        Toast.makeText(mContext, "没有更多了", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    private void loadFirstPage() {
        if (sortData == null) {
            showEmptyState();
            return;
        }
        if (ApiConfig.get().getHomeSourceBean() == null
                || ApiConfig.get().getHomeSourceBean().getApi() == null) {
            showEmptyState();
            return;
        }
        page = 1;
        maxPage = 0;
        showLoadingState();
        sourceViewModel.getList(sortData, page);
    }

    private void loadMore() {
        if (sortData == null) return;
        loadingMore = true;
        sourceViewModel.getList(sortData, page);
    }

    private void showLoadingState() {
        if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (rvVod != null) rvVod.setVisibility(View.INVISIBLE);
    }

    private void showEmptyState() {
        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
        if (tvEmpty != null) tvEmpty.setVisibility(adapter != null && adapter.hasHeader() ? View.GONE : View.VISIBLE);
        // 有钱包首项（首页收藏/历史）时列表仍要显示，否则整块头部会跟着空态一起消失
        if (rvVod != null) {
            rvVod.setVisibility(adapter != null && adapter.hasHeader() ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void showContent() {
        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (rvVod != null) rvVod.setVisibility(View.VISIBLE);
    }

    private void openVideo(Movie.Video video) {
        if (video == null) return;
        android.util.Log.i("HOOPOE_ROUTE", "openVideo id=" + video.id + " tag=" + video.tag + " name=" + video.name);
        Bundle bundle = new Bundle();
        bundle.putString("id", video.id);
        bundle.putString("sourceKey", video.sourceKey);
        bundle.putString("title", video.name);

        if (video.tag != null && (video.tag.equals("folder") || video.tag.equals("cover"))) {
            // 文件夹式分类在 TV 端是「点进去换一页」，手机端还没做这个交互
            Toast.makeText(mContext, "该分类是文件夹结构，手机端暂不支持进入", Toast.LENGTH_SHORT).show();
            return;
        }
        if (video.id == null || video.id.isEmpty() || video.id.startsWith("msearch:")) {
            // 站点推荐/搜索型条目（id 为 msearch:xxx）在手机端必须进手机端搜索页，
            // 之前这里错用了 TV 的 SearchActivity，点开就是横屏电视搜索界面。
            jumpActivity(MobileSearchActivity.class, bundle);
        } else {
            // 手机端走自己的竖屏详情页（MobileDetailActivity），不再复用 TV 的 DetailActivity
            jumpActivity(MobileDetailActivity.class, bundle);
        }
    }

    /** 首页重建 tab 时用同一个实例复用，避免重复请求 */
    public void reload() {
        adapter.setNewData(null);
        loadFirstPage();
    }

    /* ===================== 筛选条 ===================== */

    /** 筛选胶囊开关的外观：轨道背景 + 圆钮位移 + 文案颜色，明暗主题都走主题色 */
    private void applyFilterSwitch(boolean on, boolean animate) {
        if (swTrack == null || swThumb == null) return;
        swTrack.setBackgroundResource(on
                ? R.drawable.shape_switch_track_on
                : R.drawable.shape_switch_track_off);
        // 轨道 34dp - 圆钮 14dp - 左右各 2dp 内边距
        float onX = dp(18);
        if (animate) {
            swThumb.animate().translationX(on ? onX : 0f).setDuration(160).start();
        } else {
            swThumb.setTranslationX(on ? onX : 0f);
        }
        if (tvFilterLabel != null) {
            tvFilterLabel.setTextColor(on
                    ? com.github.tvbox.osc.util.SkinManager.accent(mContext)
                    : com.github.tvbox.osc.util.SkinManager.text2(mContext));
        }
    }

    private void buildFilterBar() {
        if (llFilterBar == null) return;
        llFilterBar.removeAllViews();
        if (sortData == null || sortData.filters == null || sortData.filters.isEmpty()) {
            if (llFilterSwitch != null) llFilterSwitch.setVisibility(View.GONE);
            llFilterBar.setVisibility(View.GONE);
            filterSwitchOn = false;
            applyFilterSwitch(false, false);
            updateSortInfo();
            return;
        }
        if (llFilterSwitch != null) llFilterSwitch.setVisibility(View.VISIBLE);

        for (MovieSort.SortFilter filter : sortData.filters) {
            LinearLayout row = new LinearLayout(mContext);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(3), dp(6), dp(3));

            TextView label = new TextView(mContext);
            label.setText(filter.name);
            label.setTextSize(11);
            label.setTextColor(com.github.tvbox.osc.util.SkinManager.text2(mContext));
            label.setPadding(0, 0, dp(8), 0);
            row.addView(label);

            HorizontalScrollView scroll = new HorizontalScrollView(mContext);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = new LinearLayout(mContext);
            chips.setOrientation(LinearLayout.HORIZONTAL);

            final ArrayList<String> values = new ArrayList<>(filter.values.keySet());
            final ArrayList<String> keys = new ArrayList<>(filter.values.values());
            for (int i = 0; i < values.size(); i++) {
                final MovieSort.SortFilter currentFilter = filter;
                final String chipKey = keys.get(i);
                final TextView chip = new TextView(mContext);
                chip.setText(values.get(i));
                chip.setTextSize(12);
                chip.setPadding(dp(10), dp(5), dp(10), dp(5));
                chip.setBackgroundResource(R.drawable.shape_filter_chip);
                String selected = sortData.filterSelect.get(filter.key);
                boolean on = selected != null && selected.equals(chipKey);
                chip.setSelected(on);
                chip.setTextColor((on ? com.github.tvbox.osc.util.SkinManager.text1(mContext) : com.github.tvbox.osc.util.SkinManager.text2(mContext)));
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String selected = sortData.filterSelect.get(currentFilter.key);
                        if (selected != null && selected.equals(chipKey)) {
                            sortData.filterSelect.remove(currentFilter.key);
                        } else {
                            sortData.filterSelect.put(currentFilter.key, chipKey);
                        }
                        buildFilterBar();
                        forceReload();
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, dp(6), 0);
                chips.addView(chip, lp);
            }

            scroll.addView(chips);
            row.addView(scroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            llFilterBar.addView(row);
        }
        updateSortInfo();
    }

    private void updateSortInfo() {
        if (tvSortInfo == null) return;
        if (sortData == null || sortData.filterSelect == null || sortData.filterSelect.isEmpty()) {
            tvSortInfo.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (MovieSort.SortFilter filter : sortData.filters) {
            String value = sortData.filterSelect.get(filter.key);
            if (value == null || value.isEmpty()) continue;
            String name = null;
            for (Map.Entry<String, String> entry : filter.values.entrySet()) {
                if (entry.getValue().equals(value)) {
                    name = entry.getKey();
                    break;
                }
            }
            if (name == null) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(name);
        }
        tvSortInfo.setText(sb.toString());
    }

    /** 改筛选条件后从第 1 页重新拉 */
    private void forceReload() {
        if (sortData == null) return;
        page = 1;
        maxPage = 0;
        loadingMore = false;
        adapter.setNewData(null);
        showLoadingState();
        sourceViewModel.getList(sortData, page);
    }

    private int dp(int value) {
        return (int) (mContext.getResources().getDisplayMetrics().density * value + 0.5f);
    }
}
