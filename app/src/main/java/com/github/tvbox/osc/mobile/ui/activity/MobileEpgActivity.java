package com.github.tvbox.osc.mobile.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.mobile.live.EpgChannelAdapter;
import com.github.tvbox.osc.mobile.live.EpgProgramAdapter;
import com.github.tvbox.osc.mobile.live.LiveCatchup;
import com.github.tvbox.osc.mobile.live.MobileEpgXml;
import com.github.tvbox.osc.mobile.ui.view.MobileDockHelper;
import com.github.tvbox.osc.setting.CommonSetting;
import com.github.tvbox.osc.util.live.TxtSubscribe;
import com.google.gson.JsonArray;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 三栏节目单页：
 *   左列「节目源」= 直播分组（央视/卫视/体育…）
 *   中列「频道」  = 当前分组的频道，左侧色条蓝=可回看、灰=不可回看
 *   右列「节目单」= 当前频道的时间轴，每行右侧状态标签按颜色区分能否回放
 *
 * 点「回看」标签的节目 → 直接进横屏播放页播这一档（直链模式）；
 * 点「直播中」 → 播该频道直播流。
 */
public class MobileEpgActivity extends BaseActivity {

    private LinearLayout llSource;
    private RecyclerView rvChannel;
    private RecyclerView rvProgram;
    private ProgressBar pbEpg;
    private TextView tvEpgEmpty;
    private TextView tvEpgChannelTitle;
    private TextView tvEpgHint;
    private TextView tvSubTitle;

    private final List<LiveChannelGroup> groups = new ArrayList<>();
    private EpgChannelAdapter channelAdapter;
    private EpgProgramAdapter programAdapter;

    private int currentGroup = 0;
    private LiveChannelItem currentChannel = null;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_epg_mobile;
    }

    @Override
    public float getSizeInDp() {
        return 360;
    }

    @Override
    protected void init() {
        com.github.tvbox.osc.mobile.ui.compose.MobileHomeDock.bindDockOnly(this, findViewById(R.id.cvDock), com.github.tvbox.osc.mobile.ui.compose.MobileHomeDock.TAB_LIVE);
        llSource = findViewById(R.id.llSource);
        rvChannel = findViewById(R.id.rvEpgChannel);
        rvProgram = findViewById(R.id.rvEpgProgram);
        pbEpg = findViewById(R.id.pbEpg);
        tvEpgEmpty = findViewById(R.id.tvEpgEmpty);
        tvEpgChannelTitle = findViewById(R.id.tvEpgChannelTitle);
        tvEpgHint = findViewById(R.id.tvEpgHint);
        tvSubTitle = findViewById(R.id.tvSubTitle);
        findViewById(R.id.tvBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        channelAdapter = new EpgChannelAdapter();
        channelAdapter.setChecker(new EpgChannelAdapter.CatchupChecker() {
            @Override
            public boolean canCatchup(LiveChannelItem item) {
                // 注意：这里必须显式限定外部类，否则会递归调用自己导致 StackOverflow（v1.3.8 真机崩溃点）
                return MobileEpgActivity.this.isCatchupable(item);
            }
        });
        channelAdapter.setOnChannelClick(new EpgChannelAdapter.OnChannelClick() {
            @Override
            public void onChannelClick(int position, LiveChannelItem item) {
                channelAdapter.setSelected(position);
                loadPrograms(item);
            }
        });
        rvChannel.setLayoutManager(new LinearLayoutManager(this));
        rvChannel.setAdapter(channelAdapter);

        programAdapter = new EpgProgramAdapter();
        programAdapter.setOnProgramClick(new EpgProgramAdapter.OnProgramClick() {
            @Override
            public void onProgramClick(MobileEpgXml.Program program, boolean live, boolean canCatchup) {
                onProgramTap(program, live, canCatchup);
            }
        });
        rvProgram.setLayoutManager(new LinearLayoutManager(this));
        rvProgram.setAdapter(programAdapter);

        tvEpgEmpty.setText("左侧选频道 → 这里出节目单");
        tvEpgEmpty.setVisibility(View.VISIBLE);

        loadGroups();
    }

    // ------------------------------------------------------------ 左列：节目源

    private void loadGroups() {
        List<LiveChannelGroup> list = ApiConfig.get().getChannelGroupList();
        boolean cacheIsProxy = list != null && list.size() == 1
                && list.get(0).getGroupName() != null
                && list.get(0).getGroupName().startsWith("http://127.0.0.1");
        if (list == null || list.isEmpty() || cacheIsProxy) {
            String current = null;
            try {
                current = CommonSetting.getLiveUrl();
            } catch (Throwable ignored) {
            }
            if (!TextUtils.isEmpty(current)) {
                loadProxyLives(proxyUrlOf(current));
                return;
            }
        }
        if (list == null || list.isEmpty()) {
            Toast.makeText(this, "没有可用的直播源", Toast.LENGTH_SHORT).show();
            return;
        }
        groups.clear();
        groups.addAll(list);
        buildSources();
    }

    private String proxyUrlOf(String liveUrl) {
        try {
            String enc = Base64.encodeToString(liveUrl.getBytes("UTF-8"),
                    Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
            return "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + enc;
        } catch (Throwable th) {
            return liveUrl;
        }
    }

    private void loadProxyLives(String url) {
        try {
            Uri parsedUrl = Uri.parse(url);
            url = new String(Base64.decode(parsedUrl.getQueryParameter("ext"),
                    Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
        } catch (Throwable th) {
            Toast.makeText(this, "直播源解析失败", Toast.LENGTH_SHORT).show();
            return;
        }
        OkGo.<String>get(url).execute(new AbsCallback<String>() {
            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body().string();
            }

            @Override
            public void onSuccess(Response<String> response) {
                LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap = new LinkedHashMap<>();
                TxtSubscribe.parse(linkedHashMap, response.body());
                JsonArray livesArray = TxtSubscribe.live2JsonArray(linkedHashMap);
                ApiConfig.get().loadLives(livesArray);
                List<LiveChannelGroup> loaded = ApiConfig.get().getChannelGroupList();
                if (loaded == null || loaded.isEmpty()) {
                    Toast.makeText(MobileEpgActivity.this, "直播频道为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                groups.clear();
                groups.addAll(loaded);
                buildSources();
            }
        });
    }

    private void buildSources() {
        llSource.removeAllViews();
        for (int i = 0; i < groups.size(); i++) {
            final int index = i;
            TextView chip = new TextView(this);
            String name = groups.get(i).getGroupName();
            chip.setText(name == null ? "" : name);
            chip.setTextSize(11);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(6), 0, dp(4), 0);
            chip.setHeight(dp(32));
            chip.setBackgroundResource(R.drawable.shape_filter_chip);
            boolean on = i == currentGroup;
            chip.setSelected(on);
            chip.setTextColor(getResources().getColor(
                    on ? R.color.shell_text : R.color.shell_text_dim));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentGroup == index) return;
                    currentGroup = index;
                    buildSources();
                    buildChannels();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(32));
            lp.setMargins(0, 0, 0, dp(6));
            llSource.addView(chip, lp);
        }
        buildChannels();
    }

    // ------------------------------------------------------------ 中列：频道

    private void buildChannels() {
        if (currentGroup < 0 || currentGroup >= groups.size()) return;
        ArrayList<LiveChannelItem> channels = groups.get(currentGroup).getLiveChannels();
        if (channels == null || channels.isEmpty()) {
            channelAdapter.setData(null, -1);
            Toast.makeText(this, "该分类没有频道", Toast.LENGTH_SHORT).show();
            return;
        }
        channelAdapter.setData(channels, 0);
        loadPrograms(channels.get(0));
    }

    private LiveChannelItem findCatchupTwin(LiveChannelItem item) {
        if (item == null || TextUtils.isEmpty(item.getChannelName())) return null;
        for (LiveChannelGroup g : groups) {
            ArrayList<LiveChannelItem> list = g.getLiveChannels();
            if (list == null) continue;
            for (LiveChannelItem it : list) {
                if (it == item) continue;
                if (item.getChannelName().equals(it.getChannelName()) && it.hasCatchup()) return it;
            }
        }
        return null;
    }

    /** 这条源能不能回看：自身有 catchup 模板，或别的分组里有同名可回看源；且 EPG 没确认它没节目单 */
    private boolean isCatchupable(LiveChannelItem item) {
        if (item == null) return false;
        if (MobileEpgXml.isKnownNoEpg(item.getChannelName())) return false;
        return item.hasCatchup() || findCatchupTwin(item) != null;
    }

    // ------------------------------------------------------------ 右列：节目单

    private void loadPrograms(final LiveChannelItem item) {
        currentChannel = item;
        tvSubTitle.setText(item.getChannelName() == null ? "" : item.getChannelName());
        tvEpgChannelTitle.setText(item.getChannelName() == null ? "" : item.getChannelName());
        pbEpg.setVisibility(View.VISIBLE);
        tvEpgEmpty.setVisibility(View.GONE);
        programAdapter.setCanCatchup(isCatchupable(item));
        programAdapter.setData(null);

        MobileEpgXml.load(item.getChannelName(), new MobileEpgXml.Callback() {
            @Override
            public void onLoaded(List<MobileEpgXml.Program> programs) {
                if (isFinishing()) return;
                pbEpg.setVisibility(View.GONE);
                if (programs == null || programs.isEmpty()) {
                    programAdapter.setData(null);
                    tvEpgEmpty.setText("EPG 里没有这个频道的节目单，无法回看\n可以换一个蓝色标记的频道");
                    tvEpgEmpty.setVisibility(View.VISIBLE);
                    tvEpgHint.setText("该频道无可回看节目");
                    return;
                }
                int canCount = 0;
                long now = System.currentTimeMillis();
                for (MobileEpgXml.Program p : programs) {
                    if (isCatchupable(item) && p.start.getTime() <= now) canCount++;
                }
                tvEpgHint.setText("共 " + programs.size() + " 档 · 可回看 " + canCount + " 档");
                tvEpgEmpty.setVisibility(View.GONE);
                programAdapter.setData(programs);
                scrollToCurrent(programs);
            }

            @Override
            public void onFail(String message) {
                if (isFinishing()) return;
                pbEpg.setVisibility(View.GONE);
                tvEpgEmpty.setText(message == null ? "节目单加载失败" : message);
                tvEpgEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void scrollToCurrent(List<MobileEpgXml.Program> programs) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < programs.size(); i++) {
            MobileEpgXml.Program p = programs.get(i);
            if (p.start.getTime() <= now && p.stop.getTime() > now) {
                rvProgram.scrollToPosition(i);
                return;
            }
        }
    }

    private void onProgramTap(MobileEpgXml.Program program, boolean live, boolean canCatchup) {
        if (currentChannel == null) return;
        if (live) {
            openPlayer(currentChannel.getUrl(), currentChannel.getChannelName() + " · 直播");
            return;
        }
        if (!canCatchup) {
            Toast.makeText(this, "这一档不可回看（未开始，或该频道不支持回看）",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        LiveChannelItem source = currentChannel;
        if (!source.hasCatchup()) {
            LiveChannelItem twin = findCatchupTwin(source);
            if (twin == null) {
                Toast.makeText(this, "该频道不支持回看", Toast.LENGTH_SHORT).show();
                return;
            }
            source = twin;
        }
        String url = LiveCatchup.build(source.getUrl(), source.getCatchupSource(),
                program.start, program.stop);
        if (url == null) {
            Toast.makeText(this, "回看地址构造失败", Toast.LENGTH_SHORT).show();
            return;
        }
        openPlayer(url, currentChannel.getChannelName() + " · 回看");
    }

    private void openPlayer(String url, String title) {
        Intent intent = new Intent(this, MobileLivePlayerActivity.class);
        intent.putExtra("directUrl", url);
        intent.putExtra("directTitle", title);
        startActivity(intent);
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }
}
