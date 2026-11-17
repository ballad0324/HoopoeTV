package com.github.tvbox.osc.mobile.ui.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.mobile.setting.MobileSetting;
import com.github.tvbox.osc.mobile.ui.view.MobileDockHelper;
import com.github.tvbox.osc.mobile.ui.compose.SettingsHost;
import com.github.tvbox.osc.mobile.ui.adapter.MobileSettingAdapter;
import com.github.tvbox.osc.setting.CommonSetting;
import com.github.tvbox.osc.setting.TvSetting;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.dialog.ResetDialog;
import com.github.tvbox.osc.util.BackupHelper;
import com.github.tvbox.osc.util.FavSyncHelper;
import com.github.tvbox.osc.util.SettingsSyncHelper;
import com.github.tvbox.osc.util.CacheHelper;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.SkinManager;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.XXPermissions;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import xyz.doikki.videoplayer.player.VideoView;

/**
 * 手机端设置页。
 *
 * 分组原则（对应用户要求）：
 * - 「通用」分组里的项两端都有，读写一律走 {@link CommonSetting}；
 * - 「手机端专有」的显示开关已固定为开启，不再在本页展示（见 {@link MobileSetting}）；
 * - TV 端专有项（T 型双栏、拨轮位置、焦点防抖等）不在此页出现，见 {@link TvSetting}。
 */
public class MobileSettingActivity extends BaseActivity implements SettingsHost {

    /** 缓存占用文案，异步算完后回填（缓存目录可能很大，不能卡主线程） */
    private String cacheSizeText = "计算中…";

    private final int[] playTypes = {1, 2, 0, 10};
    private final int[] playRenders = {0, 1};
    private final int[] playScales = {
            VideoView.SCREEN_SCALE_DEFAULT,
            VideoView.SCREEN_SCALE_16_9,
            VideoView.SCREEN_SCALE_4_3,
            VideoView.SCREEN_SCALE_MATCH_PARENT,
            VideoView.SCREEN_SCALE_ORIGINAL,
            VideoView.SCREEN_SCALE_CENTER_CROP
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_setting_mobile;
    }

    @Override
    public float getSizeInDp() {
        return 360;
    }

    @Override
    protected void init() {
        // 设置页不属于 dock 任何一项（设置入口已移到各页右上角），传 TAB_NONE 让四项都不点亮
        com.github.tvbox.osc.mobile.ui.compose.MobileHomeDock.bindDockOnly(this, findViewById(R.id.cvDock), com.github.tvbox.osc.mobile.ui.compose.MobileHomeDock.TAB_NONE);
        findViewById(R.id.btnBack).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                finish();
            }
        });

        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        // 设置列表本体由 Compose 渲染（SettingsScreen.kt），这里只把 Row 数据喂给它
        androidx.compose.ui.platform.ComposeView cv = findViewById(R.id.cvSettings);
        if (cv != null) {
            com.github.tvbox.osc.mobile.ui.compose.MobileSettings.bindSettings(cv, this);
        }
        refreshRows();
        refreshCacheSizeAsync();

        // 顶部「界面模式」分段按钮：手机端 / 电视端
        findViewById(R.id.tvModeMobile).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                applyUiMode(CommonSetting.UI_MOBILE);
            }
        });
        findViewById(R.id.tvModeTv).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                applyUiMode(CommonSetting.UI_TV);
            }
        });
        refreshModeSegment();
    }

    /** 分段按钮按当前偏好高亮，并显示一句说明 */
    private void refreshModeSegment() {
        boolean mobile = CommonSetting.isMobileUi();
        TextView tvMobile = findViewById(R.id.tvModeMobile);
        TextView tvTv = findViewById(R.id.tvModeTv);
        TextView tvCurrent = findViewById(R.id.tvModeCurrent);
        tvMobile.setSelected(mobile);
        tvTv.setSelected(!mobile);
        tvMobile.setTextColor((mobile ? com.github.tvbox.osc.util.SkinManager.text1(this) : com.github.tvbox.osc.util.SkinManager.text2(this)));
        tvTv.setTextColor((mobile ? com.github.tvbox.osc.util.SkinManager.text2(this) : com.github.tvbox.osc.util.SkinManager.text1(this)));
        tvCurrent.setText("当前：" + CommonSetting.getUiModeName() + "，切换后立即重启界面");
    }

    private void refreshRows() {
        List<MobileSettingAdapter.Row> rows = new ArrayList<>();

        // ---------- 通用：数据源（置顶：接口/直播源是最常改的配置） ----------
        // 四项都改成「输入框 + 立即更改」：填完就地生效，不再弹对话框
        rows.add(MobileSettingAdapter.Row.header(tr("通用 · 数据源","General · Data source")));
        // v1.7.20：出厂不内置任何源。源模式胶囊与「数据源」站点列表已删除，
        // 这里只留「手动填三项」+「口令拉取默认源」+「清空」。
        rows.add(MobileSettingAdapter.Row.input(tr("接口地址","API URL"),
                tr("填 TVBox 标准 json 接口地址；改完返回首页会自动重新拉取，不用重启",
                        "TVBox JSON API address; reloads when you go back home"),
                "", ApiConfig.getApiUrl(), tr("立即更改","Apply"),
                new MobileSettingAdapter.OnInput() {
                    @Override
                    public void onInput(MobileSettingAdapter.Row row, String text) {
                        if (TextUtils.isEmpty(text)) {
                            toast(tr("地址不能为空","Address cannot be empty"));
                            return;
                        }
                        if (text.equals(ApiConfig.getApiUrl())) {
                            toast(tr("地址没有变化","Address unchanged"));
                            return;
                        }
                        CommonSetting.putApiUrl(text);
                        // 打脏标记：返回首页后由首页重新拉取配置并重建分类，不必重启 App
                        CommonSetting.markConfigDirty();
                        toast(tr("接口地址已更新，返回首页即重新加载",
                                "Saved. Back to home to reload"));
                        refreshRows();
                    }
                }));
        rows.add(MobileSettingAdapter.Row.input(tr("直播源","Live source"),
                tr("填直播源地址（m3u/txt），保存后重新进直播页生效",
                        "Live source (.m3u/.txt); takes effect when you re-enter Live"),
                "",
                CommonSetting.getLiveUrl(), tr("立即更改","Apply"),
                new MobileSettingAdapter.OnInput() {
                    @Override
                    public void onInput(MobileSettingAdapter.Row row, String text) {
                        if (TextUtils.isEmpty(text)) {
                            toast(tr("直播源不能为空","Live source cannot be empty"));
                            return;
                        }
                        CommonSetting.putLiveUrl(text);
                        toast(tr("直播源已更新","Live source saved"));
                        refreshRows();
                    }
                }));
        rows.add(MobileSettingAdapter.Row.input(tr("EPG 地址","EPG URL"),
                tr("填 EPG 地址，填完即生效", "EPG address, applied immediately"),
                "",
                CommonSetting.getEpgUrl(), tr("立即更改","Apply"),
                new MobileSettingAdapter.OnInput() {
                    @Override
                    public void onInput(MobileSettingAdapter.Row row, String text) {
                        CommonSetting.putEpgUrl(text);
                        toast(tr("EPG 地址已更新","EPG URL saved"));
                        refreshRows();
                    }
                }));

        // ---------- 默认源（口令门禁）：出厂什么源都没有，只有这里能一次性写入 ----------
        rows.add(MobileSettingAdapter.Row.item(tr("拉取默认源","Pull default source"),
                CommonSetting.isBuiltinUnlocked()
                        ? tr("已启用（口令已验证，再次拉取会覆盖当前三项）","Enabled (verified; re-pull overwrites)")
                        : tr("未启用 · 点击后输入口令才会写入","Off · tap and enter the password"),
                new Runnable() {
                    @Override
                    public void run() {
                        showDefaultSourceDialog();
                    }
                }));
        rows.add(MobileSettingAdapter.Row.item(tr("清空数据源","Clear sources"),
                tr("接口 / 直播源 / EPG 全部清空，并撤销默认源解锁",
                        "Wipes API / live / EPG and revokes the unlock"),
                new Runnable() {
                    @Override
                    public void run() {
                        confirmAction(tr("清空所有数据源","Clear all sources"),
                                tr("接口、直播源、EPG 都会被清空，之后要重新输口令才能拉取默认源。确定？",
                                        "API, live source and EPG will be wiped. Continue?"),
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        CommonSetting.clearAllSource();
                                        toast(tr("已清空所有数据源","All sources cleared"));
                                        refreshRows();
                                    }
                                });
                    }
                }));

        // 局域网直连入口已按用户要求关闭（实现代码仍在，需要时再挂回来）

        // ---------- 通用：外观（两端共用，走 CommonSetting） ----------
        // 「界面模式」已提到页面顶部的分段按钮（A3），这里不再重复一行
        rows.add(MobileSettingAdapter.Row.header(tr("通用 · 外观","General · Appearance")));
        rows.add(MobileSettingAdapter.Row.capsule(tr("语言","Language"),
                tr("默认简体中文，切换后立即重建界面", "Simplified Chinese by default"),
                new String[]{"简体中文", "English"},
                CommonSetting.getLanguage(), new MobileSettingAdapter.OnOption() {
                    @Override
                    public void onOption(MobileSettingAdapter.Row row, int index) {
                        CommonSetting.putLanguage(index);
                        com.github.tvbox.osc.util.LocaleHelper.setLocale(
                                MobileSettingActivity.this, index == 1 ? "en" : "zh");
                        toast(index == 1 ? "Language: English" : "语言：简体中文");
                        refreshRows();
                        // 立刻按新语言重建界面
                        recreate();
                    }
                }));

        // v1.7.22：补齐电视端设置里原有的三项外观开关（原位置：语言之后）
        rows.add(MobileSettingAdapter.Row.capsule(tr("主题","Theme"),
                tr("七套配色，选中立即生效", "Seven color presets, applied at once"),
                SkinManager.allNames(), SkinManager.get(), new MobileSettingAdapter.OnOption() {
                    @Override
                    public void onOption(MobileSettingAdapter.Row row, int index) {
                        SkinManager.set(index);
                        toast(tr("已切换主题：", "Theme: ") + SkinManager.name(index));
                        recreate();
                    }
                }));
        rows.add(MobileSettingAdapter.Row.item(tr("换壁纸","Change wallpaper"),
                tr("点按更换","Tap to change"),
                new Runnable() {
                    @Override
                    public void run() {
                        changeWallpaperNow();
                    }
                }));
        rows.add(MobileSettingAdapter.Row.item(tr("重置壁纸","Reset wallpaper"),
                tr("恢复默认","Restore"),
                new Runnable() {
                    @Override
                    public void run() {
                        File wp = new File(getFilesDir().getAbsolutePath() + "/wp");
                        if (wp.exists()) wp.delete();
                        changeWallpaper(true);
                        toast(tr("壁纸已重置", "Wallpaper reset"));
                    }
                }));
        rows.add(MobileSettingAdapter.Row.header(tr("通用 · 播放","General · Playback")));
        rows.add(MobileSettingAdapter.Row.switchRow(tr("聚合搜索","Aggregated search"),
                tr("开启后在所有可搜索站点并发检索，关闭只搜当前源","On: search every site at once. Off: current source only"),
                CommonSetting.isAggSearch(),
                new MobileSettingAdapter.OnSwitch() {
                    @Override
                    public void onSwitch(MobileSettingAdapter.Row row, boolean checked) {
                        CommonSetting.putAggSearch(checked);
                        toast(checked
                                ? tr("已开启聚合搜索", "Aggregated search on")
                                : tr("已关闭，只搜当前源", "Off, current source only"));
                    }
                }));
        rows.add(MobileSettingAdapter.Row.capsule(tr("播放内核","Player engine"),
                tr("画面异常时可换内核再试", "Switch engine if playback looks wrong"),
                namesOfPlayTypes(), indexOf(playTypes, CommonSetting.getPlayType()),
                new MobileSettingAdapter.OnOption() {
                    @Override
                    public void onOption(MobileSettingAdapter.Row row, int index) {
                        CommonSetting.putPlayType(playTypes[index]);
                        refreshRows();
                    }
                }));
        rows.add(MobileSettingAdapter.Row.capsule(tr("渲染方式","Render mode"),
                tr("SurfaceView 不参与合成，画面更稳", "SurfaceView avoids GPU composition"),
                new String[]{"TextureView", "SurfaceView"},
                indexOf(playRenders, CommonSetting.getPlayRender()),
                new MobileSettingAdapter.OnOption() {
                    @Override
                    public void onOption(MobileSettingAdapter.Row row, int index) {
                        CommonSetting.putPlayRender(playRenders[index]);
                        refreshRows();
                    }
                }));
        rows.add(MobileSettingAdapter.Row.capsule(tr("画面比例","Aspect ratio"),
                null, namesOfScales(), indexOf(playScales, CommonSetting.getPlayScale()),
                new MobileSettingAdapter.OnOption() {
                    @Override
                    public void onOption(MobileSettingAdapter.Row row, int index) {
                        CommonSetting.putPlayScale(playScales[index]);
                        refreshRows();
                    }
                }));

        rows.add(MobileSettingAdapter.Row.capsule(tr("直播内核","Live engine"),
                tr("直播播放内核，与电视端共用同一设置项", "Same setting as the TV page"),
                new String[]{"系统", "IJK", "EXO"},
                CommonSetting.getLivePlayerType() >= 0 && CommonSetting.getLivePlayerType() <= 2
                        ? CommonSetting.getLivePlayerType() : 0,
                new MobileSettingAdapter.OnOption() {
                    @Override
                    public void onOption(MobileSettingAdapter.Row row, int index) {
                        CommonSetting.putLivePlayerType(index);
                        toast(tr("直播内核已切换，重进直播生效", "Live engine switched"));
                        refreshRows();
                    }
                }));

        // v1.7.24：电视端「首页显示」「直播启动」在手机端的等价落地
        rows.add(MobileSettingAdapter.Row.switchRow(tr("首页继续观看条","Home watch strip"),
                tr("关闭后首页顶部不再显示「继续观看 / 我的收藏」横条", "Hide the continue-watching strip on home"),
                MobileSetting.isShowHomeStrip(),
                new MobileSettingAdapter.OnSwitch() {
                    @Override
                    public void onSwitch(MobileSettingAdapter.Row row, boolean checked) {
                        MobileSetting.putShowHomeStrip(checked);
                        toast(checked ? tr("首页横条已显示", "Strip shown") : tr("首页横条已隐藏", "Strip hidden"));
                    }
                }));
        rows.add(MobileSettingAdapter.Row.switchRow(tr("启动直达直播","Open live on launch"),
                tr("开 App 后直接进直播页，不再停在首页", "Jump straight to the live page after launch"),
                MobileSetting.isLiveBoot(),
                new MobileSettingAdapter.OnSwitch() {
                    @Override
                    public void onSwitch(MobileSettingAdapter.Row row, boolean checked) {
                        MobileSetting.putLiveBoot(checked);
                        toast(checked ? tr("下次启动直达直播", "Will open live next launch")
                                : tr("下次启动回到首页", "Will open home next launch"));
                    }
                }));

        // v1.7.31：删掉手机端点了没行为的设置（后台播放器 / 播放器渲染器 / 渲染模式 /
        // 首选配置来源 / 视频净化 / 解析 WebView）——这些键只有电视端有消费者，手机端保留就是摆设
        final String[] cacheOnOff = new String[]{tr("关闭","Off"), tr("开启","On")};
        int cacheIdx = Hawk.get(HawkConfig.IJK_CACHE_PLAY, false) ? 1 : 0;
        rows.add(MobileSettingAdapter.Row.capsule(tr("播放缓存","Playback buffer"),
                tr("弱网卡顿时开启，只在 IJK 内核生效", "Turn on for unstable network (IJK engine only)"),
                cacheOnOff, cacheIdx,
                new MobileSettingAdapter.OnOption() {
                    @Override
                    public void onOption(MobileSettingAdapter.Row row, int index) {
                        Hawk.put(HawkConfig.IJK_CACHE_PLAY, index == 1);
                        toast(tr("播放缓存：", "Buffer: ") + cacheOnOff[index]);
                        refreshRows();
                    }
                }));
        rows.add(MobileSettingAdapter.Row.capsule(tr("搜索展示","Search display"),
                tr("搜索结果用列表还是缩略图", "List or thumbnail for search results"),
                new String[]{"文字列表", "缩略图"},
                clampInt(Hawk.get(HawkConfig.SEARCH_VIEW, 0), 0, 1),
                new MobileSettingAdapter.OnOption() {
                    @Override
                    public void onOption(MobileSettingAdapter.Row row, int index) {
                        Hawk.put(HawkConfig.SEARCH_VIEW, index);
                        toast(tr("搜索展示已切换", "Search display switched"));
                        refreshRows();
                    }
                }));
        rows.add(MobileSettingAdapter.Row.switchRow(tr("快速搜索","Fast search"),
                tr("输入即搜，命中更快", "Search as you type"),
                Hawk.get(HawkConfig.FAST_SEARCH_MODE, false),
                new MobileSettingAdapter.OnSwitch() {
                    @Override
                    public void onSwitch(MobileSettingAdapter.Row row, boolean checked) {
                        Hawk.put(HawkConfig.FAST_SEARCH_MODE, checked);
                        toast(checked ? tr("已开启快速搜索", "Fast search on") : tr("已关闭快速搜索", "Fast search off"));
                    }
                }));
        rows.add(MobileSettingAdapter.Row.capsule(tr("历史条数","History limit"),
                tr("首页「继续观看」最多保留多少条", "How many continue-watching entries to keep"),
                new String[]{"20 条", "40 条", "60 条", "80 条", "100 条"},
                clampInt(Hawk.get(HawkConfig.HOME_NUM, 0), 0, 4),
                new MobileSettingAdapter.OnOption() {
                    @Override
                    public void onOption(MobileSettingAdapter.Row row, int index) {
                        Hawk.put(HawkConfig.HOME_NUM, index);
                        toast(tr("历史条数已设置", "History limit set"));
                        refreshRows();
                    }
                }));

        // ---------- 通用：网络 ----------
        rows.add(MobileSettingAdapter.Row.header(tr("通用 · 网络","General · Network")));
        rows.add(MobileSettingAdapter.Row.capsule("DNS",
                tr("加密 DNS，点选立即生效", "Encrypted DNS, applied instantly"),
                dnsShortNames(), CommonSetting.getDohUrl(),
                new MobileSettingAdapter.OnOption() {
                    @Override
                    public void onOption(MobileSettingAdapter.Row row, int index) {
                        CommonSetting.putDohUrl(index);
                        toast(tr("DNS 已切换", "DNS switched") + "：" + dnsName(index));
                        refreshRows();
                    }
                }));

        // 代理服务器设置已移除（电视端 EXO 的代理重试逻辑一并删掉）

        // ---------- 账号与云备份（v1.7.17：原「管理面板」的门槛取消） ----------
        // 不再有登录/注册/退出登录这套概念：云备份用 App 内置的服务器与账号，
        // 首次点开只需输一次密码，密码与 token 都记在本地，之后直接用。
        rows.add(MobileSettingAdapter.Row.header(tr("云端备份","Cloud backup")));
        // v1.7.30：账号与服务器地址不外露，只留「设置密码」入口
        rows.add(MobileSettingAdapter.Row.item(tr("云备份密码","Cloud password"),
                tr("内置服务器，输一次密码即可","Built-in server, set a password once"), new Runnable() {
                    @Override
                    public void run() {
                        showCloudAccountDialog();
                    }
                }));
        rows.add(MobileSettingAdapter.Row.item(tr("上传到云端","Upload to cloud"),
                tr("把本机设置与收藏推到云端，另一台设备拉回即可","Push settings and favorites"),
                new Runnable() {
                    @Override
                    public void run() {
                        cloudPush();
                    }
                }));
        rows.add(MobileSettingAdapter.Row.item(tr("从云端拉回","Pull from cloud"),
                tr("拉取云端最近一次上传的设置与收藏","Pull the latest from cloud"),
                new Runnable() {
                    @Override
                    public void run() {
                        cloudPull();
                    }
                }));

        // ---------- 通用 · 存储与关于（审计补缺：原先手机端没有这三项） ----------
        rows.add(MobileSettingAdapter.Row.header(tr("通用 · 存储与关于","General · Storage & About")));
        rows.add(MobileSettingAdapter.Row.item(tr("下载管理","Downloads"),
                tr("下载过的视频与文件","Downloaded files"), new Runnable() {
            @Override
            public void run() {
                jumpActivity(MobileDownloadsActivity.class);
            }
        }));
        rows.add(MobileSettingAdapter.Row.item(tr("清空播放历史","Clear history"),
                tr("只清本机的播放记录，收藏和设置不动","Local play records only"), new Runnable() {
                    @Override
                    public void run() {
                        confirmAction(tr("清空播放历史","Clear history"),
                                tr("确定清空全部播放记录？","Clear all play records?"), new Runnable() {
                                    @Override
                                    public void run() {
                                        com.github.tvbox.osc.mobile.ui.MobilePlayHistory.clear();
                                        toast(tr("播放历史已清空","History cleared"));
                                    }
                                });
                    }
                }));
        rows.add(MobileSettingAdapter.Row.item(tr("清空我的收藏","Clear favorites"),
                tr("两端共用一份收藏，清完电视端也会同步变空","Favorites are shared with the TV UI"), new Runnable() {
                    @Override
                    public void run() {
                        confirmAction(tr("清空我的收藏","Clear favorites"),
                                tr("确定清空全部收藏？","Clear all favorites?"), new Runnable() {
                                    @Override
                                    public void run() {
                                        List<com.github.tvbox.osc.mobile.ui.MobileFavorites.Item> list =
                                                com.github.tvbox.osc.mobile.ui.MobileFavorites.load();
                                        if (list != null) {
                                            for (com.github.tvbox.osc.mobile.ui.MobileFavorites.Item it : list) {
                                                com.github.tvbox.osc.mobile.ui.MobileFavorites.remove(it.vodId, it.sourceKey);
                                            }
                                        }
                                        toast(tr("收藏已清空","Favorites cleared"));
                                    }
                                });
                    }
                }));
        rows.add(MobileSettingAdapter.Row.item(tr("清理缓存","Clear cache"), cacheSizeText, new Runnable() {
            @Override
            public void run() {
                confirmClearCache();
            }
        }));
        rows.add(MobileSettingAdapter.Row.item(tr("备份与恢复","Backup & restore"), backupSummary(), new Runnable() {
            @Override
            public void run() {
                showBackupDialog();
            }
        }));
        // v1.7.23：电视端有「重置」，手机端也补上（同一个 ResetDialog）
        rows.add(MobileSettingAdapter.Row.item(tr("恢复出厂设置","Factory reset"),
                tr("清空配置", "Wipe config"),
                new Runnable() {
                    @Override
                    public void run() {
                        new ResetDialog(MobileSettingActivity.this).show();
                    }
                }));
        // 云端备份（MySQL）已移到首页右上角用户面板（v1.6.37），设置页不再重复这一行
        rows.add(MobileSettingAdapter.Row.item(tr("关于","About"), versionText(), new Runnable() {
            @Override
            public void run() {
                showAbout();
            }
        }));

        // 手机端专有的三个显示开关（搜索入口 / 底部导航文字 / 分类下划线）按需求固定为开启，
        // 不再在设置页暴露开关，MobileSetting 里的默认值即生效值。

        adapterSetRows(rows);
    }

    /** 把重建好的行数据推给 Compose 层（Compose 只负责画，行为仍挂在这些 Row 上） */
    private void adapterSetRows(List<MobileSettingAdapter.Row> rows) {
        com.github.tvbox.osc.mobile.ui.compose.MobileSettings.updateRows(rows);
    }

    @Override
    public void onRowClick(MobileSettingAdapter.Row row) {
        if (row != null && row.action != null) row.action.run();
    }

    /* ===================== 选项文案 ===================== */

    private String[] namesOfPlayTypes() {
        String[] names = new String[playTypes.length];
        for (int i = 0; i < playTypes.length; i++) {
            names[i] = PlayerHelper.getPlayerName(playTypes[i]);
        }
        return names;
    }

    private String[] namesOfScales() {
        String[] names = new String[playScales.length];
        for (int i = 0; i < playScales.length; i++) {
            names[i] = PlayerHelper.getScaleName(playScales[i]);
        }
        return names;
    }

    private int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) return i;
        }
        return 0;
    }

    private String[] dnsNames() {
        String[] names = new String[OkGoHelper.dnsHttpsList.size()];
        for (int i = 0; i < names.length; i++) names[i] = OkGoHelper.dnsHttpsList.get(i);
        return names;
    }

    /** 胶囊上只放主机名，完整地址太长会把整行挤爆 */
    private String[] dnsShortNames() {
        String[] names = new String[OkGoHelper.dnsHttpsList.size()];
        for (int i = 0; i < names.length; i++) {
            String full = OkGoHelper.dnsHttpsList.get(i);
            String shortName = full;
            if (full != null) {
                shortName = full.replace("https://", "").replace("http://", "");
                int slash = shortName.indexOf('/');
                if (slash > 0) shortName = shortName.substring(0, slash);
                if (shortName.length() > 14) shortName = shortName.substring(0, 13) + "…";
            }
            names[i] = shortName;
        }
        return names;
    }

    private String dnsName(int index) {
        if (index >= 0 && index < OkGoHelper.dnsHttpsList.size()) {
            return OkGoHelper.dnsHttpsList.get(index);
        }
        return "关闭";
    }

    private String emptyAsDash(String value) {
        return TextUtils.isEmpty(value) ? "未设置" : value;
    }

    /* ===================== 缓存 / 备份 / 关于 ===================== */

    /** 缓存目录可能很大，统计放到后台线程，算完回填列表 */
    private void refreshCacheSizeAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long size = CacheHelper.size(MobileSettingActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        cacheSizeText = CacheHelper.formatSize(size);
                        refreshRows();
                    }
                });
            }
        }).start();
    }

    private void confirmClearCache() {
        new AlertDialog.Builder(this)
                .setTitle("清理缓存")
                .setMessage("当前缓存 " + cacheSizeText + "。\n清理后图片与视频缓冲会被清空，收藏、历史和设置不受影响。")
                .setPositiveButton("清理", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearCacheAsync();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearCacheAsync() {
        cacheSizeText = "清理中…";
        refreshRows();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long freed = CacheHelper.clear(MobileSettingActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        cacheSizeText = CacheHelper.formatSize(CacheHelper.size(MobileSettingActivity.this));
                        refreshRows();
                        toast("已清理 " + CacheHelper.formatSize(freed));
                    }
                });
            }
        }).start();
    }

    private String backupSummary() {
        int count = BackupHelper.list().size();
        return count == 0 ? "还没有备份" : (count + " 份备份");
    }

    /* ===================== 云端备份（v1.7.17） ===================== */

    /** 云备份内置服务器与账号：不再有「登录」这一步，用户只需输一次密码 */
    private static final String CLOUD_URL = "http://43.226.44.105:6066/tvbox";
    private static final String CLOUD_USER = "ballad0324";

    private String cloudAccountText() {
        String url = com.github.tvbox.osc.util.CloudBackupHelper.getBaseUrl();
        if (TextUtils.isEmpty(url)) {
            return tr("未配置：点开输一次密码", "Not set: tap to enter password");
        }
        String user = com.github.tvbox.osc.util.CloudBackupHelper.getUser();
        return (TextUtils.isEmpty(user) ? CLOUD_USER : user) + " · " + url.replace("http://", "");
    }

    private void showCloudAccountDialog() {
        final EditText et = new EditText(this);
        et.setHint(tr("云备份密码", "Cloud backup password"));
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        et.setPadding(pad, pad / 2, pad, pad / 2);
        String pass = com.github.tvbox.osc.util.CloudBackupHelper.getPass();
        if (!TextUtils.isEmpty(pass)) et.setText(pass);
        new AlertDialog.Builder(this)
                .setTitle(tr("云备份密码", "Cloud password"))
                .setMessage(tr("账号由 App 内置，你只需要设一次密码；"
                                + "密码和登录凭证都存在本机，之后自动使用，不用每次登录。",
                        "The account is built in; set a password once and it is remembered on this device."))
                .setView(et)
                .setNegativeButton(tr("取消", "Cancel"), null)
                .setPositiveButton(tr("保存", "Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String p = et.getText() == null ? "" : et.getText().toString().trim();
                        if (TextUtils.isEmpty(p)) {
                            toast(tr("密码为空，未保存", "Empty password"));
                            return;
                        }
                        cloudLogin(p);
                    }
                })
                .show();
    }

    private void cloudLogin(final String pass) {
        toast(tr("正在连接云端…", "Connecting…"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                com.github.tvbox.osc.util.CloudBackupHelper.save(CLOUD_URL, CLOUD_USER, pass);
                final com.github.tvbox.osc.util.CloudBackupHelper.Res res =
                        com.github.tvbox.osc.util.CloudBackupHelper.login();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast(res != null && res.ok
                                ? tr("云备份已就绪", "Cloud backup ready")
                                : tr("云端连接失败：", "Cloud failed: ") + (res == null ? "?" : res.msg));
                        refreshRows();
                    }
                });
            }
        }).start();
    }

    private boolean ensureCloud() {
        if (com.github.tvbox.osc.util.CloudBackupHelper.isConfigured()) return true;
        toast(tr("先在「云备份密码」里设一次密码", "Set the cloud password first"));
        showCloudAccountDialog();
        return false;
    }

    private void cloudPush() {
        if (!ensureCloud()) return;
        toast(tr("正在上传…", "Uploading…"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final com.github.tvbox.osc.util.CloudBackupHelper.Res a = SettingsSyncHelper.push();
                final com.github.tvbox.osc.util.CloudBackupHelper.Res b = FavSyncHelper.push();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast((a != null && a.ok
                                ? tr("设置已上传", "Settings pushed")
                                : tr("设置上传失败：", "Push failed: ") + (a == null ? "?" : a.msg))
                                + "；" + (b != null && b.ok
                                ? tr("收藏已上传", "Favorites pushed")
                                : tr("收藏上传失败：", "Favorites failed: ") + (b == null ? "?" : b.msg)));
                    }
                });
            }
        }).start();
    }

    private void cloudPull() {
        if (!ensureCloud()) return;
        confirmAction(tr("从云端拉回", "Pull from cloud"),
                tr("会覆盖本机的设置与收藏（收藏按时间合并），确定继续？",
                        "Overwrites settings; favorites are merged."),
                new Runnable() {
                    @Override
                    public void run() {
                        toast(tr("正在拉回…", "Pulling…"));
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final com.github.tvbox.osc.util.CloudBackupHelper.Res a = SettingsSyncHelper.pull();
                                final com.github.tvbox.osc.util.CloudBackupHelper.Res b = FavSyncHelper.pull();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        toast((a != null && a.ok
                                                ? tr("设置已拉回", "Settings pulled")
                                                : tr("设置拉回失败：", "Pull failed: ") + (a == null ? "?" : a.msg))
                                                + "；" + (b != null && b.ok
                                                ? tr("收藏已合并", "Favorites merged")
                                                : tr("收藏拉回失败：", "Favorites failed: ") + (b == null ? "?" : b.msg)));
                                        // 设置变了：回首页重新拉接口
                                        CommonSetting.markConfigDirty();
                                        refreshRows();
                                    }
                                });
                            }
                        }).start();
                    }
                });
    }

    /**
     * 换壁纸：与电视端 ModelSettingFragment 同一套逻辑 ——
     * 接口下发了 wallpaper 就下载到 files/wp 再用；没有就轮换内置三张。
     */
    private void changeWallpaperNow() {
        if (!ApiConfig.get().wallpaper.isEmpty()) {
            toast(tr("正在下载壁纸…", "Downloading wallpaper…"));
            OkGo.<File>get(ApiConfig.get().wallpaper).execute(new FileCallback(getFilesDir().getAbsolutePath(), "wp") {
                @Override
                public void onSuccess(Response<File> response) {
                    changeWallpaper(true);
                    toast(tr("壁纸已更换", "Wallpaper changed"));
                }

                @Override
                public void onError(Response<File> response) {
                    super.onError(response);
                    toast(tr("壁纸下载失败", "Wallpaper download failed"));
                }

                @Override
                public void downloadProgress(Progress progress) {
                    super.downloadProgress(progress);
                }
            });
            return;
        }
        try {
            int[] wps = new int[]{R.drawable.wallpaper_1, R.drawable.wallpaper_2, R.drawable.wallpaper_3};
            int idx = (Hawk.get("wp_index", -1) + 1) % wps.length;
            Hawk.put("wp_index", idx);
            Bitmap bmp = BitmapFactory.decodeResource(getResources(), wps[idx]);
            File wpFile = new File(getFilesDir().getAbsolutePath() + "/wp");
            FileOutputStream fos = new FileOutputStream(wpFile);
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos);
            fos.flush();
            fos.close();
            bmp.recycle();
            changeWallpaper(true);
            toast(tr("壁纸已更换", "Wallpaper changed"));
        } catch (Throwable th) {
            th.printStackTrace();
            toast(tr("换壁纸失败", "Change wallpaper failed"));
        }
    }

    /** 默认源口令门禁：口令错只 Toast，不发任何请求；对了才写入三件套 */
    private void showDefaultSourceDialog() {
        final EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setHint(tr("请输入口令","Password"));
        new AlertDialog.Builder(this)
                .setTitle(tr("拉取默认源","Pull default source"))
                .setMessage(tr("注意：口令仅在本人设备使用；写入后接口 / 直播源 / EPG 三项会被覆盖。",
                        "Writes the default API, live source and EPG for this device."))
                .setView(et)
                .setNegativeButton(tr("取消","Cancel"), null)
                .setPositiveButton(tr("拉取","Pull"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String pass = et.getText().toString().trim();
                        if (!CommonSetting.BUILTIN_PASS.equals(pass)) {
                            toast(tr("口令错误","Wrong password"));
                            return;
                        }
                        CommonSetting.applyDefaultSource();
                        toast(tr("已写入默认源，返回首页自动重载","Default source applied"));
                        refreshRows();
                    }
                })
                .show();
    }

    private void confirmAction(String title, String msg, final Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setNegativeButton(tr("取消", "Cancel"), null)
                .setPositiveButton(tr("确定", "OK"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        action.run();
                    }
                })
                .show();
    }

    private void showBackupDialog() {
        final List<String> names = BackupHelper.list();
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("备份与恢复");
        if (names.isEmpty()) {
            builder.setMessage("还没有备份。\n点「立即备份」会把收藏、历史、搜索记录和设置存成一份，随后可一键上传到你的服务器。");
        } else {
            builder.setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    askRestoreOrDelete(names.get(which));
                }
            });
        }
        builder.setPositiveButton("立即备份", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                doBackup();
            }
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void doBackup() {
        // 不再强制申请「所有文件访问」：BackupHelper 会自动落到可写的目录
        runBackup();
    }

    private void runBackup() {
        toast("开始备份…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String name = BackupHelper.backup(MobileSettingActivity.this);
                final boolean appDir = BackupHelper.usingAppDir();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (name == null) {
                            toast("备份失败，请检查存储空间");
                            return;
                        }
                        toast(appDir
                                ? "已备份到应用私有目录 tvbox_backup/" + name + "（文件管理器看不到，云端上传不受影响）"
                                : "已备份到 tvbox_backup/" + name);
                        refreshRows();
                        showBackupDialog();
                    }
                });
            }
        }).start();
    }

    private void askRestoreOrDelete(final String name) {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"恢复这份备份", "删除这份备份"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) doRestore(name);
                        else doDeleteBackup(name);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doRestore(final String name) {
        toast("恢复中…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int result = BackupHelper.restore(MobileSettingActivity.this, name);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result == BackupHelper.OK) {
                            toast("恢复成功，3 秒后重启");
                            new android.os.Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    BackupHelper.restartApp(MobileSettingActivity.this);
                                }
                            }, 3000);
                        } else if (result == BackupHelper.ERR_DB) {
                            toast("恢复失败：数据库还原出错");
                        } else if (result == BackupHelper.ERR_HAWK) {
                            toast("恢复失败：设置项文件缺失或损坏");
                        } else {
                            toast("恢复失败：备份不存在或已损坏");
                        }
                    }
                });
            }
        }).start();
    }

    private void doDeleteBackup(final String name) {
        new AlertDialog.Builder(this)
                .setTitle("删除备份")
                .setMessage("确定删除 " + name + " ？此操作不可恢复。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean ok = BackupHelper.delete(name);
                        toast(ok ? "已删除" : "删除失败");
                        refreshRows();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

        private String versionText() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "未知";
        }
    }

    /** 关于：不复用 TV 的 dialog_about（那份布局按 TV 600px 宽写死，手机屏会超宽） */
    /* ===================== 跨端设置同步（手机 ⇄ 电视） ===================== */

    private void doSettingsPush() {
        toast(tr("正在上传设置…", "Uploading settings…"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final com.github.tvbox.osc.util.CloudBackupHelper.Res r =
                        com.github.tvbox.osc.util.SettingsSyncHelper.push();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast(r.ok ? r.msg : tr("上传失败：", "Upload failed: ") + r.msg);
                        refreshRows();
                    }
                });
            }
        }).start();
    }

    private void confirmSettingsPull() {
        new AlertDialog.Builder(this)
                .setTitle(tr("从云端拉回设置", "Pull settings"))
                .setMessage(tr("会用云端的接口地址、主题、播放器等设置覆盖本机，拉取后自动重启。确定继续？",
                        "Cloud settings will overwrite local ones, then the app restarts. Continue?"))
                .setPositiveButton(tr("拉取", "Pull"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doSettingsPull();
                    }
                })
                .setNegativeButton(tr("取消", "Cancel"), null)
                .show();
    }

    private void doSettingsPull() {
        toast(tr("正在拉取设置…", "Pulling settings…"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final com.github.tvbox.osc.util.CloudBackupHelper.Res r =
                        com.github.tvbox.osc.util.SettingsSyncHelper.pull();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (r.ok) {
                            toast(tr("已同步，3 秒后重启", "Synced, restarting in 3s"));
                            new android.os.Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    BackupHelper.restartApp(MobileSettingActivity.this);
                                }
                            }, 3000);
                        } else {
                            toast(tr("拉取失败：", "Pull failed: ") + r.msg);
                        }
                        refreshRows();
                    }
                });
            }
        }).start();
    }
    /* ===================== 收藏跨端同步（手机 ⇄ 电视） ===================== */

    private void doFavPush() {
        toast(tr("正在上传收藏…", "Uploading favorites…"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final com.github.tvbox.osc.util.CloudBackupHelper.Res r =
                        com.github.tvbox.osc.util.FavSyncHelper.push();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast(r.ok ? r.msg : tr("上传失败：", "Upload failed: ") + r.msg);
                        refreshRows();
                    }
                });
            }
        }).start();
    }

    private void doFavPull() {
        toast(tr("正在拉取收藏…", "Pulling favorites…"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final com.github.tvbox.osc.util.CloudBackupHelper.Res r =
                        com.github.tvbox.osc.util.FavSyncHelper.pull();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast(r.ok ? r.msg : tr("拉取失败：", "Pull failed: ") + r.msg);
                        refreshRows();
                    }
                });
            }
        }).start();
    }

    private void showAbout() {
        int code = 0;
        try {
            code = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Throwable ignored) {
        }
        String msg = "HoopoeTV Box · 手机端\n\n"
                + "包名：" + getPackageName() + "\n"
                + "版本：" + versionText() + "（" + code + "）\n"
                + "界面模式：" + CommonSetting.getUiModeName() + "\n"
                + "接口：" + emptyAsDash(ApiConfig.getApiUrl()) + "\n\n"
                + "本 APP 仅供学习参考使用，为测试教学源代码，请下载后于 24 小时内及时删除。";
        new AlertDialog.Builder(this)
                .setTitle("关于")
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .show();
    }

    /* ===================== 对话框 ===================== */

    private interface IntPicker {
        void onPick(int index);
    }

    private interface TextPicker {
        void onPick(String text);
    }

    /** 中英双语：0=简体中文，1=English；设置页文案统一走这里 */
    private static String tr(String zh, String en) {
        return com.github.tvbox.osc.setting.CommonSetting.getLanguage() == 1 ? en : zh;
    }
    private void chooseInt(String title, String[] names, int checked, final IntPicker picker) {
        if (names == null || names.length == 0) return;
        final int safeChecked = (checked >= 0 && checked < names.length) ? checked : 0;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(names, safeChecked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        picker.onPick(which);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 当前点播源名（json 配置里 sites 的 name，与 TV 设置页「数据源」同源同值） */
    private String homeSourceName() {
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (home == null || TextUtils.isEmpty(home.getName())) return "未设置";
        return home.getName();
    }

    /** 输入框里显示的原始站点名（未设置时留空，避免用户误把「未设置」当名字改） */
    private String homeSourceRawName() {
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (home == null || TextUtils.isEmpty(home.getName())) return "";
        return home.getName();
    }

    /** 按名字切换点播源：先精确匹配，再模糊包含匹配；立即写回并刷新列表 */
    private void switchSourceByName(String text) {
        if (TextUtils.isEmpty(text)) {
            toast(tr("请输入站点名","Enter a site name"));
            return;
        }
        List<SourceBean> all = ApiConfig.get().getSourceBeanList();
        if (all == null || all.isEmpty()) {
            toast(tr("还没有可用数据源，请先填「接口地址」","No sources yet, set the API URL first"));
            return;
        }
        SourceBean exact = null;
        SourceBean fuzzy = null;
        String lower = text.toLowerCase();
        for (SourceBean bean : all) {
            if (bean == null || bean.getHide() == 1 || TextUtils.isEmpty(bean.getName())) continue;
            String name = bean.getName();
            if (name.equals(text)) {
                exact = bean;
                break;
            }
            if (fuzzy == null && name.toLowerCase().contains(lower)) fuzzy = bean;
        }
        SourceBean target = exact != null ? exact : fuzzy;
        if (target == null) {
            toast(tr("没找到「", "No source matched: ") + text + tr("」这个站点", ""));
            return;
        }
        ApiConfig.get().setSourceBean(target);
        toast(tr("已切换到「", "Switched to ") + target.getName() + tr("」，返回首页自动重载",
                "'. Reloads when you go back home"));
        refreshRows();
    }

    

    

    /**
     * 选择点播数据源：列出 json 配置里未隐藏的站点，选中即写入 HawkConfig.HOME_API 并回首页重载。
     * 之前手机端缺这一项，json 里的站点只能靠 TV 端切换，手机端永远停在默认源。
     */
    private void showSourcePicker() {
        List<SourceBean> all = ApiConfig.get().getSourceBeanList();
        final List<SourceBean> shown = new ArrayList<>();
        if (all != null) {
            for (SourceBean bean : all) {
                if (bean != null && bean.getHide() != 1) shown.add(bean);
            }
        }
        if (shown.isEmpty()) {
            toast("还没有可用数据源，请先在「接口地址」填入 json 配置地址");
            return;
        }
        final String[] names = new String[shown.size()];
        int checked = 0;
        String currentKey = ApiConfig.get().getHomeSourceBean() == null ? ""
                : ApiConfig.get().getHomeSourceBean().getKey();
        for (int i = 0; i < shown.size(); i++) {
            names[i] = shown.get(i).getName();
            if (currentKey != null && currentKey.equals(shown.get(i).getKey())) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("数据源")
                .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        SourceBean bean = shown.get(which);
                        ApiConfig.get().setSourceBean(bean);
                        toast("已切换到「" + bean.getName() + "」，返回首页自动重载");
                        refreshRows();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void editText(String title, String current, final TextPicker picker) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        if (!TextUtils.isEmpty(current)) input.setText(current);
        FrameLayout wrap = new FrameLayout(this);
        int pad = (int) (getResources().getDisplayMetrics().density * 16);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        picker.onPick(input.getText().toString().trim());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 存取下标兜底：越界一律拉回区间内，避免胶囊选中项空掉 */
    private int clampInt(Integer v, int min, int max) {
        int i = (v == null) ? min : v;
        return i < min ? min : (i > max ? max : i);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** 切换界面模式：写偏好后清栈重启到 HomeActivity，由 HomeActivity 按模式分流 */
    private void applyUiMode(int mode) {
        if (mode == CommonSetting.getUiMode()) {
            // 已经是这个模式，不重启，只回填高亮
            refreshModeSegment();
            toast("当前已是" + CommonSetting.getUiModeName());
            return;
        }
        CommonSetting.putUiMode(mode);
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    

    

    /** 网络图片：输入图片直链，下载后落到本地再应用（与相册选图走同一条流程） */
    

    

    
}
