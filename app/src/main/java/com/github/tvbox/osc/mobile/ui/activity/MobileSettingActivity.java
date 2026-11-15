package com.github.tvbox.osc.mobile.ui.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
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
import com.github.tvbox.osc.util.BackupHelper;
import com.github.tvbox.osc.util.FavSyncHelper;
import com.github.tvbox.osc.util.SettingsSyncHelper;
import com.github.tvbox.osc.util.CacheHelper;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.XXPermissions;

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
        tvMobile.setTextColor(getResources().getColor(
                mobile ? R.color.shell_text : R.color.shell_text_dim));
        tvTv.setTextColor(getResources().getColor(
                mobile ? R.color.shell_text_dim : R.color.shell_text));
        tvCurrent.setText("当前：" + CommonSetting.getUiModeName() + "，切换后立即重启界面");
    }

    private void refreshRows() {
        List<MobileSettingAdapter.Row> rows = new ArrayList<>();

        // ---------- 通用：数据源（置顶：接口/直播源是最常改的配置） ----------
        // 四项都改成「输入框 + 立即更改」：填完就地生效，不再弹对话框
        rows.add(MobileSettingAdapter.Row.header(tr("通用 · 数据源","General · Data source")));
        // 内置源开关与「从云端拉内置源」入口均已删除，内置源不再由 App 侧下发
        // 数据源保持原来「点击弹站点列表选择」的方式，不做输入框
        rows.add(MobileSettingAdapter.Row.item(tr("数据源","Data source"),
                homeSourceName(), new Runnable() {
                    @Override
                    public void run() {
                        showSourcePicker();
                    }
                }));
        rows.add(MobileSettingAdapter.Row.input(tr("接口地址","API URL"),
                tr("填 TVBox 标准 json 接口地址；改完返回首页会自动重新拉取，不用重启",
                        "TVBox JSON API address; reloads when you go back home"),
                "https://raw.liucn.cc/box/m.json",
                ApiConfig.getApiUrl(), tr("立即更改","Apply"),
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
                "http://example.com/live.m3u",
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
                "http://epg.example.com/?ch={name}&date={date}",
                CommonSetting.getEpgUrl(), tr("立即更改","Apply"),
                new MobileSettingAdapter.OnInput() {
                    @Override
                    public void onInput(MobileSettingAdapter.Row row, String text) {
                        CommonSetting.putEpgUrl(text);
                        toast(tr("EPG 地址已更新","EPG URL saved"));
                        refreshRows();
                    }
                }));

        // ---------- 跨端同步：只保留局域网直连（不经任何服务器/数据库） ----------
        // 云端备份通道的四个入口已移除：同步交给「局域网直连」与电视端账号面板处理
        rows.add(MobileSettingAdapter.Row.header(tr("通用 · 局域网同步", "General · LAN sync")));
        rows.add(MobileSettingAdapter.Row.item(tr("局域网直连同步", "LAN direct sync"),
                tr("同一 WiFi 下直接连电视，不经过任何服务器", "Direct over your Wi-Fi, no server"),
                new Runnable() {
                    @Override
                    public void run() {
                        new com.github.tvbox.osc.ui.dialog.LanSyncDialog(
                                MobileSettingActivity.this).show();
                    }
                }));
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

        // ---------- 通用：播放 ----------
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

        // ---------- 通用 · 存储与关于（审计补缺：原先手机端没有这三项） ----------
        rows.add(MobileSettingAdapter.Row.header(tr("通用 · 存储与关于","General · Storage & About")));
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
