package com.github.tvbox.osc.mobile.ui.activity;

import android.content.DialogInterface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.mobile.setting.MobileUiTuning;
import com.github.tvbox.osc.mobile.play.MobileParseHelper;
import com.github.tvbox.osc.mobile.ui.MobileCastHelper;
import com.github.tvbox.osc.mobile.ui.MobileDownloadHelper;
import com.github.tvbox.osc.mobile.ui.MobileFavorites;
import com.github.tvbox.osc.mobile.ui.adapter.MobileSeriesAdapter;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.ui.activity.PlayActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import xyz.doikki.videoplayer.player.ProgressManager;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * 手机端播放页。
 *
 * 取流链路与 TV 的 PlayActivity 一致：SourceViewModel.playResult → playUrl + url（+ header）。
 * 与 TV 的差别：
 * - 强制横屏、全屏 VideoView + 触摸控制层（点画面显隐控制条、拖进度、上/下一集、选集弹窗、画面比例切换）；
 * - 需要「解析」的线路（parse=1 / 需 webview 解析的源）直接回落到 TV 的 PlayActivity，
 *   避免在手机端重复实现解析引擎；直链与自带 playUrl 的线路在本页内播放。
 */
public class MobilePlayActivity extends BaseActivity {

    private MyVideoView videoView;
    private ProgressBar pbLoading;
    private View llTop;
    private View llBottom;
    private TextView tvTitle;
    private TextView tvSeries;
    private TextView tvPosition;
    private TextView tvDuration;
    private TextView tvPlayPause;
    private TextView tvPrev;
    private TextView tvNext;
    private TextView tvScale;
    private TextView tvFavPlay;
    private TextView tvDownload;
    private TextView tvCastPlay;
    private SeekBar sbProgress;
    /** 当前真正在播的地址与请求头（投屏 / 下载要用），解析出来的也得记着 */
    private String lastPlayUrl = "";
    private HashMap<String, String> lastPlayHeaders = null;

    /* ---------- C3 选集抽屉 ---------- */
    private View seriesRoot;
    private View seriesMask;
    private RecyclerView rvPanelSeries;
    private LinearLayout llPanelFlags;
    private HorizontalScrollView svPanelFlags;
    private TextView tvPanelTitle;
    private MobileSeriesAdapter panelAdapter;

    private SourceViewModel sourceViewModel;
    private VodInfo vodInfo;
    private String sourceKey;
    private String progressKey;
    /** 竖屏页点全屏带过来的续播位置（毫秒） */
    private long pendingSeek = 0;
    private String subtitleCacheKey;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean userSeeking = false;
    private boolean overlayVisible = true;
    private int scaleIndex = 0;
    private int skipIntroSeconds = 0;

    /* ---------- 手势（C2） ---------- */
    private View touchLayer;
    private TextView tvGestureHint;
    private AudioManager audioManager;
    private int maxVolume = 15;
    private float downX, downY;
    private long downTime = 0, lastTapTime = 0;
    private int gestureMode = 0;          // 0 未定 1 横向进度 2 纵向亮度/音量
    private boolean verticalLeft = false;
    private long gestureSeekMs = 0;
    private long gestureBaseMs = 0;
    private float startBrightness = 0f;
    private int startVolume = 0;

    private final Runnable hideHintTask = new Runnable() {
        @Override
        public void run() {
            if (tvGestureHint != null) tvGestureHint.setVisibility(View.GONE);
        }
    };

    private final Runnable progressTask = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 800);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_play_mobile;
    }

    @Override
    public float getSizeInDp() {
        return 800;
    }

    @Override
    public boolean isBaseOnWidth() {
        return true;
    }

    @Override
    protected void init() {
        videoView = findViewById(R.id.videoView);
        pbLoading = findViewById(R.id.pbLoading);
        llTop = findViewById(R.id.llTop);
        llBottom = findViewById(R.id.llBottom);
        tvTitle = findViewById(R.id.tvTitle);
        tvSeries = findViewById(R.id.tvSeries);
        tvPosition = findViewById(R.id.tvPosition);
        tvDuration = findViewById(R.id.tvDuration);
        tvPlayPause = findViewById(R.id.tvPlayPause);
        tvPrev = findViewById(R.id.tvPrev);
        tvNext = findViewById(R.id.tvNext);
        tvScale = findViewById(R.id.tvScale);
        sbProgress = findViewById(R.id.sbProgress);
        tvFavPlay = findViewById(R.id.tvFavPlay);
        tvDownload = findViewById(R.id.tvDownload);
        tvCastPlay = findViewById(R.id.tvCastPlay);
        touchLayer = findViewById(R.id.touchLayer);
        tvGestureHint = findViewById(R.id.tvGestureHint);

        // C3 选集抽屉
        seriesRoot = findViewById(R.id.seriesRoot);
        seriesMask = findViewById(R.id.seriesMask);
        rvPanelSeries = findViewById(R.id.rvPanelSeries);
        llPanelFlags = findViewById(R.id.llPanelFlags);
        svPanelFlags = findViewById(R.id.svPanelFlags);
        tvPanelTitle = findViewById(R.id.tvPanelTitle);
        panelAdapter = new MobileSeriesAdapter();
        rvPanelSeries.setLayoutManager(new GridLayoutManager(this, MobileUiTuning.SERIES_PANEL_COLUMNS));
        rvPanelSeries.setAdapter(panelAdapter);
        panelAdapter.setOnItemClick(new MobileSeriesAdapter.OnItemClick() {
            @Override
            public void onItemClick(int position, VodInfo.VodSeries series) {
                switchSeries(position);
                hideSeriesPanel();
            }
        });
        seriesMask.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSeriesPanel();
            }
        });
        findViewById(R.id.tvPanelClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSeriesPanel();
            }
        });
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }

        Bundle bundle = getIntent() != null ? getIntent().getExtras() : null;
        if (bundle == null) {
            finish();
            return;
        }
        vodInfo = (VodInfo) bundle.getSerializable("VodInfo");
        sourceKey = bundle.getString("sourceKey");
        pendingSeek = bundle.getLong("seekTo", 0);
        if (vodInfo == null || vodInfo.seriesMap == null || vodInfo.seriesMap.isEmpty()) {
            finish();
            return;
        }

        // B4 跳过片头：解析播放配置里的 st（秒），与 TV PlayActivity 的 mVodPlayerCfg.getInt("st") 同语义
        skipIntroSeconds = parseSkipIntroSeconds(vodInfo.playerCfg);

        PlayerHelper.updateCfg(videoView);
        // 画面比例：起播时按设置项 PLAY_SCALE 恢复（原来只在点按时循环，设置里的值不起作用）
        applySavedScale();
        // C1 断点续播：与 TV PlayActivity 同一套进度存储（CacheManager + URL 的 MD5）
        videoView.setProgressManager(new ProgressManager() {
            @Override
            public void saveProgress(String url, long progress) {
                long duration = videoView.getDuration();
                if (duration <= 0) return;
                // B4 片尾回零：离结束不足 TAIL_RESET_MS 时记为 0，下次进来从头播（TV 端没做这条）
                if (progress >= duration - MobileUiTuning.TAIL_RESET_MS) {
                    CacheManager.save(MD5.string2MD5(url), 0L);
                    return;
                }
                CacheManager.save(MD5.string2MD5(url), progress);
            }

            @Override
            public long getSavedProgress(String url) {
                long skip = skipIntroSeconds * 1000L;
                if (CacheManager.getCache(MD5.string2MD5(url)) == null) return skip;
                long rec = (long) CacheManager.getCache(MD5.string2MD5(url));
                if (rec < skip) return skip;
                return rec;
            }
        });
        videoView.setOnStateChangeListener(new VideoView.OnStateChangeListener() {
            @Override
            public void onPlayerStateChanged(int playerState) {
            }

            @Override
            public void onPlayStateChanged(final int playState) {
                // 解析回调（WebView shouldInterceptRequest 线程）会直接触发 state 变化，
                // 这里的 View 操作必须切回主线程，否则崩溃 CalledFromWrongThreadException
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        applyPlayState(playState);
                    }
                });
            }
        });

        findViewById(R.id.tvBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        tvPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoView.isPlaying()) {
                    videoView.pause();
                } else {
                    videoView.resume();
                }
            }
        });
        tvPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPre();
            }
        });
        tvNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNext();
            }
        });
        tvSeries.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSeriesPanel();
            }
        });
        tvScale.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 点按循环切换 + 回写设置项，下次起播按这里的选择恢复
                applyScale(scaleIndex + 1);
                Hawk.put(HawkConfig.PLAY_SCALE, scaleIndex);
            }
        });
        // v1.7.1：顶栏收藏 / 底部下载 / 底部投屏
        if (tvFavPlay != null) {
            tvFavPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleFavorite();
                }
            });
            updateFavLabel();
        }
        if (tvDownload != null) {
            tvDownload.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadCurrent();
                }
            });
        }
        if (tvCastPlay != null) {
            tvCastPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (TextUtils.isEmpty(lastPlayUrl)) {
                        Toast.makeText(MobilePlayActivity.this,
                                "还没有拿到播放地址，等画面出来再投屏", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String t = vodInfo != null && !TextUtils.isEmpty(vodInfo.name) ? vodInfo.name : "";
                    MobileCastHelper.cast(MobilePlayActivity.this, t, lastPlayUrl);
                }
            });
        }
        touchLayer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleGesture(event);
            }
        });
        sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                long duration = videoView.getDuration();
                if (duration > 0) {
                    videoView.seekTo((long) (duration * seekBar.getProgress() / 100.0));
                }
            }
        });

        tvScale.setText(PlayerHelper.getScaleName(VideoView.SCREEN_SCALE_DEFAULT));

        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.playResult.observeForever(playObserver);

        play(false);
        handler.postDelayed(progressTask, 800);
    }

    private final Observer<JSONObject> playObserver = new Observer<JSONObject>() {
        @Override
        public void onChanged(JSONObject info) {
            if (info == null) {
                Toast.makeText(MobilePlayActivity.this, "获取播放信息失败", Toast.LENGTH_SHORT).show();
                pbLoading.setVisibility(View.GONE);
                return;
            }
            progressKey = info.optString("proKey", null);
            subtitleCacheKey = info.optString("subtKey", null);
            boolean parse = info.optString("parse", "1").equals("1");
            boolean jx = info.optString("jx", "0").equals("1");
            String playUrl = info.optString("playUrl", "");
            String msg = info.optString("msg", "");
            if (!msg.isEmpty()) {
                Toast.makeText(MobilePlayActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
            String url;
            try {
                url = info.getString("url");
            } catch (Throwable th) {
                url = "";
            }
            HashMap<String, String> headers = null;
            if (info.has("header")) {
                try {
                    JSONObject hds = new JSONObject(info.getString("header"));
                    Iterator<String> keys = hds.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (headers == null) headers = new HashMap<>();
                        headers.put(key, hds.getString(key));
                    }
                } catch (Throwable ignored) {
                }
            }

            if (parse || jx) {
                // 需要解析的线路：在手机端自己的解析引擎里跑（json 接口 / WebView 嗅探），
                // 不再整页甩给 TV 的 PlayActivity（那样界面就变成电视版了）。
                pbLoading.setVisibility(View.VISIBLE);
                MobileParseHelper.parse(MobilePlayActivity.this, jx, playUrl, url, new MobileParseHelper.Callback() {
                    @Override
                    public void onParsed(final String realUrl, final HashMap<String, String> parsedHeaders) {
                        // 解析回调在 WebView 线程，回主线程再改界面与起播
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pbLoading.setVisibility(View.GONE);
                                playUrl(realUrl, parsedHeaders);
                            }
                        });
                    }

                    @Override
                    public void onFail(final String reason) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pbLoading.setVisibility(View.GONE);
                                Toast.makeText(MobilePlayActivity.this,
                                        "解析失败：" + reason + "，已切到电视内核兜底", Toast.LENGTH_LONG).show();
                                Bundle fallback = new Bundle();
                                fallback.putString("sourceKey", sourceKey);
                                fallback.putSerializable("VodInfo", vodInfo);
                                jumpActivity(PlayActivity.class, fallback);
                                finish();
                            }
                        });
                    }
                });
                return;
            }
            playUrl(playUrl + url, headers);
        }
    };

    private void play(boolean forceFromStart) {
        VodInfo.VodSeries series = currentSeries();
        if (series == null) {
            Toast.makeText(this, "没有可播放的剧集", Toast.LENGTH_SHORT).show();
            return;
        }
        updateTitle();
        pbLoading.setVisibility(View.VISIBLE);
        sourceViewModel.getPlay(sourceKey, vodInfo.playFlag, progressKey, series.url, subtitleCacheKey);
    }

    private void playUrl(String url, HashMap<String, String> headers) {
        if (TextUtils.isEmpty(url) || "null".equals(url)) {
            Toast.makeText(this, "播放地址为空", Toast.LENGTH_SHORT).show();
            pbLoading.setVisibility(View.GONE);
            return;
        }
        // v1.7.1 兜底：解析后/进页时把地址也记下来，投屏与下载都靠它
        lastPlayUrl = url;
        lastPlayHeaders = headers;
        videoView.release();
        videoView.setUrl(url, headers);
        videoView.start();
    }

    /** 顶栏收藏：与详情页 / 首页收藏夹共用 MobileFavorites 这一份数据 */
    private void toggleFavorite() {
        if (vodInfo == null) return;
        MobileFavorites.Item it = new MobileFavorites.Item();
        it.vodId = vodInfo.id == null ? "" : vodInfo.id;
        it.sourceKey = sourceKey == null ? "" : sourceKey;
        it.name = vodInfo.name == null ? "" : vodInfo.name;
        it.pic = vodInfo.pic == null ? "" : vodInfo.pic;
        it.time = System.currentTimeMillis();
        VodInfo.VodSeries series = currentSeries();
        if (series != null && series.name != null) it.note = series.name;
        boolean added = MobileFavorites.toggle(it);
        updateFavLabel();
        Toast.makeText(this, added ? "已加入收藏，首页收藏夹可见" : "已取消收藏", Toast.LENGTH_SHORT).show();
    }

    private void updateFavLabel() {
        if (tvFavPlay == null || vodInfo == null) return;
        boolean has = MobileFavorites.has(vodInfo.id == null ? "" : vodInfo.id,
                sourceKey == null ? "" : sourceKey);
        tvFavPlay.setText(has ? "★ 已收藏" : "☆ 收藏");
        tvFavPlay.setAlpha(has ? 1f : 0.85f);
    }

    /** 下载当前集：与直播录制同一个落盘目录，完成后可在「下载管理」查看 */
    private void downloadCurrent() {
        if (TextUtils.isEmpty(lastPlayUrl)) {
            Toast.makeText(this, "还没有拿到播放地址，先等画面出来再下载", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = vodInfo == null ? "video" : (vodInfo.name == null ? "video" : vodInfo.name);
        VodInfo.VodSeries series = currentSeries();
        if (series != null && series.name != null && series.name.length() > 0) {
            name = name + " " + series.name;
        }
        MobileDownloadHelper.start(this, name, lastPlayUrl, lastPlayHeaders);
    }

    private VodInfo.VodSeries currentSeries() {
        List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (list == null || list.isEmpty()) return null;
        int index = vodInfo.getplayIndex();
        if (index < 0 || index >= list.size()) index = 0;
        return list.get(index);
    }

    /** 播放状态变化后的界面刷新，统一在主线程执行 */
    private void applyPlayState(int playState) {
        switch (playState) {
            case VideoView.STATE_PREPARED:
                // 从竖屏内嵌页点「全屏」过来时，接着原来的播放进度
                if (pendingSeek > 0) {
                    long target = pendingSeek;
                    pendingSeek = 0;
                    videoView.seekTo(target);
                }
                break;
            case VideoView.STATE_PLAYING:
                pbLoading.setVisibility(View.GONE);
                tvPlayPause.setText(R.string.m_pause);
                break;
            case VideoView.STATE_PAUSED:
                tvPlayPause.setText(R.string.m_play);
                break;
            case VideoView.STATE_PREPARING:
                pbLoading.setVisibility(View.VISIBLE);
                break;
            case VideoView.STATE_PLAYBACK_COMPLETED:
                pbLoading.setVisibility(View.GONE);
                playNext();
                break;
            case VideoView.STATE_ERROR:
                pbLoading.setVisibility(View.GONE);
                Toast.makeText(MobilePlayActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void playNext() {
        List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (list == null) return;
        int index = vodInfo.getplayIndex();
        if (index + 1 >= list.size()) {
            Toast.makeText(this, "已是最后一集", Toast.LENGTH_SHORT).show();
            return;
        }
        switchSeries(index + 1);
    }

    private void playPre() {
        int index = vodInfo.getplayIndex();
        if (index - 1 < 0) {
            Toast.makeText(this, "已是第一集", Toast.LENGTH_SHORT).show();
            return;
        }
        switchSeries(index - 1);
    }

    private void switchSeries(int index) {
        List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (list == null || index < 0 || index >= list.size()) return;
        for (int i = 0; i < list.size(); i++) {
            list.get(i).selected = (i == index);
        }
        vodInfo.playIndex = index;
        play(false);
    }

    /* ===================== C3 选集抽屉 / 线路切换 ===================== */

    private void showSeriesPanel() {
        if (vodInfo == null || vodInfo.seriesMap == null || vodInfo.seriesMap.isEmpty()) return;
        overlayVisible = false;
        llTop.setVisibility(View.GONE);
        llBottom.setVisibility(View.GONE);
        seriesRoot.setVisibility(View.VISIBLE);
        buildPanelFlags();
        refreshPanelSeries();
    }

    private void hideSeriesPanel() {
        seriesRoot.setVisibility(View.GONE);
        overlayVisible = true;
        llTop.setVisibility(View.VISIBLE);
        llBottom.setVisibility(View.VISIBLE);
    }

    private void buildPanelFlags() {
        llPanelFlags.removeAllViews();
        if (vodInfo.seriesFlags == null || vodInfo.seriesFlags.size() <= 1) {
            svPanelFlags.setVisibility(View.GONE);
            return;
        }
        svPanelFlags.setVisibility(View.VISIBLE);
        for (final VodInfo.VodSeriesFlag flag : vodInfo.seriesFlags) {
            boolean on = flag.name.equals(vodInfo.playFlag);
            TextView chip = new TextView(this);
            chip.setText(flag.name);
            chip.setTextSize(12);
            int h = dp(30);
            chip.setHeight(h);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12), 0, dp(12), 0);
            chip.setBackgroundResource(R.drawable.shape_filter_chip);
            chip.setSelected(on);
            chip.setTextColor((on ? com.github.tvbox.osc.util.SkinManager.text1(this) : com.github.tvbox.osc.util.SkinManager.text2(this)));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchFlag(flag.name);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, h);
            lp.setMargins(0, 0, dp(8), 0);
            llPanelFlags.addView(chip, lp);
        }
    }

    private void switchFlag(String flagName) {
        if (flagName.equals(vodInfo.playFlag)) return;
        List<VodInfo.VodSeries> series = vodInfo.seriesMap.get(flagName);
        if (series == null || series.isEmpty()) {
            Toast.makeText(this, "该线路没有剧集", Toast.LENGTH_SHORT).show();
            return;
        }
        if (vodInfo.seriesFlags != null) {
            for (VodInfo.VodSeriesFlag flag : vodInfo.seriesFlags) {
                flag.selected = flag.name.equals(flagName);
            }
        }
        vodInfo.playFlag = flagName;
        vodInfo.playIndex = 0;
        buildPanelFlags();
        refreshPanelSeries();
        play(false);
    }

    private void refreshPanelSeries() {
        List<VodInfo.VodSeries> series = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (series == null || series.isEmpty()) return;
        for (int i = 0; i < series.size(); i++) {
            series.get(i).selected = (i == vodInfo.getplayIndex());
        }
        panelAdapter.setData(series);
        tvPanelTitle.setText("选集 · " + vodInfo.playFlag + "（共 " + series.size() + " 集）");
        int index = vodInfo.getplayIndex();
        if (index >= 0 && index < series.size()) rvPanelSeries.scrollToPosition(index);
    }

    private void updateTitle() {
        VodInfo.VodSeries series = currentSeries();
        String name = vodInfo.name == null ? "" : vodInfo.name;
        if (series != null && !TextUtils.isEmpty(series.name)) name = name + " · " + series.name;
        tvTitle.setText(name);
        tvSeries.setText("选集 " + (vodInfo.getplayIndex() + 1) + "/"
                + (vodInfo.seriesMap.get(vodInfo.playFlag) == null ? 0 : vodInfo.seriesMap.get(vodInfo.playFlag).size()));
    }

    private void toggleOverlay() {
        overlayVisible = !overlayVisible;
        llTop.setVisibility(overlayVisible ? View.VISIBLE : View.GONE);
        llBottom.setVisibility(overlayVisible ? View.VISIBLE : View.GONE);
    }

    private void updateProgress() {
        if (userSeeking) return;
        long duration = videoView.getDuration();
        long position = videoView.getCurrentPosition();
        if (duration > 0) {
            sbProgress.setProgress((int) (position * 100 / duration));
        }
        tvPosition.setText(formatTime(position));
        tvDuration.setText(formatTime(duration));
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "00:00";
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    /* ===================== 手势控制（C2） ===================== */

    /**
     * 单击：显隐控制条；双击：暂停/播放；
     * 横滑：调播放进度；左半屏上下滑：亮度；右半屏上下滑：音量。
     */
    private boolean handleGesture(MotionEvent event) {
        int width = touchLayer.getWidth();
        int height = touchLayer.getHeight();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                downTime = System.currentTimeMillis();
                gestureMode = 0;
                gestureBaseMs = videoView.getCurrentPosition();
                gestureSeekMs = gestureBaseMs;
                startBrightness = currentBrightness();
                startVolume = audioManager == null ? 0
                        : audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                float threshold = dp(MobileUiTuning.GESTURE_START_SLOP_DP);
                if (gestureMode == 0) {
                    if (Math.abs(dx) < threshold && Math.abs(dy) < threshold) return true;
                    if (Math.abs(dx) >= Math.abs(dy)) {
                        gestureMode = 1;
                    } else {
                        gestureMode = 2;
                        verticalLeft = downX < width / 2.0f;
                    }
                }
                if (gestureMode == 1) {
                    long duration = videoView.getDuration();
                    if (duration <= 0) return true;
                    float ratio = dx / (width * MobileUiTuning.SEEK_SENSITIVITY_SCREEN_RATIO);
                    long target = gestureBaseMs + (long) (duration * ratio);
                    if (target < 0) target = 0;
                    if (target > duration) target = duration;
                    gestureSeekMs = target;
                    showGestureHint(formatTime(target) + " / " + formatTime(duration));
                } else {
                    float ratio = -dy / (height * MobileUiTuning.VERTICAL_FULL_RANGE_SCREEN_RATIO);
                    if (verticalLeft) {
                        float b = clampF(startBrightness + ratio, MobileUiTuning.BRIGHTNESS_MIN, 1f);
                        setBrightness(b);
                        showGestureHint("亮度 " + Math.round(b * 100) + "%");
                    } else if (audioManager != null && maxVolume > 0) {
                        int v = (int) (startVolume + ratio * maxVolume);
                        if (v < 0) v = 0;
                        if (v > maxVolume) v = maxVolume;
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
                        showGestureHint("音量 " + v + "/" + maxVolume);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                long held = System.currentTimeMillis() - downTime;
                if (gestureMode == 1) {
                    videoView.seekTo(gestureSeekMs);
                } else if (gestureMode == 0 && held < MobileUiTuning.TAP_MAX_MS
                        && Math.abs(dx) < dp(MobileUiTuning.TAP_MAX_MOVE_DP)
                        && Math.abs(dy) < dp(MobileUiTuning.TAP_MAX_MOVE_DP)) {
                    long now = System.currentTimeMillis();
                    if (now - lastTapTime < MobileUiTuning.DOUBLE_TAP_MS) {
                        lastTapTime = 0;
                        if (videoView.isPlaying()) {
                            videoView.pause();
                            showGestureHint("已暂停");
                        } else {
                            videoView.resume();
                            showGestureHint("继续播放");
                        }
                    } else {
                        lastTapTime = now;
                        toggleOverlay();
                    }
                }
                gestureMode = 0;
                handler.removeCallbacks(hideHintTask);
                handler.postDelayed(hideHintTask, MobileUiTuning.HINT_AUTO_HIDE_MS);
                return true;
            }
        }
        return false;
    }

    private void showGestureHint(String text) {
        if (tvGestureHint == null) return;
        tvGestureHint.setText(text);
        tvGestureHint.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideHintTask);
    }

    private float currentBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (lp.screenBrightness > 0) return lp.screenBrightness;
        try {
            int sys = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
            return clampF(sys / 255f, 0.03f, 1f);
        } catch (Throwable th) {
            return 0.5f;
        }
    }

    private void setBrightness(float value) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = value;
        getWindow().setAttributes(lp);
    }

    private float clampF(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    /** 播放配置里的 st（片头跳过秒数），与 TV 端 mVodPlayerCfg.getInt("st") 同义 */
    private int parseSkipIntroSeconds(String playerCfg) {
        if (TextUtils.isEmpty(playerCfg)) return 0;
        try {
            return Math.max(0, new JSONObject(playerCfg).optInt("st", 0));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        videoView.pause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressTask);
        if (sourceViewModel != null) {
            sourceViewModel.playResult.removeObserver(playObserver);
        }
        videoView.release();
        super.onDestroy();
    }

    /* ===================== 画面比例：设置项 → 起播 ===================== */

    /** 起播时按设置项恢复比例（0 默认 / 1 16:9 / 2 4:3 / 3 铺满） */
    private void applySavedScale() {
        int idx = 0;
        try {
            idx = Hawk.get(HawkConfig.PLAY_SCALE, 0);
        } catch (Throwable ignored) {
        }
        applyScale(idx);
    }

    /** 应用一个比例档位，同步更新右下角的比例文字 */
    private void applyScale(int index) {
        scaleIndex = ((index % 4) + 4) % 4;
        int scaleType = scaleTypeOf(scaleIndex);
        if (videoView != null) videoView.setScreenScaleType(scaleType);
        if (tvScale != null) tvScale.setText(PlayerHelper.getScaleName(scaleType));
    }

    private static int scaleTypeOf(int index) {
        switch (index) {
            case 1:
                return VideoView.SCREEN_SCALE_16_9;
            case 2:
                return VideoView.SCREEN_SCALE_4_3;
            case 3:
                return VideoView.SCREEN_SCALE_MATCH_PARENT;
            default:
                return VideoView.SCREEN_SCALE_DEFAULT;
        }
    }
}
