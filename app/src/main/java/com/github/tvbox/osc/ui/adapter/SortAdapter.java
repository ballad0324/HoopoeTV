package com.github.tvbox.osc.ui.adapter;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {

    // true 时分类项按左栏竖排样式铺满宽度（分类内容页），false 时保持横排自适应宽度（首页）
    public static boolean verticalMode = false;

    // 「应用」独立行：插在首页与第一个分类之间，整行可点，点开应用列表
    public static final String APP_ROW_ID = "__app__";

    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    public static boolean isAppRow(MovieSort.SortData item) {
        return item != null && APP_ROW_ID.equals(item.id);
    }

    // 左侧分类行右侧的「筛选」小按钮点击回调（点开/关闭底部分类条）
    public interface OnFilterClick {
        void onFilterClick(MovieSort.SortData item);
    }

    public static OnFilterClick filterClick;

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        helper.setText(R.id.tvTitle, item.name);
        View itemView = helper.itemView;
        android.content.Context c = itemView.getContext();
        boolean isApp = isAppRow(item);
        ViewGroup.LayoutParams lp = itemView.getLayoutParams();
        if (lp != null) {
            if (verticalMode) {
                // 左栏竖排：选中框跟着文字大小收紧，不再铺满整列
                // 统一宽度：按最长 6 个汉字的宽度定死，所有菜单块等大
                lp.width = AutoSizeUtils.mm2px(c, 160f);
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                // 相邻菜单之间留出外间距（RecyclerView 的 spacing 在竖排时不生效，改用 margin）
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    int gap = AutoSizeUtils.mm2px(c, 10f);
                    ((ViewGroup.MarginLayoutParams) lp).bottomMargin = gap;
                }
            } else {
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.height = AutoSizeUtils.mm2px(c, 45f);
            }
            itemView.setLayoutParams(lp);
        }
        int padX = AutoSizeUtils.mm2px(c, verticalMode ? 12f : 20f);
        int padY = AutoSizeUtils.mm2px(c, verticalMode ? 8f : 0f);
        if (isApp) {
            // 应用行做成独立圆角块，和其它分类在视觉上分开
            padX = AutoSizeUtils.mm2px(c, 15f);
            padY = AutoSizeUtils.mm2px(c, 6f);
            itemView.setBackgroundResource(R.drawable.shape_user_home);
        } else {
            // 竖排左栏：与「首页」按钮同款（圆角 + 同底色 + 未选中无描边）
            itemView.setBackgroundResource(verticalMode ? R.drawable.shape_left_item : R.drawable.button_home_sort_focus);
        }
        itemView.setPadding(padX, padY, padX, padY);
        if (itemView instanceof android.widget.LinearLayout) {
            ((android.widget.LinearLayout) itemView).setGravity(android.view.Gravity.CENTER);
        }
        helper.getView(R.id.ivAppIcon).setVisibility(isApp ? View.VISIBLE : View.GONE);
        // 左侧竖排时，有筛选条件的分类行右侧显示小按钮，点它展开/关闭底部分类条
        View filterIcon = helper.getView(R.id.tvFilter);
        final boolean hasFilter = item != null && item.filters != null && item.filters.size() > 0;
        filterIcon.setVisibility((verticalMode && hasFilter && !isApp) ? View.VISIBLE : View.GONE);
        if (verticalMode && hasFilter && !isApp) {
            filterIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (SortAdapter.filterClick != null) {
                        SortAdapter.filterClick.onFilterClick(item);
                    }
                }
            });
        }
        TextView title = helper.getView(R.id.tvTitle);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(android.view.Gravity.CENTER);
        if (isApp) {
            title.setTextColor(0xE6FFFFFF);
        } else {
            // 竖排时字体颜色与「首页」按钮一致
            title.setTextColor(verticalMode ? 0xE6FFFFFF : 0xB3FFFFFF);
        }
        // 竖排左栏空间窄，字号降一档，横排保持原来的大字
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                AutoSizeUtils.mm2px(c, verticalMode ? 22f : 30f));
    }
}
