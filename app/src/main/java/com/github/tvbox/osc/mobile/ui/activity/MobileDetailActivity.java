package com.github.tvbox.osc.mobile.ui.activity;

import com.github.tvbox.osc.mobile.ui.drawable.UiSkin;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.mobile.play.MobileParseHelper;
import com.github.tvbox.osc.mobile.ui.MobileCastHelper;
import com.github.tvbox.osc.mobile.ui.MobileDownloadHelper;
import com.github.tvbox.osc.mobile.ui.MobileFavorites;
import com.github.tvbox.osc.mobile.ui.MobilePlayHistory;
import com.github.tvbox.osc.mobile.ui.adapter.MobileSeriesAdapter;
import com.github.tvbox.osc.mobile.ui.view.MobileDockHelper;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;

import com.github.tvbox.osc.player.render.SurfaceRenderViewFactory;
import java.util.List;
import java.util.Locale;

import xyz.doikki.videoplayer.player.VideoView;

/**
 * 手机端详情页（对齐 iOS 26 Liquid Glass 竖屏卡片式详情）。
 *
 * 结构：顶部 16:9 播放器（本页直接播放） + 播放控制条 + 玻璃信息卡（封面/标题/元信息/简介）
 *      + 线路 chip + 选集网格（点集数在本页播放器里换集）。
 * 只有点「全屏」才跳横屏的 MobilePlayActivity，并把当前进度带过去续播。
 * 数据与解析链路沿用 TV 端：SourceViewModel.getPlay + MobileParseHelper。
 */
import com.github.tvbox.osc.util.SkinManager;

public class MobileDetailActivity extends BaseActivity {

    private ImageView ivThumb;
    private ImageView ivBigPlay;
    private TextView tvName;
    private TextView tvSubName;
    private TextView tvMeta;
    private TextView tvActor;
    private TextView tvDes;
    private TextView tvDesMore;
    private TextView tvPlay;
    private TextView tvEmpty;
    private TextView tvToolbarTitle;
    private TextView tvSeriesCount;
    private TextView tvInfoYear;
    private TextView tvInfoType;
    private TextView tvInfoArea;
    private TextView tvInfoNote;
    private TextView tvCurTime;
    private TextView tvDurTime;
    private TextView tvPause;
    private TextView tvRewind;
    private TextView tvForward;
    private TextView tvPrevEp;
    private TextView tvNextEp;
    private TextView tvScaleInline;
    private TextView tvSpeed;
    private TextView tvGestureHint;
    private ImageView ivCastTop;
    private ImageView ivPipTop;
    private ImageView ivFullTop;
    private ImageView ivPipBig;
    private View touchLayer;
    private View llCtrl;
    private ProgressBar pbLoading;
    private ProgressBar pbPlayerLoading;
    private LinearLayout llFlags;
    private FrameLayout playerWrap;
    private RecyclerView rvSeries;
    private SeekBar sbInline;
    private MyVideoView videoView;

    private SourceViewModel sourceViewModel;
    private MobileSeriesAdapter seriesAdapter;

    private VodInfo vodInfo;
    private String vodId;
    private String sourceKey;
    private String firstSourceKey;
    private boolean desExpanded = false;

    /** 当前播放地址与请求头（投屏 / 下载要用） */
    private String currentPlayUrl;
    private HashMap<String, String> currentHeaders;
    /** 是否已经真正开始过一次播放（用于主按钮的 播放/暂停 语义） */
    private boolean playingStarted = false;
    private boolean userSeeking = false;
    private long pendingSeek = 0;
    private long lastSavedPos = 0;
    /** 播放内核自动降级次数（参考原移动端 FongMiTV 的引擎切换，最多 2 次） */
    private int engineRetry = 0;

    /** 画面比例与倍速循环用的下标（v1.7.2） */
    private int scaleIndex = 0;
    private int speedIndex = 2;
    private static final float[] SPEED_VALUES = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    /** 手势状态：0 未定 / 1 进度 / 2 亮度 / 3 音量 */
    private float gDownX, gDownY;
    private int gMode = 0;
    private boolean gSeeking = false;
    private long gStartPos = 0;
    private float gStartBrightness = 0.5f;
    private int gStartVolume = 0;

    /** 从首页「继续观看」进入时的续播参数 */
    private boolean autoPlayFromHistory = false;
    private String histFlag;
    private int histIndex = 0;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTask = new Runnable() {
        @Override
        public void run() {
            long duration = videoView.getDuration();
            long position = videoView.getCurrentPosition();
            if (!userSeeking) {
                if (duration > 0) {
                    int pct = (int) (position * 100 / duration);
                    sbInline.setProgress(pct);
                    tvDurTime.setText(formatTime(duration));
                }
                tvCurTime.setText(formatTime(position));
            }
            // 每 5 秒落一次进度，用于退出后回到原位置
            if (position > 0 && Math.abs(position - lastSavedPos) >= 5000) {
                lastSavedPos = position;
                saveProgress(position);
            }
            uiHandler.postDelayed(this, 800);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_detail_mobile;
    }

    @Override
    public float getSizeInDp() {
        return 360;
    }

    /**
     * 给信息卡里的文字垫一层极淡白光（不是深色投影），
     * 目的是在"透出背景颜色"的玻璃上保住深色文字的可读性。
     */
    private void addTextGlow(View v) {
        try {
            if (v == null) return;
            if (v instanceof TextView) {
                ((TextView) v).setShadowLayer(4f, 0f, 1f, 0x66FFFFFF);
                return;
            }
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) addTextGlow(g.getChildAt(i));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 给详情页玻璃挂"真磨砂底"：把底图解码 + 模糊成一张整页尺寸的低分辨率位图，
     * 卡片/胶囊/圆钮/dock 内部显示的就是背景被虚化后的颜色。
     * 解码和模糊都在子线程做，结果回主线程 setBackground；任何异常都保留原有玻璃底。
     */

    /** 初始化阶段标记，异常时用于定位崩在哪一步 */
    private String stage = "start";

    @Override
    protected void init() {
        try {
            stage = "dock";
            com.github.tvbox.osc.mobile.ui.compose.MobileHomeDock.bindDockOnly(this, findViewById(R.id.cvDock), com.github.tvbox.osc.mobile.ui.compose.MobileHomeDock.TAB_VOD);
            stage = "bindViews";
            bindViews();
            stage = "glassCard";
            UiSkin.card(findViewById(R.id.infoCard));
            // v1.7.7：信息卡回到实色，不再需要给文字加外发光（原为 30% 透明底可读性补丁）
            // 立即播放胶囊 + 三个圆形图标按钮也换成液态玻璃
            TextView tvPlayGlass = findViewById(R.id.tvPlay);
            UiSkin.pill(tvPlayGlass);
            tvPlayGlass.setTextColor(0xFF1B1E24);
            View vDownload = findViewById(R.id.tvDownloadBig);
            UiSkin.circle(vDownload);
            View vFolder = findViewById(R.id.tvFolder);
            UiSkin.circle(vFolder);
            View vFavBig = findViewById(R.id.tvFavBig);
            UiSkin.circle(vFavBig);
            stage = "setupPlayer";
            setupPlayer();
            stage = "setupControls";
            setupControls();

            stage = "seriesAdapter";
            seriesAdapter = new MobileSeriesAdapter();
            rvSeries.setLayoutManager(new GridLayoutManager(this, 4));
            rvSeries.setAdapter(seriesAdapter);
            seriesAdapter.setOnItemClick(new MobileSeriesAdapter.OnItemClick() {
                @Override
                public void onItemClick(int position, VodInfo.VodSeries series) {
                    if (vodInfo == null || series == null) return;
                    vodInfo.playIndex = position;
                    markSeriesSelected(position);
                    startPlay();
                }
            });

            stage = "toolbar";
            findViewById(R.id.tvBack).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });

            tvDesMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    desExpanded = !desExpanded;
                    tvDes.setMaxLines(desExpanded ? Integer.MAX_VALUE : 3);
                    tvDesMore.setText(desExpanded ? getString(R.string.m_desc_less) : getString(R.string.m_desc_more));
                }
            });

            tvPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (vodInfo == null) return;
                    togglePlay();
                }
            });
            ivBigPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (vodInfo == null) return;
                    startPlay();
                }
            });

            tvName.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (vodInfo == null) return;
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    String content = "视频ID：" + vodId + "，图片地址：" + (vodInfo.pic == null ? "" : vodInfo.pic);
                    clipboard.setPrimaryClip(ClipData.newPlainText(null, content));
                    Toast.makeText(MobileDetailActivity.this, "已复制" + content, Toast.LENGTH_SHORT).show();
                }
            });

            stage = "intent";
            Bundle bundle = getIntent().getExtras();
            if (bundle != null) {
                vodId = bundle.getString("id", null);
                sourceKey = bundle.getString("sourceKey", "");
                firstSourceKey = sourceKey;
                String title = bundle.getString("title", "");
                if (!TextUtils.isEmpty(title)) tvToolbarTitle.setText(title);
                // 首页「继续观看」进来：带上次的线路与集数，数据到手后自动续播
                autoPlayFromHistory = bundle.getBoolean("autoPlay", false);
                histFlag = bundle.getString("histFlag", "");
                histIndex = bundle.getInt("histIndex", 0);
            }

            stage = "viewModel";
            initViewModel();
            if (!TextUtils.isEmpty(vodId)) {
                sourceViewModel.getDetail(sourceKey, vodId);
            } else {
                showEmptyState();
            }
            stage = "progressTimer";
            uiHandler.postDelayed(progressTask, 800);
            stage = "done";
        } catch (Throwable th) {
            // 不再让整页闪退：记录阶段 + 栈，页面留在原位方便看提示
            com.github.tvbox.osc.util.CrashLog.write(th, "detail-init/" + stage);
            if (pbLoading != null) {
                pbLoading.setVisibility(View.GONE);
            }
            Toast.makeText(this, "详情页初始化失败（" + stage + "）：" + th, Toast.LENGTH_LONG).show();
        }
    }

    /** 递归给信息卡里的所有文字加一层外发光：亮色主题用白色光晕，暗色主题用黑色 */
    private void applyTextShadow(View root) {
        if (root == null) return;
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int glow = night ? 0xCC000000 : 0xCCFFFFFF;
        if (root instanceof TextView) {
            ((TextView) root).setShadowLayer(4f, 0f, 1f, glow);
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                applyTextShadow(g.getChildAt(i));
            }
        }
    }

    private void bindViews() {
        ivThumb = findViewById(R.id.ivThumb);
        ivBigPlay = findViewById(R.id.ivBigPlay);
        tvName = findViewById(R.id.tvName);
        tvSubName = findViewById(R.id.tvSubName);
        tvMeta = findViewById(R.id.tvMeta);
        tvActor = findViewById(R.id.tvActor);
        tvDes = findViewById(R.id.tvDes);
        tvDesMore = findViewById(R.id.tvDesMore);
        tvPlay = findViewById(R.id.tvPlay);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);

        // 顶栏「收藏」按钮：写入本机收藏（首页「我的收藏」读这份数据）
        View favBtn = findViewById(R.id.tvFav);
        if (favBtn instanceof TextView) {
            ((TextView) favBtn).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleFavorite();
                }
            });
            updateFavView();
        }
        // 播放按钮行最右侧的收藏按钮（「打开下载文件」右边），与顶栏收藏同一份数据
        findViewById(R.id.tvFavBig).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFavorite();
            }
        });
        // 收藏右侧改为「画中画」按钮（原分享外链已按需求取消）
        View pipBig = findViewById(R.id.ivPipBig);
        if (pipBig != null) {
            UiSkin.circle(pipBig);
            pipBig.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    enterPipMode();
                }
            });
        }
        tvSeriesCount = findViewById(R.id.tvSeriesCount);
        tvInfoYear = findViewById(R.id.tvInfoYear);
        tvInfoType = findViewById(R.id.tvInfoType);
        tvInfoArea = findViewById(R.id.tvInfoArea);
        tvInfoNote = findViewById(R.id.tvInfoNote);
        tvCurTime = findViewById(R.id.tvCurTime);
        tvDurTime = findViewById(R.id.tvDurTime);
        tvPause = findViewById(R.id.tvPause);
        tvRewind = findViewById(R.id.tvRewind);
        tvForward = findViewById(R.id.tvForward);
        tvPrevEp = findViewById(R.id.tvPrevEp);
        tvNextEp = findViewById(R.id.tvNextEp);
        tvScaleInline = findViewById(R.id.tvScaleInline);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvGestureHint = findViewById(R.id.tvGestureHint);
        ivCastTop = findViewById(R.id.ivCastTop);
        ivPipTop = findViewById(R.id.ivPipTop);
        ivFullTop = findViewById(R.id.ivFullTop);
        ivPipBig = findViewById(R.id.ivPipBig);
        touchLayer = findViewById(R.id.touchLayer);
        llCtrl = findViewById(R.id.llCtrl);
        pbLoading = findViewById(R.id.pbLoading);
        pbPlayerLoading = findViewById(R.id.pbPlayerLoading);
        llFlags = findViewById(R.id.llFlags);
        playerWrap = findViewById(R.id.playerWrap);
        rvSeries = findViewById(R.id.rvSeries);
        sbInline = findViewById(R.id.sbInline);
        videoView = findViewById(R.id.videoView);
        findViewById(R.id.tvDownloadBig).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadCurrent();
            }
        });
        // 下载文件夹：查看已下载 / 已转码的视频
        findViewById(R.id.tvFolder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MobileDetailActivity.this, MobileDownloadsActivity.class));
            }
        });

        // 顶部播放器按 16:9 铺满屏宽（布局里给的是 220dp 占位高度）
        // 注意：用 ViewGroup.LayoutParams 取值改写，避免父容器类型变化时强转崩溃
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        android.view.ViewGroup.LayoutParams lp = playerWrap.getLayoutParams();
        if (lp != null) {
            lp.height = (int) (screenWidth * 9f / 16f);
            playerWrap.setLayoutParams(lp);
        }
    }

    private void setupPlayer() {
        // 播放器引擎按设置初始化（系统 / IJK / Exo，与 TV 端同一套 PlayerHelper）
        try {
            PlayerHelper.updateCfg(videoView, new JSONObject(), -1);
        } catch (Throwable ignored) {
        }
        // 详情页顶部播放器强制走 SurfaceView：
        // TextureView 的内容要参与 hwui 合成，叠在本页玻璃卡片/大背景图上时，
        // 骁龙 845 (Adreno 630) 实测直接崩在 libGLESv2_adreno.so 的
        // GrGLGpu::sendMeshToGpu，进程被杀 → 表现为「播放器没画面、退回首页」。
        // SurfaceView 是独立图层，不参与这层合成，能绕开该崩溃。
        try {
            videoView.setRenderViewFactory(SurfaceRenderViewFactory.create());
        } catch (Throwable ignored) {
        }
        videoView.setProgressManager(null);
        videoView.setOnStateChangeListener(new VideoView.OnStateChangeListener() {
            @Override
            public void onPlayerStateChanged(int playerState) {
            }

            @Override
            public void onPlayStateChanged(int playState) {
                // 播放状态可能从内核/解析线程回调，统一投递回主线程再改 UI
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    runOnUiThread(() -> onPlayStateChanged(playState));
                    return;
                }
                switch (playState) {
                    case VideoView.STATE_PREPARED:
                        // 续播：上一轮看到的位置（或全屏页带回来的位置）
                        if (pendingSeek > 0) {
                            long target = pendingSeek;
                            pendingSeek = 0;
                            videoView.seekTo(target);
                        }
                        break;
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        pbPlayerLoading.setVisibility(View.VISIBLE);
                        break;
                    case VideoView.STATE_PLAYING:
                    case VideoView.STATE_BUFFERED:
                        pbPlayerLoading.setVisibility(View.GONE);
                        ivBigPlay.setVisibility(View.GONE);
                        tvPause.setText(R.string.m_pause);
                        break;
                    case VideoView.STATE_PAUSED:
                        tvPause.setText(R.string.m_play);
                        break;
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        pbPlayerLoading.setVisibility(View.GONE);
                        playNextIfAny();
                        break;
                    case VideoView.STATE_ERROR:
                        pbPlayerLoading.setVisibility(View.GONE);
                        // 参考原移动端（FongMiTV）的引擎策略：本页播放失败就换一个内核再试，
                        // 系统 → IJK → Exo，最多降级两次，避免一直黑屏
                        if (engineRetry < 2 && currentPlayUrl != null) {
                            engineRetry++;
                            int next = nextEngine();
                            try {
                                PlayerHelper.updateCfg(videoView, new JSONObject(), next);
                            } catch (Throwable ignored) {
                            }
                            videoView.release();
                            videoView.setUrl(currentPlayUrl, currentHeaders);
                            videoView.start();
                            pbPlayerLoading.setVisibility(View.VISIBLE);
                            Toast.makeText(MobileDetailActivity.this,
                                    "播放失败，已切换 " + PlayerHelper.getPlayerName(next) + " 内核重试",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ivBigPlay.setVisibility(View.VISIBLE);
                        Toast.makeText(MobileDetailActivity.this,
                                "播放失败，换条线路或换个集数再试", Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void setupControls() {
        tvPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!playingStarted) {
                    startPlay();
                    return;
                }
                if (videoView.isPlaying()) {
                    videoView.pause();
                } else {
                    videoView.resume();
                }
            }
        });
        // 快退 / 快进（-10s / +10s）
        tvRewind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekBy(-10000);
            }
        });
        tvForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekBy(10000);
            }
        });
        // 上一集 / 下一集
        tvPrevEp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchEpisode(-1);
            }
        });
        tvNextEp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchEpisode(1);
            }
        });
        // 画面比例
        tvScaleInline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleScale();
            }
        });
        // 倍速（含慢速 0.5x）
        tvSpeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleSpeed();
            }
        });
        // 右上角三个工具
        ivFullTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFullScreen();
            }
        });
        ivCastTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPlayUrl == null) {
                    Toast.makeText(MobileDetailActivity.this, "还没拿到播放地址，先点播放", Toast.LENGTH_SHORT).show();
                    return;
                }
                MobileCastHelper.cast(MobileDetailActivity.this, tvName.getText().toString(), currentPlayUrl);
            }
        });
        ivPipTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterPipMode();
            }
        });
        touchLayer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                return onPlayerTouch(e);
            }
        });
        sbInline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
                    long target = (long) (duration * seekBar.getProgress() / 100.0);
                    videoView.seekTo(target);
                    saveProgress(target);
                }
            }
        });
    }

    /** 相对当前位置跳转（快退/快进按钮用） */
    private void seekBy(long deltaMs) {
        if (!playingStarted || currentPlayUrl == null) {
            startPlay();
            return;
        }
        long duration = videoView.getDuration();
        long target = videoView.getCurrentPosition() + deltaMs;
        if (target < 0) target = 0;
        if (duration > 0 && target > duration - 1000) target = duration - 1000;
        videoView.seekTo(target);
        saveProgress(target);
    }

    /** 上一集 / 下一集：delta = -1 / +1，边界处给提示 */
    private void switchEpisode(int delta) {
        if (vodInfo == null) return;
        List<VodInfo.VodSeries> list = vodInfo.seriesMap == null ? null : vodInfo.seriesMap.get(vodInfo.playFlag);
        if (list == null || list.isEmpty()) return;
        int index = vodInfo.getplayIndex() + delta;
        if (index < 0) {
            Toast.makeText(this, "已经是第一集", Toast.LENGTH_SHORT).show();
            return;
        }
        if (index >= list.size()) {
            Toast.makeText(this, "已经是最后一集", Toast.LENGTH_SHORT).show();
            return;
        }
        vodInfo.playIndex = index;
        markSeriesSelected(index);
        startPlay();
        if (rvSeries != null) {
            try {
                rvSeries.scrollToPosition(index);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 画面比例循环：自适应 → 16:9 → 4:3 → 铺满 */
    private void cycleScale() {
        scaleIndex = (scaleIndex + 1) % 4;
        int scaleType = scaleIndex == 0 ? VideoView.SCREEN_SCALE_DEFAULT
                : scaleIndex == 1 ? VideoView.SCREEN_SCALE_16_9
                : scaleIndex == 2 ? VideoView.SCREEN_SCALE_4_3
                : VideoView.SCREEN_SCALE_MATCH_PARENT;
        videoView.setScreenScaleType(scaleType);
        tvScaleInline.setText(PlayerHelper.getScaleName(scaleType));
    }

    /** 倍速循环：0.5 / 0.75 / 1.0 / 1.25 / 1.5 / 2.0 */
    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEED_VALUES.length;
        float speed = SPEED_VALUES[speedIndex];
        try {
            videoView.getMediaPlayer().setSpeed(speed);
        } catch (Throwable th) {
            Toast.makeText(this, "当前内核不支持改倍速", Toast.LENGTH_SHORT).show();
            return;
        }
        tvSpeed.setText(formatSpeed(speed));
    }

    private String formatSpeed(float speed) {
        return new java.text.DecimalFormat("0.##").format(speed) + "x";
    }

    /**
     * 播放器手势（详情页内嵌播放器）：单击显隐控制条、横滑调进度、
     * 左半屏上下滑调亮度、右半屏上下滑调音量。
     */
    private boolean onPlayerTouch(android.view.MotionEvent e) {
        switch (e.getAction()) {
            case android.view.MotionEvent.ACTION_DOWN:
                gDownX = e.getX();
                gDownY = e.getY();
                gMode = 0;
                gSeeking = false;
                gStartPos = videoView.getCurrentPosition();
                gStartBrightness = curBrightness();
                gStartVolume = curVolume();
                return true;
            case android.view.MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - gDownX;
                float dy = e.getY() - gDownY;
                if (gMode == 0) {
                    if (Math.abs(dx) > 24 && Math.abs(dx) > Math.abs(dy)) {
                        gMode = 1; // 进度
                    } else if (Math.abs(dy) > 24) {
                        gMode = (gDownX < playerWrap.getWidth() / 2f) ? 2 : 3; // 2 亮度 / 3 音量
                    }
                    return true;
                }
                if (gMode == 1) {
                    gSeeking = true;
                    float ratio = dx / playerWrap.getWidth();
                    long duration = videoView.getDuration();
                    long target = (long) (gStartPos + ratio * Math.max(1, duration));
                    if (target < 0) target = 0;
                    if (duration > 0 && target > duration) target = duration;
                    showGestureHint(timeText(target) + " / " + timeText(duration));
                } else if (gMode == 2 || gMode == 3) {
                    float delta = -dy / (playerWrap.getHeight() * 1.2f);
                    if (gMode == 2) {
                        float v = clamp01(gStartBrightness + delta);
                        setBrightness(v);
                        showGestureHint("亮度 " + Math.round(v * 100) + "%");
                    } else {
                        int max = audioManager().getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int target = Math.round(gStartVolume + delta * max);
                        if (target < 0) target = 0;
                        if (target > max) target = max;
                        audioManager().setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
                        showGestureHint("音量 " + Math.round(target * 100f / Math.max(1, max)) + "%");
                    }
                }
                return true;
            }
            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL: {
                if (gMode == 1) {
                    float dx = e.getX() - gDownX;
                    float ratio = dx / playerWrap.getWidth();
                    long duration = videoView.getDuration();
                    long target = (long) (gStartPos + ratio * Math.max(1, duration));
                    if (target < 0) target = 0;
                    if (duration > 0 && target > duration) target = duration;
                    videoView.seekTo(target);
                    saveProgress(target);
                    gSeeking = false;
                    hideGestureHintSoon();
                } else if (gMode == 0) {
                    // 单击：显隐控制条
                    boolean visible = llCtrl.getVisibility() == View.VISIBLE;
                    llCtrl.setVisibility(visible ? View.GONE : View.VISIBLE);
                    View top = findViewById(R.id.llPlayerTop);
                    if (top != null) top.setVisibility(visible ? View.GONE : View.VISIBLE);
                    ivBigPlay.setVisibility(visible && playingStarted ? View.GONE : (playingStarted ? View.GONE : View.VISIBLE));
                } else {
                    hideGestureHintSoon();
                }
                gMode = 0;
                return true;
            }
            default:
                return false;
        }
    }

    private float curBrightness() {
        try {
            float v = getWindow().getAttributes().screenBrightness;
            if (v < 0) {
                v = android.provider.Settings.System.getInt(getContentResolver(),
                        android.provider.Settings.System.SCREEN_BRIGHTNESS, 128) / 255f;
            }
            return clamp01(v);
        } catch (Throwable th) {
            return 0.5f;
        }
    }

    private void setBrightness(float v) {
        try {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = clamp01(v);
            getWindow().setAttributes(lp);
        } catch (Throwable ignored) {
        }
    }

    private float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private AudioManager audioManager() {
        return (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    private int curVolume() {
        try {
            return audioManager().getStreamVolume(AudioManager.STREAM_MUSIC);
        } catch (Throwable th) {
            return 0;
        }
    }

    private void showGestureHint(String text) {
        if (tvGestureHint == null) return;
        tvGestureHint.setText(text);
        tvGestureHint.setVisibility(View.VISIBLE);
        uiHandler.removeCallbacks(hideHintTask);
        uiHandler.postDelayed(hideHintTask, 900);
    }

    private void hideGestureHintSoon() {
        uiHandler.removeCallbacks(hideHintTask);
        uiHandler.postDelayed(hideHintTask, 600);
    }

    private final Runnable hideHintTask = new Runnable() {
        @Override
        public void run() {
            if (tvGestureHint != null) tvGestureHint.setVisibility(View.GONE);
        }
    };

    private String timeText(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format(Locale.CHINA, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.CHINA, "%02d:%02d", m, s);
    }

    /** 画中画：缩小成悬浮小窗继续播（Android 8.0+，清单里已声明 supportsPictureInPicture） */
    private void enterPipMode() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            Toast.makeText(this, "当前系统不支持画中画（需 Android 8.0 及以上）", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (!playingStarted || currentPlayUrl == null) {
                Toast.makeText(this, "先播放再小窗", Toast.LENGTH_SHORT).show();
                return;
            }
            android.app.PictureInPictureParams.Builder b = new android.app.PictureInPictureParams.Builder();
            // 本页是竖屏锁定的，不强行指定 16:9 窗口比例（指定了会被系统拒绝）
            enterPictureInPictureMode(b.build());
        } catch (Throwable th) {
            Toast.makeText(this, "进入画中画失败：" + th, Toast.LENGTH_SHORT).show();
        }
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.detailResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                try {
                    if (absXml == null || absXml.movie == null
                            || absXml.movie.videoList == null || absXml.movie.videoList.isEmpty()) {
                        showEmptyState();
                        return;
                    }
                    if (!TextUtils.isEmpty(absXml.msg) && !absXml.msg.equals("数据列表")) {
                        Toast.makeText(MobileDetailActivity.this, absXml.msg, Toast.LENGTH_SHORT).show();
                        showEmptyState();
                        return;
                    }
                    Movie.Video video = absXml.movie.videoList.get(0);
                    video.id = vodId;
                    vodInfo = new VodInfo();
                    vodInfo.setVideo(video);
                    vodInfo.sourceKey = video.sourceKey;
                    sourceKey = video.sourceKey;
                    render(video);
                    updateFavView();
                    startFromHistoryIfNeeded();
                } catch (Throwable th) {
                    com.github.tvbox.osc.util.CrashLog.write(th, "detail-observe");
                    Toast.makeText(MobileDetailActivity.this,
                            "详情渲染失败：" + th, Toast.LENGTH_LONG).show();
                }
            }
        });
        sourceViewModel.playResult.observe(this, playObserver);
    }

    private final Observer<JSONObject> playObserver = new Observer<JSONObject>() {
        @Override
        public void onChanged(JSONObject info) {
            if (info == null) {
                pbPlayerLoading.setVisibility(View.GONE);
                Toast.makeText(MobileDetailActivity.this, "获取播放信息失败", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean parse = info.optString("parse", "1").equals("1");
            boolean jx = info.optString("jx", "0").equals("1");
            String playUrl = info.optString("playUrl", "");
            String msg = info.optString("msg", "");
            if (!TextUtils.isEmpty(msg)) {
                Toast.makeText(MobileDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
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
                pbPlayerLoading.setVisibility(View.VISIBLE);
                MobileParseHelper.parse(MobileDetailActivity.this, jx, playUrl, url, new MobileParseHelper.Callback() {
                    @Override
                    public void onParsed(String realUrl, HashMap<String, String> parsedHeaders) {
                        // 嗅探成功回调来自 WebView 的 IO 线程，必须切主线程才能动视图与播放器
                        runOnUiThread(() -> {
                            pbPlayerLoading.setVisibility(View.GONE);
                            playUrl(realUrl, parsedHeaders);
                        });
                    }

                    @Override
                    public void onFail(String reason) {
                        runOnUiThread(() -> {
                            pbPlayerLoading.setVisibility(View.GONE);
                            ivBigPlay.setVisibility(View.VISIBLE);
                            Toast.makeText(MobileDetailActivity.this,
                                    "解析失败：" + reason + "，换条线路再试", Toast.LENGTH_LONG).show();
                        });
                    }
                });
                return;
            }
            playUrl(playUrl + url, headers);
        }
    };

    /** 下一个要尝试的播放内核：系统(0) → IJK(1) → Exo(2) */
    private int nextEngine() {
        int current = Hawk.get(HawkConfig.PLAY_TYPE, 0);
        if (current == 1) return 2;
        if (current == 2) return 1;
        return 1;
    }

    private void playUrl(String url, HashMap<String, String> headers) {
        // 兜底：无论从哪个线程发起，播放器操作都回到主线程
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> playUrl(url, headers));
            return;
        }
        if (TextUtils.isEmpty(url) || "null".equals(url)) {
            pbPlayerLoading.setVisibility(View.GONE);
            ivBigPlay.setVisibility(View.VISIBLE);
            Toast.makeText(this, "播放地址为空", Toast.LENGTH_SHORT).show();
            return;
        }
        currentPlayUrl = url;
        currentHeaders = headers;
        // 先释放再设置地址：连续点集数时避免旧内核还占着 Surface 导致黑屏
        videoView.release();
        videoView.setUrl(url, headers);
        videoView.start();
    }

    private VodInfo.VodSeries currentSeries() {
        if (vodInfo == null || vodInfo.seriesMap == null) return null;
        List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (list == null || list.isEmpty()) return null;
        int index = vodInfo.getplayIndex();
        if (index < 0 || index >= list.size()) index = 0;
        return list.get(index);
    }

    private void togglePlay() {
        if (!playingStarted || currentPlayUrl == null) {
            startPlay();
            return;
        }
        if (videoView.isPlaying()) {
            videoView.pause();
        } else {
            videoView.resume();
        }
    }

    /** 本页播放：换集/首次播放都走这里 */
    private void startPlay() {
        VodInfo.VodSeries series = currentSeries();
        if (series == null) {
            Toast.makeText(this, "没有可播放的剧集", Toast.LENGTH_SHORT).show();
            return;
        }
        playingStarted = true;
        engineRetry = 0;
        ivBigPlay.setVisibility(View.GONE);
        pbPlayerLoading.setVisibility(View.VISIBLE);
        long saved = Hawk.get(progressKey(), 0L);
        pendingSeek = saved > 3000 ? saved : 0;
        lastSavedPos = pendingSeek;
        updatePlayTitle(series);
        recordHistory(series, pendingSeek);
        sourceViewModel.getPlay(sourceKey, vodInfo.playFlag, null, series.url, null);
    }

    private void playNextIfAny() {
        if (vodInfo == null) return;
        List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (list == null) return;
        int index = vodInfo.getplayIndex();
        if (index + 1 < list.size()) {
            vodInfo.playIndex = index + 1;
            markSeriesSelected(vodInfo.playIndex);
            startPlay();
        }
    }

    private void updatePlayTitle(VodInfo.VodSeries series) {
        String name = series.name;
        tvToolbarTitle.setText((vodInfo.name == null ? "" : vodInfo.name)
                + (TextUtils.isEmpty(name) ? "" : (" · " + name)));
    }

    private String progressKey() {
        if (vodInfo == null) return "inline_prog_none";
        return "inline_prog_" + vodId + "_" + vodInfo.playFlag + "_" + vodInfo.getplayIndex();
    }

    private void saveProgress(long position) {
        if (vodInfo == null || position < 3000) return;
        Hawk.put(progressKey(), position);
        updateHistoryProgress(position);
    }

    /** 首页「继续观看」进入后：切到记录的线路 / 集数并自动开始播放 */
    private void startFromHistoryIfNeeded() {
        if (!autoPlayFromHistory || vodInfo == null) return;
        autoPlayFromHistory = false;
        try {
            if (!TextUtils.isEmpty(histFlag) && vodInfo.seriesMap != null
                    && vodInfo.seriesMap.containsKey(histFlag)) {
                vodInfo.playFlag = histFlag;
                if (vodInfo.seriesFlags != null) {
                    for (VodInfo.VodSeriesFlag flag : vodInfo.seriesFlags) {
                        flag.selected = flag.name.equals(histFlag);
                    }
                }
                buildFlags();
                refreshSeries();
            }
            List<VodInfo.VodSeries> list = vodInfo.seriesMap == null
                    ? null : vodInfo.seriesMap.get(vodInfo.playFlag);
            if (list != null && histIndex >= 0 && histIndex < list.size()) {
                vodInfo.playIndex = histIndex;
                markSeriesSelected(histIndex);
            }
            startPlay();
        } catch (Throwable th) {
            com.github.tvbox.osc.util.CrashLog.write(th, "detail-history");
        }
    }

    /** 记一条「继续观看」，点开即记录，不依赖播放是否成功 */
    private void recordHistory(VodInfo.VodSeries series, long progress) {
        if (vodInfo == null) return;
        try {
            MobilePlayHistory.Item item = new MobilePlayHistory.Item();
            item.vodId = vodId == null ? "" : vodId;
            item.sourceKey = sourceKey == null ? "" : sourceKey;
            item.name = vodInfo.name == null ? "" : vodInfo.name;
            item.pic = vodInfo.pic == null ? "" : vodInfo.pic;
            item.flag = vodInfo.playFlag == null ? "" : vodInfo.playFlag;
            item.seriesName = series == null || series.name == null ? "" : series.name;
            item.playIndex = vodInfo.getplayIndex();
            item.progress = progress;
            item.duration = videoView.getDuration();
            MobilePlayHistory.record(item);
        } catch (Throwable ignored) {
        }
    }

    /** 播放中定期刷新进度，首页历史卡才能显示「第 N 集 · 45%」 */
    private void updateHistoryProgress(long position) {
        recordHistory(currentSeries(), position);
    }

    private void downloadCurrent() {
        String title = tvName.getText().toString();
        MobileDownloadHelper.start(this, title, currentPlayUrl, currentHeaders);
    }

    private void openFullScreen() {
        if (vodInfo == null) return;
        long position = videoView.getCurrentPosition();
        saveProgress(position);
        if (videoView.isPlaying()) videoView.pause();
        Bundle bundle = new Bundle();
        bundle.putString("sourceKey", sourceKey);
        bundle.putSerializable("VodInfo", vodInfo);
        bundle.putLong("seekTo", position);
        Intent intent = new Intent(this, MobilePlayActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    private void render(Movie.Video video) {
        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
        tvName.setText(TextUtils.isEmpty(video.name) ? tr("片名", "Untitled") : video.name);
        if (!TextUtils.isEmpty(video.name)) tvToolbarTitle.setText(video.name);

        StringBuilder meta = new StringBuilder();
        if (video.year != 0) meta.append(video.year);
        appendPart(meta, video.area);
        appendPart(meta, video.type);
        appendPart(meta, video.note);
        tvMeta.setText(meta.toString());

        String actor = strip(video.actor);
        tvActor.setText(TextUtils.isEmpty(actor) ? "" : tr("主演：", "Cast: ") + actor);
        tvActor.setVisibility(TextUtils.isEmpty(actor) ? View.GONE : View.VISIBLE);

        String des = strip(video.des);
        tvDes.setText(des);
        tvDesMore.setVisibility(des.length() > 70 ? View.VISIBLE : View.GONE);

        if (!TextUtils.isEmpty(video.pic)) {
            ImgUtil.load(video.pic, ivThumb, 10);
        } else {
            ivThumb.setImageResource(R.drawable.img_loading_placeholder);
        }

        tvInfoYear.setText(video.year != 0 ? String.valueOf(video.year) : "—");
        tvInfoType.setText(TextUtils.isEmpty(video.type) ? "—" : video.type);
        tvInfoArea.setText(TextUtils.isEmpty(video.area) ? "—" : video.area);
        tvInfoNote.setText(TextUtils.isEmpty(video.note) ? "—" : video.note);

        if (vodInfo.seriesFlags != null && !vodInfo.seriesFlags.isEmpty()) {
            if (vodInfo.playFlag == null) {
                vodInfo.playFlag = vodInfo.seriesFlags.get(0).name;
                vodInfo.seriesFlags.get(0).selected = true;
            }
            buildFlags();
        }
        refreshSeries();
    }

    private void buildFlags() {
        llFlags.removeAllViews();
        if (vodInfo.seriesFlags == null || vodInfo.seriesFlags.size() <= 1) {
            findViewById(R.id.svFlags).setVisibility(View.GONE);
            return;
        }
        findViewById(R.id.svFlags).setVisibility(View.VISIBLE);
        for (final VodInfo.VodSeriesFlag flag : vodInfo.seriesFlags) {
            TextView chip = new TextView(this);
            chip.setText(flag.name);
            chip.setTextSize(12);
            int h = dp(30);
            chip.setPadding(dp(12), 0, dp(12), 0);
            chip.setHeight(h);
            chip.setGravity(android.view.Gravity.CENTER);
            boolean chipOn = flag.selected || flag.name.equals(vodInfo.playFlag);
            chip.setSelected(chipOn);
            // 液态玻璃 chip：选中态走蓝色玻璃，未选中走透明白玻璃
            UiSkin.chip(chip, chipOn);
            chip.setTextColor(chipOn ? SkinManager.onAccent(this)
                    : com.github.tvbox.osc.util.SkinManager.text2(this));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchFlag(flag.name);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, h);
            lp.setMargins(0, 0, dp(8), 0);
            llFlags.addView(chip, lp);
        }
    }

    private void switchFlag(String flagName) {
        if (vodInfo.seriesFlags != null) {
            for (VodInfo.VodSeriesFlag flag : vodInfo.seriesFlags) {
                flag.selected = flag.name.equals(flagName);
            }
        }
        vodInfo.playFlag = flagName;
        vodInfo.playIndex = 0;
        buildFlags();
        refreshSeries();
        // 换线路后原地址失效，下次点播放/点集数重新解析
        currentPlayUrl = null;
        currentHeaders = null;
        playingStarted = false;
    }

    private void refreshSeries() {
        if (vodInfo == null || vodInfo.seriesMap == null || vodInfo.seriesMap.isEmpty()) {
            showEmptyState();
            return;
        }
        List<VodInfo.VodSeries> series = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (series == null || series.isEmpty()) {
            showEmptyState();
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        rvSeries.setVisibility(View.VISIBLE);
        tvSeriesCount.setText(tr("共 ", "Total ") + series.size() + tr(" 集", " eps"));
        markSeriesSelected(vodInfo.getplayIndex());
    }

    private void markSeriesSelected(int index) {
        List<VodInfo.VodSeries> series = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (series == null) return;
        for (int i = 0; i < series.size(); i++) {
            series.get(i).selected = (i == index);
        }
        seriesAdapter.setData(series);
        if (index >= 0 && index < series.size()) {
            rvSeries.scrollToPosition(index);
        }
    }

    private void showEmptyState() {
        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
        rvSeries.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
        tvSeriesCount.setText("");
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private void appendPart(StringBuilder sb, String part) {
        if (TextUtils.isEmpty(part)) return;
        if (sb.length() > 0) sb.append(" · ");
        sb.append(part);
    }

    private String strip(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\<.*?\\>", "").replaceAll("\\s", " ").trim();
    }

    /** 中英双语（手机端界面文案） */
    private String tr(String zh, String en) {
        return com.github.tvbox.osc.setting.CommonSetting.getLanguage() == 1 ? en : zh;
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null) {
            saveProgress(videoView.getCurrentPosition());
            if (videoView.isPlaying()) videoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (uiHandler != null) uiHandler.removeCallbacksAndMessages(null);
        if (sourceViewModel != null) {
            sourceViewModel.playResult.removeObserver(playObserver);
        }
        if (videoView != null) videoView.release();
        super.onDestroy();
    }

    /** 收藏 / 取消收藏当前影片，并同步刷新顶栏与按钮行的星标 */
    private void toggleFavorite() {
        MobileFavorites.Item it = new MobileFavorites.Item();
        it.vodId = vodId == null ? "" : vodId;
        it.sourceKey = sourceKey == null ? "" : sourceKey;
        // 名字优先用影片名，避免把「片名 · 第3集」这种播放标题存进收藏
        String name = vodInfo == null ? null : vodInfo.name;
        if (TextUtils.isEmpty(name) && tvToolbarTitle != null) {
            name = tvToolbarTitle.getText().toString();
        }
        it.name = name == null ? "" : name;
        // 封面跟着影片走，首页「我的收藏」卡才有图可显示（原来固定写空串，卡片只能是灰底占位）
        it.pic = (vodInfo == null || vodInfo.pic == null) ? "" : vodInfo.pic;
        String flag = vodInfo == null ? null : vodInfo.playFlag;
        it.note = TextUtils.isEmpty(flag) ? "已收藏" : ("已收藏 · " + flag);
        boolean added = MobileFavorites.toggle(it);
        updateFavView();
        Toast.makeText(MobileDetailActivity.this,
                added ? "已加入收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
    }

    /** 收藏按钮状态：★ 已收藏 / ☆ 未收藏（顶栏文字按钮与右侧星标按钮同时更新） */
    private void updateFavView() {
        boolean has = MobileFavorites.has(vodId == null ? "" : vodId, sourceKey == null ? "" : sourceKey);
        View v = findViewById(R.id.tvFav);
        if (v instanceof TextView) {
            ((TextView) v).setText(has ? "★ 已收藏" : "☆ 收藏");
        }
        View big = findViewById(R.id.tvFavBig);
        if (big instanceof android.widget.ImageView) {
            // 星标是实心图形：已收藏给金色 + 全不透明，未收藏压到半透明，避免两者看不出区别
            ((android.widget.ImageView) big).setColorFilter(has
                    ? 0xFFFFD54F : com.github.tvbox.osc.util.SkinManager.text1(this));
            big.setAlpha(has ? 1f : 0.55f);
        }
    }
}