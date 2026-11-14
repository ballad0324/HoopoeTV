package com.github.tvbox.osc.mobile.ui.adapter;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import java.util.List;

/**
 * 手机端首页内容 pager：每个分类一页，页面复用 Box 内核的 GridFragment。
 */
public class MobileHomePagerAdapter extends FragmentStatePagerAdapter {

    private final List<Fragment> fragments;

    public MobileHomePagerAdapter(FragmentManager fm, List<Fragment> fragments) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.fragments = fragments;
    }

    @Override
    public Fragment getItem(int position) {
        return fragments.get(position);
    }

    @Override
    public int getCount() {
        return fragments.size();
    }
}
