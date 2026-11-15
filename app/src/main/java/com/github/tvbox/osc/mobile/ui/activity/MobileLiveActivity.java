package com.github.tvbox.osc.mobile.ui.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.mobile.live.LiveCatchup;
import com.github.tvbox.osc.mobile.live.LiveSourceChecker;
import com.github.tvbox.osc.mobile.live.LiveReminder;
import com.github.tvbox.osc.mobile.live.MobileEpgXml;
import com.github.tvbox.osc.mobile.live.EpgChannelAdapter;
import com.github.tvbox.osc.mobile.live.EpgProgramAdapter;
import com.github.tvbox.osc.mobile.live.ProgramListAdapter;
import com.github.tvbox.osc.mobile.live.EpgSheetAdapter;
import com.github.tvbox.osc.mobile.ui.adapter.MobileChannelAdapter;
import com.github.tvbox.osc.mobile.ui.view.MobileDockHelper;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.setting.CommonSetting;
import com.github.tvbox.osc.util.live.TxtSubscribe;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonArray;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import xyz.doikki.videoplayer.player.VideoView;

/**
 * 手机端直播页（竖屏）：
 * 顶部常驻播放器（点下方任意频道先在这里播放、可一键全屏、支持节目回看）
 * + 节目单信息条 + 分类横滑 chip（含「收藏」「最近」两个虚拟分组）+ 3 列频道网格。
 *
 * 回看能力来自 m3u 的 catchup 声明 + playback.xml 节目单：
 *   - 单频道没有 catchup 模板时，自动找同名的其它分组源做兜底；
 *   - EPG 里确实没有该频道时，回看按钮置灰，不再白等一次 2.6MB 下载。
 */
import com.github.tvbox.osc.util.SkinManager;

public class MobileLiveActivity extends BaseActivity {

    private RecyclerView rvChannel;
    private ProgressBar pbLoading;
    private TextView tvEmpty;
    private LinearLayout llGroup;
    private ScrollView svGroup;

    private FrameLayout flMiniPlayer;
    private MyVideoView videoViewMini;
    private ProgressBar pbMini;
    private TextView tvMiniTip;
    private TextView tvMiniName;
    private TextView tvMiniCatchup;
    private TextView tvMiniFull;
    private TextView tvMiniEpg;
    private TextView tvMiniFav;

    private final List<LiveChannelGroup> groups = new ArrayList<>();
    private EpgChannelAdapter channelAdapter;
    private EpgProgramAdapter programAdapter;
    private RecyclerView rvEpisode;
    private TextView tvEpgHint;
    private TextView tvEpgEmpty;

    // 节目回看面板：日期 + 半小时时间段
    private LinearLayout llCatchupBody;
    private LinearLayout llCatchupDays;
    private GridLayout glCatchupSlots;
    private TextView tvCatchupTip;
    private android.widget.FrameLayout swCatchupTrack;
    private View swCatchupThumb;
    /** 节目回看面板展开状态，默认关闭（胶囊开关默认关） */
    private boolean catchupOpen = false;
    private int catchupDayOffset = 0;
    private int currentGroup = 0;
    /** 0=正常分类，1=收藏，2=最近看过 */
    private int pseudo = 0;

    private final LivePlayerManager livePlayerManager = new LivePlayerManager();
    /** 顶部播放器当前正在播的频道在当前列表里的下标；-1 表示还没选 */
    private int playingIndex = -1;
    /** 当前正在播的频道对象（收藏/最近分组里也能定位回去） */
    private LiveChannelItem playingItem = null;
    /** 当前播的是回看还是直播 */
    private boolean playingCatchup = false;

    private SharedPreferences prefs;

    /** 节目单底部弹层（A 方案）：BottomSheetDialog + 日期胶囊 + 时间|节目名|回看按钮 */
    private BottomSheetDialog epgSheet;
    private EpgSheetAdapter sheetAdapter;
    private LinearLayout llSheetChips;
    private TextView tvSheetHint;
    private int sheetDayOffset = 0;

    private static final int PSEUDO_NONE = 0;
    private static final int PSEUDO_FAV = 1;
    private static final int PSEUDO_RECENT = 2;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_mobile;
    }

    @Override
    public float getSizeInDp() {
        return 360;
    }

    @Override
    protected void init() {
        prefs = getSharedPreferences("hoopoe_live", MODE_PRIVATE);
        com.github.tvbox.osc.mobile.ui.compose.MobileHomeDock.bindDockOnly(this, findViewById(R.id.cvDock), com.github.tvbox.osc.mobile.ui.compose.MobileHomeDock.TAB_LIVE);
        findViewById(R.id.tvBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        rvChannel = findViewById(R.id.rvChannel);
        pbLoading = findViewById(R.id.pbLoading);
        tvEmpty = findViewById(R.id.tvEmpty);
        llGroup = findViewById(R.id.llGroup);
        svGroup = findViewById(R.id.svGroup);
        rvEpisode = findViewById(R.id.rvEpisode);
        tvEpgHint = findViewById(R.id.tvEpgHint);
        tvEpgEmpty = findViewById(R.id.tvEpgEmpty);

        llCatchupBody = findViewById(R.id.llCatchupBody);
        llCatchupDays = findViewById(R.id.llCatchupDays);
        glCatchupSlots = findViewById(R.id.glCatchupSlots);
        tvCatchupTip = findViewById(R.id.tvCatchupTip);
        swCatchupTrack = findViewById(R.id.swCatchupTrack);
        swCatchupThumb = findViewById(R.id.swCatchupThumb);
        // 默认收起：胶囊开关关闭、面板隐藏
        catchupOpen = false;
        llCatchupBody.setVisibility(View.GONE);
        applyCatchupSwitch(false, false);
        findViewById(R.id.llCatchupHead).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                catchupOpen = !catchupOpen;
                llCatchupBody.setVisibility(catchupOpen ? View.VISIBLE : View.GONE);
                applyCatchupSwitch(catchupOpen, true);
            }
        });
        buildCatchupDays();
        buildCatchupSlots();
        updateCatchupTip();

        flMiniPlayer = findViewById(R.id.flMiniPlayer);
        videoViewMini = findViewById(R.id.videoViewMini);
        pbMini = findViewById(R.id.pbMini);
        tvMiniTip = findViewById(R.id.tvMiniTip);
        tvMiniName = findViewById(R.id.tvMiniName);
        tvMiniCatchup = findViewById(R.id.tvMiniCatchup);
        tvMiniFull = findViewById(R.id.tvMiniFull);
        tvMiniEpg = findViewById(R.id.tvMiniEpg);
        tvMiniFav = findViewById(R.id.tvMiniFav);

        // 顶部播放器四角改成 5% 圆角（按宽度算），靠 outline 裁剪，TextureView 才能被裁
        if (flMiniPlayer != null) {
            flMiniPlayer.setClipToOutline(true);
            flMiniPlayer.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    int w = view.getWidth();
                    int h = view.getHeight();
                    if (w <= 0 || h <= 0) return;
                    float r = w * 0.05f;
                    outline.setRoundRect(0, 0, w, h, r);
                }
            });
        }

        setupMiniPlayer();

        // 中列频道列表：与节目单页共用同一个适配器（左色条 = 能否回看）
        channelAdapter = new EpgChannelAdapter();
        channelAdapter.setChecker(new EpgChannelAdapter.CatchupChecker() {
            @Override
            public boolean canCatchup(LiveChannelItem item) {
                return MobileLiveActivity.this.canCatchup(item);
            }
        });
        channelAdapter.setOnChannelClick(new EpgChannelAdapter.OnChannelClick() {
            @Override
            public void onChannelClick(int position, LiveChannelItem item) {
                // 点频道先在顶部播放器里放，右侧同时刷出该频道节目单
                playChannel(position);
            }
        });
        rvChannel.setLayoutManager(new LinearLayoutManager(this));
        rvChannel.setAdapter(channelAdapter);

        // 右列节目单
        programAdapter = new EpgProgramAdapter();
        programAdapter.setOnProgramClick(new EpgProgramAdapter.OnProgramClick() {
            @Override
            public void onProgramClick(MobileEpgXml.Program program, boolean live, boolean canCatchup) {
                onEpgProgramTap(program, live, canCatchup);
            }
        });
        rvEpisode.setLayoutManager(new LinearLayoutManager(this));
        rvEpisode.setAdapter(programAdapter);

        loadChannels();
        // 进直播页就先把 EPG（2.6MB）在后台装进内存/写盘缓存，第一次点「回看」不再等下载
        MobileEpgXml.prefetch();
    }

    /** 回看面板的胶囊开关：on/off 换轨道背景，圆钮左右滑动 */
    private void applyCatchupSwitch(boolean on, boolean animate) {
        if (swCatchupTrack == null || swCatchupThumb == null) return;
        swCatchupTrack.setBackgroundResource(on
                ? R.drawable.shape_switch_track_on
                : R.drawable.shape_switch_track_off);
        float onX = getResources().getDisplayMetrics().density * 18f;
        if (animate) {
            swCatchupThumb.animate().translationX(on ? onX : 0f).setDuration(160).start();
        } else {
            swCatchupThumb.setTranslationX(on ? onX : 0f);
        }
    }
    // ---------------------------------------------------------------- 顶部播放器

    private void setupMiniPlayer() {
        // 16:9 高度按当前屏宽算，避免不同分辨率下发虚
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int h = (int) ((screenWidth - dp(20)) * 9 / 16f);
        ViewGroup.LayoutParams lp = flMiniPlayer.getLayoutParams();
        lp.height = h;
        flMiniPlayer.setLayoutParams(lp);

        livePlayerManager.init(videoViewMini);
        videoViewMini.setProgressManager(null);
        videoViewMini.setOnStateChangeListener(new VideoView.OnStateChangeListener() {
            @Override
            public void onPlayerStateChanged(int playerState) {
            }

            @Override
            public void onPlayStateChanged(int playState) {
                switch (playState) {
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        pbMini.setVisibility(View.VISIBLE);
                        break;
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PLAYING:
                        pbMini.setVisibility(View.GONE);
                        tvMiniTip.setVisibility(View.GONE);
                        break;
                    case VideoView.STATE_ERROR:
                        pbMini.setVisibility(View.GONE);
                        if (playingCatchup && playingItem != null) {
                            // 回看链接失效时自动回落当前直播流
                            playingCatchup = false;
                            Toast.makeText(MobileLiveActivity.this,
                                    "回看播放失败，已切回直播", Toast.LENGTH_SHORT).show();
                            playUrl(playingItem.getUrl(), playingItem.getChannelName(), false);
                        } else {
                            Toast.makeText(MobileLiveActivity.this,
                                    "该频道暂时无法播放", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    default:
                        break;
                }
            }
        });

        tvMiniFull.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFullScreen();
            }
        });
        tvMiniCatchup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEpgSheet();
            }
        });
        tvMiniEpg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openEpgPage();
            }
        });
        tvMiniFav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFavorite();
            }
        });
        // 点画面本身也能进全屏
        videoViewMini.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFullScreen();
            }
        });
        updateCatchupButton();
        updateFavMark();
    }

    private void playChannel(int position) {
        List<LiveChannelItem> channels = currentList();
        if (channels == null || position < 0 || position >= channels.size()) return;
        LiveChannelItem item = channels.get(position);
        if (TextUtils.isEmpty(item.getUrl())) {
            Toast.makeText(this, "该频道没有可用地址", Toast.LENGTH_SHORT).show();
            return;
        }
        playingIndex = position;
        playingItem = item;
        playingCatchup = false;
        addRecent(item);
        channelAdapter.setData(channels, position);
        playUrl(item.getUrl(), item.getChannelName(), false);
        loadProgramsFor(item);
        buildCatchupSlots();
        updateCatchupTip();
    }

    private void playUrl(String url, String name, boolean catchup) {
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "该频道没有可用地址", Toast.LENGTH_SHORT).show();
            return;
        }
        tvMiniName.setText(name == null ? "" : name);
        tvMiniTip.setVisibility(catchup ? View.GONE : View.VISIBLE);
        if (!catchup) tvMiniTip.setText("加载中…");
        pbMini.setVisibility(View.VISIBLE);
        try {
            livePlayerManager.getLiveChannelPlayer(videoViewMini, name);
        } catch (Throwable ignored) {
        }
        videoViewMini.release();
        videoViewMini.setUrl(url, liveHeaders(url));
        videoViewMini.start();
        refreshEpgBar();
        updateCatchupButton();
        updateFavMark();
    }

    private void openFullScreen() {
        if (playingItem == null) {
            Toast.makeText(this, "先点一个频道播放", Toast.LENGTH_SHORT).show();
            return;
        }
        int[] loc = locate(playingItem);
        if (loc == null) {
            Toast.makeText(this, "该频道不在分类列表里，无法全屏", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(MobileLiveActivity.this, MobileLivePlayerActivity.class);
        intent.putExtra("groupIndex", loc[0]);
        intent.putExtra("channelIndex", loc[1]);
        startActivity(intent);
    }

    // ---------------------------------------------------------------- 节目单信息条

    /** 播放器下方一行：正在播的节目 + 下一档；EPG 未加载时提示去点「回看」 */
    private void refreshEpgBar() {
        if (playingItem == null) {
            tvMiniEpg.setText("节目单未加载 · 点「回看」可加载");
            return;
        }
        if (MobileEpgXml.isKnownNoEpg(playingItem.getChannelName())) {
            tvMiniEpg.setText("该频道没有节目单");
            return;
        }
        List<MobileEpgXml.Program> cached = MobileEpgXml.cachedPrograms(playingItem.getChannelName());
        if (cached == null) {
            tvMiniEpg.setText("点「回看」加载节目单");
            return;
        }
        long now = System.currentTimeMillis();
        MobileEpgXml.Program cur = null;
        MobileEpgXml.Program next = null;
        for (MobileEpgXml.Program p : cached) {
            if (cur == null && p.start.getTime() <= now && p.stop.getTime() > now) cur = p;
            if (p.start.getTime() > now && (next == null || p.start.getTime() < next.start.getTime())) {
                next = p;
            }
        }
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.CHINA);
        StringBuilder sb = new StringBuilder();
        if (cur != null) {
            sb.append("正在播 ").append(hm.format(cur.start)).append(" ").append(cur.title);
        } else {
            sb.append("暂无正在播节目");
        }
        if (next != null) {
            sb.append(" ｜ 下一档 ").append(hm.format(next.start)).append(" ").append(next.title);
        }
        tvMiniEpg.setText(sb.toString());
    }

    // ---------------------------------------------------------------- 回看

    /** 回看 / 信息条统一入口：进三栏节目单页（左节目源 / 中频道 / 右节目单） */
    private void openEpgPage() {
        startActivity(new Intent(this, MobileEpgActivity.class));
    }

    // ---------------------------------------------------------------- 节目单底部弹层（A 方案）

    /**
     * A 方案：BottomSheetDialog 底部弹层。顶部拖拽条 + 日期胶囊横滑 + 时间轴列表，
     * 每行「时间 | 节目名 | 回看按钮」，正在播淡蓝底 + 「直播中」，未来节目「提醒」。
     */
    private void showEpgSheet() {
        if (playingItem == null) {
            Toast.makeText(this, "先点一个频道播放", Toast.LENGTH_SHORT).show();
            return;
        }
        LiveChannelItem source = playingItem;
        String note = "";
        boolean canCatchup = source.hasCatchup();
        if (!canCatchup) {
            LiveChannelItem twin = findCatchupTwin(source);
            if (twin != null) {
                source = twin;
                canCatchup = true;
                note = " · 用「" + groupNameOf(twin) + "」组同名源";
            }
        }
        final LiveChannelItem used = source;
        final boolean canCatch = canCatchup;
        final String usedNote = note;

        if (epgSheet != null && epgSheet.isShowing()) epgSheet.dismiss();
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_epg_bottom, null, false);
        TextView tvTitle = content.findViewById(R.id.tvSheetTitle);
        tvSheetHint = content.findViewById(R.id.tvSheetHint);
        llSheetChips = content.findViewById(R.id.llSheetChips);
        final LinearLayout llLoading = content.findViewById(R.id.llSheetLoading);
        final TextView tvLoading = content.findViewById(R.id.tvSheetLoading);
        final RecyclerView rvSheet = content.findViewById(R.id.rvSheetProgram);

        tvTitle.setText("节目单 · " + used.getChannelName() + usedNote);
        tvSheetHint.setText("正在加载…");
        llSheetChips.removeAllViews();

        // 弹层里列表高度占屏幕不足一半，剩下的留给播放器，保证还能看到画面
        rvSheet.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * 0.44f);
        rvSheet.setLayoutManager(new LinearLayoutManager(this));
        sheetAdapter = new EpgSheetAdapter(this);
        rvSheet.setAdapter(sheetAdapter);
        sheetAdapter.setOnActionClick(new EpgSheetAdapter.OnActionClick() {
            @Override
            public void onAction(MobileEpgXml.Program program, int state) {
                switch (state) {
                    case EpgSheetAdapter.STATE_LIVE:
                        playingCatchup = false;
                        playUrl(used.getUrl(), used.getChannelName(), false);
                        Toast.makeText(MobileLiveActivity.this, "已切回直播", Toast.LENGTH_SHORT).show();
                        break;
                    case EpgSheetAdapter.STATE_CATCHUP:
                        startCatchup(used, program, usedNote);
                        break;
                    case EpgSheetAdapter.STATE_FUTURE:
                        showFutureActions(used, program);
                        return;
                    default:
                        return;
                }
                if (epgSheet != null && epgSheet.isShowing()) epgSheet.dismiss();
            }
        });

        content.findViewById(R.id.tvSheetAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (epgSheet != null && epgSheet.isShowing()) epgSheet.dismiss();
                openEpgPage();
            }
        });

        epgSheet = new BottomSheetDialog(this, R.style.Theme_Hoopoe_BottomSheet);
        epgSheet.setContentView(content);
        epgSheet.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                epgSheet = null;
                sheetAdapter = null;
            }
        });
        epgSheet.show();

        llLoading.setVisibility(View.VISIBLE);
        tvLoading.setText("正在加载节目单…");
        MobileEpgXml.load(used.getChannelName(), new MobileEpgXml.Callback() {
            @Override
            public void onLoaded(List<MobileEpgXml.Program> programs) {
                if (isFinishing() || sheetAdapter == null) return;
                llLoading.setVisibility(View.GONE);
                if (programs == null || programs.isEmpty()) {
                    tvSheetHint.setText("EPG 里没有这个频道的节目单，无法回看");
                    llLoading.setVisibility(View.VISIBLE);
                    tvLoading.setText("换个能回看的频道，或在设置里确认 EPG 地址");
                    return;
                }
                sheetPrograms = programs;
                sheetCanCatch = canCatch;
                sheetDayOffset = 0;
                buildSheetChips(programs);
                sheetAdapter.setData(programs, canCatch, 0);
                applySheetHint();
                rvSheet.scrollToPosition(sheetAdapter.getScrollTarget());
            }

            @Override
            public void onFail(String message) {
                if (isFinishing()) return;
                llLoading.setVisibility(View.VISIBLE);
                tvLoading.setText(message);
                tvSheetHint.setText("加载失败");
            }
        });
    }

    private List<MobileEpgXml.Program> sheetPrograms;
    private boolean sheetCanCatch;

    /** 日期胶囊：只显示有节目数据的那几天（今天 / 昨天 / 前天） */
    private void buildSheetChips(List<MobileEpgXml.Program> programs) {
        if (llSheetChips == null) return;
        llSheetChips.removeAllViews();
        String[] labels = {"今天", "昨天", "前天"};
        for (int day = 0; day <= 2; day++) {
            if (!hasProgramOnDay(programs, day) && day != 0) continue;
            addSheetChip(labels[day], day);
        }
    }

    private boolean hasProgramOnDay(List<MobileEpgXml.Program> programs, int day) {
        long start = startOfDayMs(day);
        long end = start + 24L * 60 * 60 * 1000;
        for (MobileEpgXml.Program p : programs) {
            long t = p.start.getTime();
            if (t >= start && t < end) return true;
        }
        return false;
    }

    private long startOfDayMs(int dayOffset) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, -dayOffset);
        return c.getTimeInMillis();
    }

    private void addSheetChip(String label, final int dayOffset) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        chip.setLayoutParams(lp);
        chip.setTag(dayOffset);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheetDayOffset = dayOffset;
                paintSheetChips();
                if (sheetAdapter != null && sheetPrograms != null) {
                    sheetAdapter.setDayOffset(dayOffset);
                    RecyclerView rv = epgSheet == null ? null : epgSheet.findViewById(R.id.rvSheetProgram);
                    if (rv != null) rv.scrollToPosition(0);
                }
                applySheetHint();
            }
        });
        llSheetChips.addView(chip);
        paintSheetChips();
    }

    private void paintSheetChips() {
        if (llSheetChips == null) return;
        int primary = com.github.tvbox.osc.util.SkinManager.accent(this);
        for (int i = 0; i < llSheetChips.getChildCount(); i++) {
            View child = llSheetChips.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            TextView chip = (TextView) child;
            int day = child.getTag() instanceof Integer ? (Integer) child.getTag() : 0;
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(16));
            if (day == sheetDayOffset) {
                bg.setColor(primary);
                chip.setTextColor(SkinManager.onAccent(this));
            } else {
                bg.setColor(Color.argb(0x14, Color.red(primary), Color.green(primary), Color.blue(primary)));
                chip.setTextColor(primary);
            }
            chip.setBackground(bg);
        }
    }

    private void applySheetHint() {
        if (tvSheetHint == null || sheetPrograms == null) return;
        String[] labels = {"今天", "昨天", "前天"};
        int total = 0;
        int catchable = 0;
        long now = System.currentTimeMillis();
        long start = startOfDayMs(sheetDayOffset);
        long end = start + 24L * 60 * 60 * 1000;
        for (MobileEpgXml.Program p : sheetPrograms) {
            long t = p.start.getTime();
            if (t < start || t >= end) continue;
            total++;
            if (p.stop.getTime() <= now && sheetCanCatch) catchable++;
        }
        String hint = labels[Math.min(sheetDayOffset, 2)] + " · 共 " + total + " 档";
        if (sheetCanCatch) hint += " · 可回看 " + catchable + " 档";
        else hint += " · 该源不支持回看";
        tvSheetHint.setText(hint);
    }


    private void showCatchupList() {
        if (playingItem == null) {
            Toast.makeText(this, "先点一个频道播放", Toast.LENGTH_SHORT).show();
            return;
        }
        LiveChannelItem source = playingItem;
        String note = "";
        if (!source.hasCatchup()) {
            // 当前分组这条源不能回看，找同名的其它分组源兜底（例如「央视频」组标 none、「央视」组可用）
            LiveChannelItem twin = findCatchupTwin(source);
            if (twin == null) {
                Toast.makeText(this, "该频道不支持回看", Toast.LENGTH_SHORT).show();
                return;
            }
            source = twin;
            String twinGroup = groupNameOf(twin);
            note = " · 改用「" + twinGroup + "」组的同名源";
        }
        if (MobileEpgXml.isKnownNoEpg(source.getChannelName())) {
            Toast.makeText(this, "该频道没有节目单，无法回看", Toast.LENGTH_SHORT).show();
            updateCatchupButton();
            return;
        }
        Toast.makeText(this, "正在加载节目单…", Toast.LENGTH_SHORT).show();
        final AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setTitle("节目单 · " + source.getChannelName() + note)
                .setMessage("正在下载并解析节目单，第一次约几秒；加载完这里会换成按日期分组的节目列表。")
                .setNegativeButton("取消", null)
                .create();
        loadingDialog.show();
        final LiveChannelItem used = source;
        final String usedNote = note;
        MobileEpgXml.load(source.getChannelName(), new MobileEpgXml.Callback() {
            @Override
            public void onLoaded(List<MobileEpgXml.Program> programs) {
                if (isFinishing()) return;
                if (loadingDialog.isShowing()) loadingDialog.dismiss();
                if (programs == null || programs.isEmpty()) {
                    new AlertDialog.Builder(MobileLiveActivity.this)
                            .setTitle("节目单 · " + used.getChannelName())
                            .setMessage("EPG 里没有这个频道的节目单（或最近 72 小时内没有节目），所以没法回看。\n"
                                    + "可以先在设置里确认 EPG 地址是不是 playback.xml，或者换一个能回看的频道试试。")
                            .setPositiveButton("知道了", null)
                            .show();
                    updateCatchupButton();
                    refreshEpgBar();
                    return;
                }
                showProgramDialog(used, programs, usedNote);
                refreshEpgBar();
                updateCatchupButton();
            }

            @Override
            public void onFail(String message) {
                if (isFinishing()) return;
                if (loadingDialog.isShowing()) loadingDialog.dismiss();
                new AlertDialog.Builder(MobileLiveActivity.this)
                        .setTitle("节目单加载失败")
                        .setMessage(message)
                        .setPositiveButton("知道了", null)
                        .show();
            }
        });
    }

    private void showProgramDialog(final LiveChannelItem source,
                                   final List<MobileEpgXml.Program> programs,
                                   final String note) {
        final ProgramListAdapter programAdapter = new ProgramListAdapter(this);
        programAdapter.setPrograms(programs);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("节目单 · " + source.getChannelName() + note);
        builder.setAdapter(programAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ProgramListAdapter.Row row = programAdapter.getItem(which);
                if (row == null || row.program == null) return;
                MobileEpgXml.Program p = row.program;
                if (row.live) {
                    // 正在播的节目：切回直播
                    playingCatchup = false;
                    playUrl(source.getUrl(), source.getChannelName(), false);
                    Toast.makeText(MobileLiveActivity.this, "已切回直播", Toast.LENGTH_SHORT).show();
                } else if (row.future) {
                    showFutureActions(source, p);
                } else {
                    startCatchup(source, p, note);
                }
            }
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    /** 未来节目：可以现在回看（源支持按时间取），也可以设置开播提醒 */
    private void showFutureActions(final LiveChannelItem source, final MobileEpgXml.Program p) {
        final String[] actions = {"回看这一档", "开播时提醒我"};
        new AlertDialog.Builder(this)
                .setTitle(new SimpleDateFormat("HH:mm", Locale.CHINA).format(p.start) + " " + p.title)
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            startCatchup(source, p, "");
                        } else {
                            LiveReminder.schedule(MobileLiveActivity.this,
                                    source.getChannelName(), p.title, p.start.getTime());
                            Toast.makeText(MobileLiveActivity.this,
                                    "已设置开播提醒："
                                            + new SimpleDateFormat("HH:mm", Locale.CHINA).format(p.start)
                                            + " " + p.title, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startCatchup(LiveChannelItem source, MobileEpgXml.Program p, String note) {
        String url = LiveCatchup.build(source.getUrl(), source.getCatchupSource(),
                p.start, p.stop);
        if (url == null) {
            Toast.makeText(this, "回看地址构造失败，该频道可能不支持这种回看方式",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        playingCatchup = true;
        playUrl(url, source.getChannelName() + " · 回看", true);
        Toast.makeText(this, "正在回看：" + p.title, Toast.LENGTH_SHORT).show();
    }

    /** 回看按钮三态：不支持则隐藏；已知无节目单则置灰；其余正常 */
    private void updateCatchupButton() {
        if (playingItem == null) {
            tvMiniCatchup.setVisibility(View.GONE);
            return;
        }
        boolean can = playingItem.hasCatchup() || findCatchupTwin(playingItem) != null;
        if (!can) {
            tvMiniCatchup.setVisibility(View.GONE);
            return;
        }
        tvMiniCatchup.setVisibility(View.VISIBLE);
        boolean noEpg = MobileEpgXml.isKnownNoEpg(playingItem.getChannelName());
        tvMiniCatchup.setText(noEpg ? "无回看" : "回看");
        tvMiniCatchup.setAlpha(noEpg ? 0.45f : 1f);
    }

    // ---------------------------------------------------------------- 收藏 / 最近

    private void updateFavMark() {
        if (playingItem == null) {
            tvMiniFav.setText("☆");
            tvMiniFav.setAlpha(0.6f);
            return;
        }
        tvMiniFav.setAlpha(1f);
        tvMiniFav.setText(isFavorite(playingItem) ? "★" : "☆");
    }

    private void toggleFavorite() {
        if (playingItem == null) {
            Toast.makeText(this, "先点一个频道播放", Toast.LENGTH_SHORT).show();
            return;
        }
        String key = keyOf(playingItem);
        List<String> fav = readKeys("fav");
        if (fav.contains(key)) {
            fav.remove(key);
            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
        } else {
            fav.add(0, key);
            Toast.makeText(this, "已加入收藏", Toast.LENGTH_SHORT).show();
        }
        writeKeys("fav", fav);
        updateFavMark();
        buildGroups();
    }

    private boolean isFavorite(LiveChannelItem item) {
        return readKeys("fav").contains(keyOf(item));
    }

    private void addRecent(LiveChannelItem item) {
        String key = keyOf(item);
        List<String> recent = readKeys("recent");
        recent.remove(key);
        recent.add(0, key);
        while (recent.size() > 30) recent.remove(recent.size() - 1);
        writeKeys("recent", recent);
    }

    private String keyOf(LiveChannelItem item) {
        return item.getChannelName() + "\u0001" + item.getUrl();
    }

    private List<String> readKeys(String name) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(name, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Throwable ignored) {
        }
        return out;
    }

    private void writeKeys(String name, List<String> keys) {
        JSONArray arr = new JSONArray();
        for (String k : keys) arr.put(k);
        prefs.edit().putString(name, arr.toString()).apply();
    }

    private List<LiveChannelItem> buildVirtualList(int mode) {
        List<LiveChannelItem> out = new ArrayList<>();
        List<String> keys = readKeys(mode == PSEUDO_FAV ? "fav" : "recent");
        for (String key : keys) {
            LiveChannelItem item = findByKey(key);
            if (item != null) out.add(item);
        }
        return out;
    }

    private LiveChannelItem findByKey(String key) {
        for (LiveChannelGroup g : groups) {
            ArrayList<LiveChannelItem> list = g.getLiveChannels();
            if (list == null) continue;
            for (LiveChannelItem it : list) {
                if (key.equals(keyOf(it))) return it;
            }
        }
        return null;
    }

    private LiveChannelItem findCatchupTwin(LiveChannelItem item) {
        if (item == null || TextUtils.isEmpty(item.getChannelName())) return null;
        for (LiveChannelGroup g : groups) {
            ArrayList<LiveChannelItem> list = g.getLiveChannels();
            if (list == null) continue;
            for (LiveChannelItem it : list) {
                if (it == item) continue;
                if (item.getChannelName().equals(it.getChannelName()) && it.hasCatchup()) {
                    return it;
                }
            }
        }
        return null;
    }

    private String groupNameOf(LiveChannelItem item) {
        for (LiveChannelGroup g : groups) {
            ArrayList<LiveChannelItem> list = g.getLiveChannels();
            if (list == null) continue;
            if (list.contains(item)) return g.getGroupName();
        }
        return "";
    }

    /** 在真实分类里的位置，用于跳横屏全屏页 */
    private int[] locate(LiveChannelItem item) {
        for (int i = 0; i < groups.size(); i++) {
            ArrayList<LiveChannelItem> list = groups.get(i).getLiveChannels();
            if (list == null) continue;
            for (int j = 0; j < list.size(); j++) {
                LiveChannelItem it = list.get(j);
                if (it == item || (item.getChannelName().equals(it.getChannelName())
                        && item.getUrl() != null && item.getUrl().equals(it.getUrl()))) {
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 频道数据

    private List<LiveChannelItem> currentList() {
        if (pseudo != PSEUDO_NONE) return buildVirtualList(pseudo);
        if (currentGroup < 0 || currentGroup >= groups.size()) return null;
        return groups.get(currentGroup).getLiveChannels();
    }

    private void loadChannels() {
        List<LiveChannelGroup> list = ApiConfig.get().getChannelGroupList();
        boolean cacheIsProxy = list != null && list.size() == 1
                && list.get(0).getGroupName() != null
                && list.get(0).getGroupName().startsWith("http://127.0.0.1");
        // 缓存里的分组只是「本地代理地址」时，它包的是上次 ApiConfig.loadConfig 那一刻的直播源地址。
        // 用户随后在设置里改了直播源，这份缓存就是旧的 —— 这里一律用设置里的当前地址重建，
        // 手机端改直播源后不用重启 App 就能生效。
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
            if (list == null || list.isEmpty()) {
                showEmptyState();
                return;
            }
        }
        if (list.get(0).getGroupName() != null
                && list.get(0).getGroupName().startsWith("http://127.0.0.1")) {
            loadProxyLives(list.get(0).getGroupName());
            return;
        }
        groups.clear();
        groups.addAll(list);
        buildGroups();
    }

    /** 与 ApiConfig 里构建代理分组同一格式：ext = base64(直播源地址) */
    private String proxyUrlOf(String liveUrl) {
        try {
            String enc = Base64.encodeToString(liveUrl.getBytes("UTF-8"),
                    Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
            return "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + enc;
        } catch (Throwable th) {
            return liveUrl;
        }
    }

    /** 本地代理型直播源（ext 参数里是 base64 的 txt 地址） */
    private void loadProxyLives(String url) {
        try {
            Uri parsedUrl = Uri.parse(url);
            url = new String(Base64.decode(parsedUrl.getQueryParameter("ext"),
                    Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
            if (url.equals("")) {
                showEmptyState();
                return;
            }
        } catch (Throwable th) {
            showEmptyState();
            return;
        }
        pbLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
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
                    showEmptyState();
                    return;
                }
                groups.clear();
                groups.addAll(loaded);
                buildGroups();
            }

            @Override
            public void onError(Response<String> response) {
                super.onError(response);
                showEmptyState();
            }
        });
    }

    private void buildGroups() {
        pbLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        rvChannel.setVisibility(View.VISIBLE);

        llGroup.removeAllViews();
        svGroup.setVisibility(View.VISIBLE);

        addChip("★ 收藏", PSEUDO_FAV);
        addChip("最近", PSEUDO_RECENT);
        for (int i = 0; i < groups.size(); i++) {
            addGroupChip(i);
        }
        showChannels();
    }

    private void addChip(String label, final int pseudoMode) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(11);
        int h = dp(32);
        chip.setHeight(h);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(4), 0, dp(4), 0);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setBackgroundResource(R.drawable.shape_filter_chip);
        chip.setSelected(pseudo == pseudoMode);
        chip.setTextColor(getResources().getColor(
                pseudo == pseudoMode ? R.color.shell_text : R.color.shell_text_dim));
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pseudo == pseudoMode) return;
                pseudo = pseudoMode;
                playingIndex = -1;
                buildGroups();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, h);
        lp.setMargins(0, 0, 0, dp(6));
        llGroup.addView(chip, lp);
    }

    private void addGroupChip(final int index) {
        LiveChannelGroup group = groups.get(index);
        TextView chip = new TextView(this);
        chip.setText(group.getGroupName());
        chip.setTextSize(11);
        int h = dp(32);
        chip.setHeight(h);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(4), 0, dp(4), 0);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setBackgroundResource(R.drawable.shape_filter_chip);
        boolean on = pseudo == PSEUDO_NONE && currentGroup == index;
        chip.setSelected(on);
        chip.setTextColor(getResources().getColor(
                on ? R.color.shell_text : R.color.shell_text_dim));
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pseudo == PSEUDO_NONE && currentGroup == index) return;
                pseudo = PSEUDO_NONE;
                currentGroup = index;
                // 换分类后顶部播放器里的频道不属于新分类了，清掉网格高亮，等用户点新分类里的频道
                playingIndex = -1;
                buildGroups();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, h);
        lp.setMargins(0, 0, 0, dp(6));
        llGroup.addView(chip, lp);
    }

    private void showChannels() {
        List<LiveChannelItem> channels = currentList();
        if (channels == null || channels.isEmpty()) {
            if (pseudo == PSEUDO_FAV) {
                showVirtualEmpty("还没有收藏的频道，点播放器右上角 ☆ 收藏");
            } else if (pseudo == PSEUDO_RECENT) {
                showVirtualEmpty("还没有看过的频道");
            } else {
                showEmptyState();
            }
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        rvChannel.setVisibility(View.VISIBLE);
        int sel = -1;
        if (playingItem != null) {
            for (int i = 0; i < channels.size(); i++) {
                if (channels.get(i) == playingItem) {
                    sel = i;
                    break;
                }
            }
        }
        playingIndex = sel;
        channelAdapter.setData(channels, sel);
        rvChannel.scrollToPosition(0);
        checkAlive(channels);
        if (playingItem != null) loadProgramsFor(playingItem);
    }

    /** 逐个频道探测源有效性：蓝/灰颜色由结果决定，检测全在后台线程 */
    private void checkAlive(final List<LiveChannelItem> channels) {
        channelAdapter.clearAlive();
        for (final LiveChannelItem item : channels) {
            if (item == null || TextUtils.isEmpty(item.getUrl())) continue;
            Boolean hit = LiveSourceChecker.cached(item.getUrl());
            if (hit != null) {
                channelAdapter.updateAlive(item.getUrl(), hit);
                continue;
            }
            LiveSourceChecker.check(item.getUrl(), new LiveSourceChecker.Callback() {
                @Override
                public void onResult(final String url, final boolean alive) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || channelAdapter == null) return;
                            channelAdapter.updateAlive(url, alive);
                        }
                    });
                }
            });
        }
    }

    // ---------------------------------------------------------------- 右列节目单


    /** 频道是否具备回看能力（自身没模板时用同名源兜底） */
    private boolean canCatchup(LiveChannelItem item) {
        if (item == null) return false;
        if (MobileEpgXml.isKnownNoEpg(item.getChannelName())) return false;
        return item.hasCatchup() || findCatchupTwin(item) != null;
    }

    /** 真正用来构造回看地址的源：本身支持就用自己的，否则换同名的可回看源 */
    private LiveChannelItem resolveCatchupSource(LiveChannelItem item) {
        if (item == null) return null;
        if (item.hasCatchup()) return item;
        return findCatchupTwin(item);
    }

    private void loadProgramsFor(final LiveChannelItem item) {
        if (item == null) {
            programAdapter.setData(null);
            tvEpgHint.setText("点中间频道看节目单");
            return;
        }
        final String name = item.getChannelName();
        final boolean can = canCatchup(item);
        programAdapter.setCanCatchup(can);
        List<MobileEpgXml.Program> cached = MobileEpgXml.cachedPrograms(name);
        if (cached != null) {
            bindPrograms(cached, name);
            return;
        }
        if (MobileEpgXml.isKnownNoEpg(name)) {
            programAdapter.setData(null);
            tvEpgHint.setText("该频道没有节目单");
            tvEpgEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEpgEmpty.setVisibility(View.GONE);
        tvEpgHint.setText("正在加载节目单…（首次需下载约 2.5MB，之后走本地缓存）");
        MobileEpgXml.load(name, new MobileEpgXml.Callback() {
            @Override
            public void onLoaded(List<MobileEpgXml.Program> programs) {
                if (isFinishing()) return;
                bindPrograms(programs, name);
            }

            @Override
            public void onFail(String message) {
                if (isFinishing()) return;
                tvEpgHint.setText(message);
                tvEpgEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void bindPrograms(List<MobileEpgXml.Program> programs, String channelName) {
        if (programs == null || programs.isEmpty()) {
            programAdapter.setData(null);
            tvEpgHint.setText("该频道没有节目单");
            tvEpgEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEpgEmpty.setVisibility(View.GONE);
        programAdapter.setData(programs);
        int current = -1;
        long now = System.currentTimeMillis();
        for (int i = 0; i < programs.size(); i++) {
            MobileEpgXml.Program p = programs.get(i);
            if (p.start.getTime() <= now && p.stop.getTime() > now) {
                current = i;
                break;
            }
            if (p.start.getTime() > now) {
                current = Math.max(0, i - 1);
                break;
            }
        }
        boolean can = playingItem != null && canCatchup(playingItem);
        tvEpgHint.setText(channelName + " · 共 " + programs.size() + " 档"
                + (can ? " · 蓝色=可回放" : " · 灰色=失效或不支持回放"));
        if (current >= 0) rvEpisode.scrollToPosition(current);
    }

    private void onEpgProgramTap(MobileEpgXml.Program program, boolean live, boolean canCatchup) {
        if (playingItem == null) return;
        if (live) {
            playingCatchup = false;
            playUrl(playingItem.getUrl(), playingItem.getChannelName(), false);
            Toast.makeText(this, "已切回直播", Toast.LENGTH_SHORT).show();
            return;
        }
        long now = System.currentTimeMillis();
        if (program.start.getTime() > now) {
            showFutureActions(playingItem, program);
            return;
        }
        if (!canCatchup) {
            Toast.makeText(this, "该频道不支持回看这一档", Toast.LENGTH_SHORT).show();
            return;
        }
        LiveChannelItem source = resolveCatchupSource(playingItem);
        if (source == null) {
            Toast.makeText(this, "该频道不支持回看", Toast.LENGTH_SHORT).show();
            return;
        }
        startCatchup(source, program, "");
    }

    // ---------------------------------------------------------------- 节目回看（7 天 + 半小时格子）

    private Calendar catchupDayStart(int offset) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, offset);
        return c;
    }

    private String catchupDayLabel(int offset) {
        Calendar c = catchupDayStart(offset);
        String md = new SimpleDateFormat("MM/dd", Locale.CHINA).format(c.getTime());
        if (offset == 0) return "今天 " + md;
        if (offset == -1) return "昨天 " + md;
        if (offset == -2) return "前天 " + md;
        return (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日 " + md;
    }

    private void buildCatchupDays() {
        llCatchupDays.removeAllViews();
        for (int i = 0; i >= -6; i--) {
            final int offset = i;
            TextView chip = new TextView(this);
            chip.setText(catchupDayLabel(offset));
            chip.setTextSize(11);
            chip.setSingleLine(true);
            chip.setGravity(Gravity.CENTER);
            chip.setHeight(dp(30));
            chip.setPadding(dp(10), 0, dp(10), 0);
            chip.setBackgroundResource(R.drawable.shape_filter_chip);
            boolean on = offset == catchupDayOffset;
            chip.setSelected(on);
            chip.setTextColor(getResources().getColor(
                    on ? R.color.shell_text : R.color.shell_text_dim));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (catchupDayOffset == offset) return;
                    catchupDayOffset = offset;
                    buildCatchupDays();
                    buildCatchupSlots();
                    updateCatchupTip();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
            lp.setMargins(0, 0, dp(6), 0);
            llCatchupDays.addView(chip, lp);
        }
    }

    private void buildCatchupSlots() {
        glCatchupSlots.removeAllViews();
        final long now = System.currentTimeMillis();
        final long dayStart = catchupDayStart(catchupDayOffset).getTimeInMillis();
        final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.CHINA);
        for (int slot = 0; slot < 48; slot++) {
            final long startMs = dayStart + slot * 30L * 60L * 1000L;
            boolean future = startMs > now;
            TextView cell = new TextView(this);
            cell.setText(hm.format(new Date(startMs)));
            cell.setTextSize(11);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundResource(R.drawable.shape_filter_chip);
            cell.setTextColor(getResources().getColor(
                    future ? R.color.shell_text_dim : R.color.shell_text));
            if (!future) {
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startCatchupAt(startMs);
                    }
                });
            } else {
                cell.setAlpha(0.35f);
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(30);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            glCatchupSlots.addView(cell, lp);
        }
    }

    private void startCatchupAt(long startMs) {
        if (playingItem == null) {
            Toast.makeText(this, "先在中间选一个频道", Toast.LENGTH_SHORT).show();
            return;
        }
        LiveChannelItem source = resolveCatchupSource(playingItem);
        if (source == null) {
            Toast.makeText(this, "当前频道不支持回看", Toast.LENGTH_SHORT).show();
            return;
        }
        long endMs = startMs + 30L * 60L * 1000L;
        String url = LiveCatchup.build(source.getUrl(), source.getCatchupSource(),
                new Date(startMs), new Date(endMs));
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "这一档生成不出回看地址", Toast.LENGTH_SHORT).show();
            return;
        }
        playingCatchup = true;
        playUrl(url, source.getChannelName(), true);
        Toast.makeText(this, "回看 "
                + new SimpleDateFormat("MM/dd HH:mm", Locale.CHINA).format(new Date(startMs))
                + " 起半小时", Toast.LENGTH_SHORT).show();
    }

    private void updateCatchupTip() {
        if (tvCatchupTip == null) return;
        if (playingItem == null) {
            tvCatchupTip.setText("先选频道，再挑日期和时间段");
            return;
        }
        if (!canCatchup(playingItem)) {
            tvCatchupTip.setText("当前频道源不支持回看，换一个频道试试");
            return;
        }
        String label = catchupDayLabel(catchupDayOffset);
        tvCatchupTip.setText(label + " · 按半小时回看，画面为空请点「回看」回直播");
    }

    private void showVirtualEmpty(String text) {
        pbLoading.setVisibility(View.GONE);
        rvChannel.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText(text);
    }

    private void showEmptyState() {
        pbLoading.setVisibility(View.GONE);
        rvChannel.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText(R.string.m_live_empty);
        Toast.makeText(App.getInstance(), "没有可用的直播频道", Toast.LENGTH_SHORT).show();
    }

    /** 与 TV LivePlayActivity.setPlayHeaders 同规则：命中 livePlayHeaders 的 flag 就用其 header，否则用默认 UA */
    private HashMap<String, String> liveHeaders(String url) {
        HashMap<String, String> header = new HashMap<>();
        try {
            boolean matchTo = false;
            JSONArray livePlayHeaders = new JSONArray(ApiConfig.get().getLivePlayHeaders().toString());
            for (int i = 0; i < livePlayHeaders.length(); i++) {
                JSONObject headerObj = livePlayHeaders.getJSONObject(i);
                JSONArray flags = headerObj.getJSONArray("flag");
                JSONObject headerData = headerObj.getJSONObject("header");
                for (int j = 0; j < flags.length(); j++) {
                    if (url.contains(flags.getString(j))) {
                        matchTo = true;
                        break;
                    }
                }
                if (matchTo) {
                    Iterator<String> keys = headerData.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        header.put(key, headerData.getString(key));
                    }
                    break;
                }
            }
            if (!matchTo) header.put("User-Agent", "Lavf/59.27.100");
        } catch (Exception e) {
            header.put("User-Agent", "Lavf/59.27.100");
        }
        return header;
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void onPause() {
        if (videoViewMini != null) {
            videoViewMini.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoViewMini != null && playingItem != null) {
            videoViewMini.resume();
        }
    }

    @Override
    protected void onDestroy() {
        if (videoViewMini != null) {
            videoViewMini.release();
        }
        super.onDestroy();
    }
}
