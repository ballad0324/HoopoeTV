package com.github.tvbox.osc.mobile.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.mobile.ui.MobileCastHelper;
import com.github.tvbox.osc.mobile.ui.MobileDownloadHelper;
import com.github.tvbox.osc.mobile.ui.MobileFavorites;
import com.github.tvbox.osc.util.PlayerHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import xyz.doikki.videoplayer.player.VideoView;

/**
 * 手机端直播播放页（横屏）：上/下一台、画面比例、带 livePlayHeaders 的播放。
 * 播放器配置复用 LivePlayerManager（与 TV 端同一套 Hawk 里的直播内核/渲染/比例偏好）。
 */
public class MobileLivePlayerActivity extends BaseActivity {

    private MyVideoView videoView;
    private ProgressBar pbLoading;
    private TextView tvTitle;
    private TextView tvGroup;
    private TextView tvPlayPause;
    private TextView tvScale;
    private ImageView ivFavLive;
    private ImageView ivDlLive;
    private ImageView liveBackdrop;
    private TextView tvCastLive;
    private static final int LIVE_RECORD_SECONDS = 60;
    /** 直播频道在全局收藏里的 sourceKey 标记（首页收藏夹据此跳直播播放页） */
    public static final String LIVE_SOURCE_KEY = "live";

    private final LivePlayerManager livePlayerManager = new LivePlayerManager();
    private List<LiveChannelItem> channels = new ArrayList<>();
    private LiveChannelGroup group;
    private int channelIndex = 0;
    private int scaleIndex = 0;
    /** 回看直链模式（节目单页点节目进来）：非空时跳过频道列表，直接播这一档 */
    private String directUrl = "";
    private String directTitle = "";
    /** 错误后是否已经自动重试过，避免死循环 */
    private boolean retried = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_play_mobile;
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
        tvTitle = findViewById(R.id.tvTitle);
        tvGroup = findViewById(R.id.tvGroup);
        tvPlayPause = findViewById(R.id.tvPlayPause);
        tvScale = findViewById(R.id.tvScale);
        ivFavLive = findViewById(R.id.ivFavLive);
        ivDlLive = findViewById(R.id.ivDlLive);
        liveBackdrop = findViewById(R.id.liveBackdrop);
        tvCastLive = findViewById(R.id.tvCastLive);

        Bundle bundle = getIntent() != null ? getIntent().getExtras() : null;
        int groupIndex = bundle != null ? bundle.getInt("groupIndex", 0) : 0;
        channelIndex = bundle != null ? bundle.getInt("channelIndex", 0) : 0;
        // 回看直链模式：节目单页点某档节目，直接带着拼好的回看地址进来
        directUrl = bundle != null ? bundle.getString("directUrl", "") : "";
        directTitle = bundle != null ? bundle.getString("directTitle", "") : "";

        if (TextUtils.isEmpty(directUrl)) {
            List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
            if (groups == null || groups.isEmpty() || groupIndex >= groups.size()) {
                Toast.makeText(this, "直播频道为空", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            group = groups.get(groupIndex);
            channels = group.getLiveChannels();
            if (channels == null || channels.isEmpty()) {
                Toast.makeText(this, "该分类没有频道", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (channelIndex >= channels.size()) channelIndex = 0;
        }

        findViewById(R.id.tvBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.tvPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepChannel(-1);
            }
        });
        findViewById(R.id.tvNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepChannel(1);
            }
        });
        tvPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoView.isPlaying()) {
                    videoView.pause();
                    tvPlayPause.setText(R.string.m_play);
                } else {
                    videoView.resume();
                    tvPlayPause.setText(R.string.m_pause);
                }
            }
        });
        if (ivFavLive != null) {
            ivFavLive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleChannelFavorite();
                }
            });
        }
        if (ivDlLive != null) {
            ivDlLive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadCurrentLive();
                }
            });
        }
        tvScale.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scaleIndex = (scaleIndex + 1) % 4;
                int scaleType = scaleIndex == 0 ? VideoView.SCREEN_SCALE_DEFAULT
                        : scaleIndex == 1 ? VideoView.SCREEN_SCALE_16_9
                        : scaleIndex == 2 ? VideoView.SCREEN_SCALE_4_3
                        : VideoView.SCREEN_SCALE_MATCH_PARENT;
                videoView.setScreenScaleType(scaleType);
                tvScale.setText(PlayerHelper.getScaleName(scaleType));
            }
        });
        // v1.7.1：右下角「投屏」，把当前频道地址推给同网 DLNA 电视
        if (tvCastLive != null) {
            tvCastLive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String url = currentChannelUrl();
                    if (TextUtils.isEmpty(url)) {
                        Toast.makeText(MobileLivePlayerActivity.this,
                                "还没有拿到频道地址，等画面出来再投屏", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    MobileCastHelper.cast(MobileLivePlayerActivity.this, currentChannelName(), url);
                }
            });
        }

        livePlayerManager.init(videoView);
        // 直播不需要进度管理（TV 端同样置空），否则会干扰内核的 seek/prepare 流程
        videoView.setProgressManager(null);
        // 关键：直播黑屏时没有任何反馈，这里把加载圈与错误提示接到播放器状态上
        videoView.setOnStateChangeListener(new VideoView.OnStateChangeListener() {
            @Override
            public void onPlayerStateChanged(int playerState) {
            }

            @Override
            public void onPlayStateChanged(int playState) {
                switch (playState) {
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        pbLoading.setVisibility(View.VISIBLE);
                        setBackdropVisible(true);
                        break;
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PLAYING:
                        pbLoading.setVisibility(View.GONE);
                        setBackdropVisible(false);
                        retried = false;
                        break;
                    case VideoView.STATE_ERROR:
                        pbLoading.setVisibility(View.GONE);
                        setBackdropVisible(true);
                        onLiveError();
                        break;
                    default:
                        break;
                }
            }
        });
        tvScale.setText(PlayerHelper.getScaleName(VideoView.SCREEN_SCALE_DEFAULT));
        updateFavMark();
        if (!TextUtils.isEmpty(directUrl)) {
            tvTitle.setText(directTitle);
            tvGroup.setText("节目回看");
            pbLoading.setVisibility(View.VISIBLE);
            livePlayerManager.getLiveChannelPlayer(videoView, directTitle);
            videoView.release();
            videoView.setUrl(directUrl, setPlayHeaders(directUrl));
            videoView.start();
            tvPlayPause.setText(R.string.m_pause);
        } else {
            play();
        }
    }

    // ---------------------------------------------------------------- 收藏 / 下载（v1.6.25）

    /** 待播底图显隐：出画面就藏起来，加载/出错时露出来 */
    private void setBackdropVisible(boolean visible) {
        if (liveBackdrop == null) return;
        liveBackdrop.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** 频道唯一键，与 MobileLiveActivity 完全一致：名称 + \u0001 + 地址 */
    private String channelKey() {
        String name = currentChannelName();
        String url = currentChannelUrl();
        return name + "\u0001" + url;
    }

    private String currentChannelName() {
        if (!TextUtils.isEmpty(directUrl)) return directTitle == null ? "" : directTitle;
        if (channels != null && channelIndex >= 0 && channelIndex < channels.size()) {
            LiveChannelItem item = channels.get(channelIndex);
            if (item != null && item.getChannelName() != null) return item.getChannelName();
        }
        return tvTitle == null ? "" : tvTitle.getText().toString();
    }

    private String currentChannelUrl() {
        if (!TextUtils.isEmpty(directUrl)) return directUrl;
        if (channels != null && channelIndex >= 0 && channelIndex < channels.size()) {
            LiveChannelItem item = channels.get(channelIndex);
            if (item != null && item.getUrl() != null) return item.getUrl();
        }
        return "";
    }

    private List<String> readLiveKeys(String name) {        List<String> out = new ArrayList<>();
        try {
            String raw = getSharedPreferences("hoopoe_live", MODE_PRIVATE).getString(name, "[]");
            org.json.JSONArray arr = new org.json.JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Throwable ignored) {
        }
        return out;
    }

    private void writeLiveKeys(String name, List<String> keys) {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (String k : keys) arr.put(k);
        getSharedPreferences("hoopoe_live", MODE_PRIVATE).edit()
                .putString(name, arr.toString()).apply();
    }

    private void updateFavMark() {
        if (ivFavLive == null) return;
        boolean has = readLiveKeys("fav").contains(channelKey());
        ivFavLive.setColorFilter(has ? 0xFFFFD54F : 0xFFFFFFFF);
        ivFavLive.setAlpha(has ? 1f : 0.85f);
    }

    private void toggleChannelFavorite() {
        String key = channelKey();
        if (TextUtils.isEmpty(currentChannelUrl())) {
            Toast.makeText(this, "还没有拿到频道地址", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = currentChannelName();
        String url = currentChannelUrl();
        List<String> fav = readLiveKeys("fav");
        boolean has = fav.contains(key);
        if (has) {
            fav.remove(key);
            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
        } else {
            fav.add(0, key);
            Toast.makeText(this, "已加入收藏", Toast.LENGTH_SHORT).show();
        }
        writeLiveKeys("fav", fav);
        // v1.7.1：同一步写进全局收藏（sourceKey=live），首页「收藏夹」才看得到直播频道
        MobileFavorites.Item it = new MobileFavorites.Item();
        it.vodId = url;
        it.sourceKey = LIVE_SOURCE_KEY;
        it.name = name;
        it.note = "直播频道";
        if (has) {
            MobileFavorites.remove(url, LIVE_SOURCE_KEY);
        } else {
            MobileFavorites.toggle(it);
        }
        updateFavMark();
    }

    /** 直播下载：m3u8 直播流按 60 秒截取（录制），回看/点播流整段下完；成功提示绝对路径 */
    private void downloadCurrentLive() {
        String url = currentChannelUrl();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "还没有拿到直播地址", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = currentChannelName();
        MobileDownloadHelper.startLive(this, name, url, setPlayHeaders(url), LIVE_RECORD_SECONDS);
    }

    private void stepChannel(int step) {
        if (!TextUtils.isEmpty(directUrl)) {
            Toast.makeText(this, "回看模式下不能切台，返回节目单换个时段", Toast.LENGTH_SHORT).show();
            return;
        }
        if (channels.isEmpty()) return;
        channelIndex = (channelIndex + step + channels.size()) % channels.size();
        play();
    }

    private void play() {
        LiveChannelItem item = channels.get(channelIndex);
        if (item == null) return;
        String name = item.getChannelName();
        tvTitle.setText(name == null ? "" : name);
        tvGroup.setText((group == null ? "" : group.getGroupName())
                + " · " + (channelIndex + 1) + "/" + channels.size());

        String url = item.getUrl();
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "该频道没有可用地址", Toast.LENGTH_SHORT).show();
            return;
        }
        pbLoading.setVisibility(View.VISIBLE);
        livePlayerManager.getLiveChannelPlayer(videoView, name);
        videoView.release();
        videoView.setUrl(url, setPlayHeaders(url));
        videoView.start();
        tvPlayPause.setText(R.string.m_pause);
    }

    /** 与 TV LivePlayActivity.setPlayHeaders 同规则：命中 livePlayHeaders 的 flag 就用其 header，否则用默认 UA */
    private HashMap<String, String> setPlayHeaders(String url) {
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

    /** 直播出错（黑屏/超时）：先用软解重试一次，仍失败则提示并给出可点的重试入口 */
    private void onLiveError() {
        if (!retried) {
            retried = true;
            try {
                com.orhanobut.hawk.Hawk.put(com.github.tvbox.osc.util.HawkConfig.IJK_CODEC, "软解码");
                videoView.release();
                play();
                return;
            } catch (Throwable ignored) {
            }
        }
        Toast.makeText(this, "该频道暂时无法播放，可切换上一台/下一台试试", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        videoView.release();
        super.onDestroy();
    }
}
