package com.github.tvbox.osc.mobile.ui.adapter;

import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.mobile.setting.MobileSetting;

/**
 * 手机端分类 tab 适配器（横向 RecyclerView），选中态用 adapter_type 里的 selector_type_tab。
 */
public class MobileTypeAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {

    private int selected = 0;

    public MobileTypeAdapter() {
        super(R.layout.adapter_type);
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        TextView tv = helper.getView(R.id.text);
        tv.setText(item.name == null ? "" : item.name);
        // 选中下划线挂在背景 selector 上，「分类下划线」开关直接切换背景
        tv.setBackgroundResource(MobileSetting.isShowTabIndicator() ? R.drawable.selector_type_tab : 0);
        tv.setSelected(helper.getLayoutPosition() == selected);
    }

    public int getSelected() {
        return selected;
    }

    public void setSelected(int position) {
        int old = selected;
        selected = position;
        if (old >= 0 && old < getItemCount()) notifyItemChanged(old);
        if (position >= 0 && position < getItemCount()) notifyItemChanged(position);
    }
}
