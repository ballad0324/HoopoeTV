package com.github.tvbox.osc.mobile.ui.activity;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.mobile.ui.MobileFavorites;
import com.github.tvbox.osc.mobile.ui.MobilePlayHistory;
import com.github.tvbox.osc.mobile.ui.MobileUser;
import com.github.tvbox.osc.setting.CommonSetting;
import com.github.tvbox.osc.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理面板（v1.6.38）：账号、源模式胶囊（内置源/解析源）、站点切换、
 * 接口地址 / 直播源 / EPG 三项（默认留空）、本地数据清理、版本信息。
 * 入口在首页右上角头像 → 用户面板里的「管理面板」。
 */
public class MobileAdminActivity extends BaseActivity {

    private TextView tvAccount;
    private TextView tvAccountHint;
    private TextView tvSource;
    private TextView tvSourceHint;
    private TextView tvVersion;
    private TextView tvModeBuiltin;
    private TextView tvModeCustom;
    private TextView tvModeHint;
    private EditText etApiUrl;
    private EditText etLiveUrl;
    private EditText etEpgUrl;
    private View llSourceConfig;
    private TextView tvSourceLoginHint;
    private TextView tvCloudHint;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_admin_mobile;
    }

    @Override
    protected void init() {
        tvAccount = findViewById(R.id.tvAccount);
        tvAccountHint = findViewById(R.id.tvAccountHint);
        tvSource = findViewById(R.id.tvSource);
        tvSourceHint = findViewById(R.id.tvSourceHint);
        tvVersion = findViewById(R.id.tvVersion);
        tvModeBuiltin = findViewById(R.id.tvModeBuiltin);
        tvModeCustom = findViewById(R.id.tvModeCustom);
        tvModeHint = findViewById(R.id.tvModeHint);
        etApiUrl = findViewById(R.id.etApiUrl);
        etLiveUrl = findViewById(R.id.etLiveUrl);
        etEpgUrl = findViewById(R.id.etEpgUrl);
        llSourceConfig = findViewById(R.id.llSourceConfig);
        tvSourceLoginHint = findViewById(R.id.tvSourceLoginHint);
        tvCloudHint = findViewById(R.id.tvCloudHint);

        // 源模式胶囊：内置源 / 解析源
        if (tvModeBuiltin != null) {
            tvModeBuiltin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchSourceMode(0);
                }
            });
        }
        if (tvModeCustom != null) {
            tvModeCustom.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchSourceMode(1);
                }
            });
        }

        // 三个地址块：默认留空，保存即生效
        findViewById(R.id.tvSaveApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = textOf(etApiUrl);
                if (TextUtils.isEmpty(url)) {
                    toast("接口地址为空，未保存");
                    return;
                }
                CommonSetting.putApiUrl(url);
                CommonSetting.markConfigDirty();
                toast("接口地址已保存，返回首页自动重载");
                refresh();
            }
        });
        findViewById(R.id.tvSaveLive).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommonSetting.putLiveUrl(textOf(etLiveUrl));
                toast(TextUtils.isEmpty(textOf(etLiveUrl)) ? "直播源已清空" : "直播源已保存");
                refresh();
            }
        });
        findViewById(R.id.tvSaveEpg).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommonSetting.putEpgUrl(textOf(etEpgUrl));
                toast(TextUtils.isEmpty(textOf(etEpgUrl)) ? "EPG 地址已清空" : "EPG 地址已保存");
                refresh();
            }
        });

        findViewById(R.id.tvBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        View rowAccount = findViewById(R.id.rowAccount);
        if (rowAccount != null) {
            rowAccount.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (MobileUser.isLoggedIn()) {
                        confirmLogout();
                    } else {
                        toast("请在首页右上角头像里登录，登录后自动切到内置源");
                    }
                }
            });
        }

        View rowSource = findViewById(R.id.rowSource);
        if (rowSource != null) {
            rowSource.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSourcePicker();
                }
            });
        }

        View rowCloud = findViewById(R.id.rowCloud);
        if (rowCloud != null) {
            rowCloud.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new android.content.Intent(MobileAdminActivity.this,
                            MobileCloudBackupActivity.class));
                }
            });
        }

        View rowClearHistory = findViewById(R.id.rowClearHistory);
        if (rowClearHistory != null) {
            rowClearHistory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirm("清空播放历史", "确定清空全部播放记录？", new Runnable() {
                        @Override
                        public void run() {
                            MobilePlayHistory.clear();
                            toast("播放历史已清空");
                        }
                    });
                }
            });
        }

        View rowClearFav = findViewById(R.id.rowClearFav);
        if (rowClearFav != null) {
            rowClearFav.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirm("清空我的收藏", "确定清空全部收藏？", new Runnable() {
                        @Override
                        public void run() {
                            List<MobileFavorites.Item> list = MobileFavorites.load();
                            if (list != null) {
                                for (MobileFavorites.Item won : list) {
                                    MobileFavorites.remove(won.vodId, won.sourceKey);
                                }
                            }
                            toast("收藏已清空");
                        }
                    });
                }
            });
        }

        View rowClearCache = findViewById(R.id.rowClearCache);
        if (rowClearCache != null) {
            rowClearCache.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirm("清理本地缓存", "会清掉接口缓存与临时文件，不会删除下载的视频。继续？", new Runnable() {
                        @Override
                        public void run() {
                            int freed = clearCache();
                            toast("已清理 " + (freed / 1024) + " KB 缓存");
                        }
                    });
                }
            });
        }

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean logged = MobileUser.isLoggedIn();
        if (MobileUser.isLoggedIn()) {
            tvAccount.setText("已登录：" + MobileUser.name());
            tvAccountHint.setText("点击可退出登录；登录后默认走内置源");
        } else {
            tvAccount.setText("未登录");
            tvAccountHint.setText("当前使用你自己填写的接口；登录后默认切到内置源");
        }

        // 未登录：整块（胶囊 + 接口/直播/EPG）先隐藏，只留一行提示；设置页那三行给未登录用户用
        if (llSourceConfig != null) {
            llSourceConfig.setVisibility(logged ? View.VISIBLE : View.GONE);
        }
        if (tvSourceLoginHint != null) {
            tvSourceLoginHint.setVisibility(logged ? View.GONE : View.VISIBLE);
        }

        boolean builtin = CommonSetting.useBuiltinSource();
        // 未登录且没填过接口时，不要拿内置地址冒充「解析源」，否则标题和地址自相矛盾
        String stored = CommonSetting.getApiUrlStored();
        String api = builtin ? ApiConfig.getDefaultApiUrl()
                : (TextUtils.isEmpty(stored) ? "" : stored);
        String home = "";
        SourceBean bean = ApiConfig.get().getHomeSourceBean();
        if (bean != null && !TextUtils.isEmpty(bean.getName())) home = bean.getName();
        tvSource.setText(builtin ? "内置源" : "解析源（自填）");
        if (!TextUtils.isEmpty(home)) tvSource.setText(tvSource.getText() + " · " + home);
        if (TextUtils.isEmpty(api)) {
            tvSourceHint.setText(builtin
                    ? "还没有可用的接口地址"
                    : "还没填接口地址：可在设置页填写，或登录后使用内置源");
        } else {
            tvSourceHint.setText(api);
        }

        applyModeUi(builtin);

        // 三个地址块回填当前值（没填过就是空）
        if (etApiUrl != null) etApiUrl.setText(CommonSetting.getApiUrlStored());
        if (etLiveUrl != null) etLiveUrl.setText(CommonSetting.getLiveUrl());
        if (etEpgUrl != null) etEpgUrl.setText(CommonSetting.getEpgUrl());

        if (tvCloudHint != null) {
            String hint = "";
            try {
                hint = com.github.tvbox.osc.util.CloudBackupHelper.summary();
            } catch (Throwable ignored) {
            }
            tvCloudHint.setText(TextUtils.isEmpty(hint) ? "备份到 MySQL 云端 / 从云端恢复" : hint);
        }

        try {
            String ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int code = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            tvVersion.setText("HoopoeTV " + ver + "（versionCode " + code + "）");
        } catch (Throwable ignored) {
            tvVersion.setText("");
        }
    }

    /** 胶囊选中态：选中的那侧白底 + primary 文字，另一侧透明 + dim 文字 */
    private void applyModeUi(boolean builtin) {
        int onText = getResources().getColor(R.color.shell_primary);
        int offText = getResources().getColor(R.color.shell_text_dim);
        if (tvModeBuiltin != null) {
            tvModeBuiltin.setBackgroundResource(builtin ? R.drawable.shape_home_segment_on : 0);
            tvModeBuiltin.setTextColor(builtin ? onText : offText);
            tvModeBuiltin.setTypeface(null, builtin ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
        }
        if (tvModeCustom != null) {
            tvModeCustom.setBackgroundResource(builtin ? 0 : R.drawable.shape_home_segment_on);
            tvModeCustom.setTextColor(builtin ? offText : onText);
            tvModeCustom.setTypeface(null, builtin ? android.graphics.Typeface.NORMAL
                    : android.graphics.Typeface.BOLD);
        }
        if (tvModeHint != null) {
            tvModeHint.setText(builtin
                    ? "当前用内置源（App 自带）；切到「解析源」后使用下面填的接口地址"
                    : "当前用解析源（你自己填的接口）；切回「内置源」后用 App 自带源");
        }
    }

    /** 胶囊切换：写入源模式后立即刷新生效源，返回首页时会重载分类 */
    private void switchSourceMode(int mode) {
        CommonSetting.putSourceModeIndex(mode);
        if (mode == 1) {
            String stored = CommonSetting.getApiUrlStored();
            if (TextUtils.isEmpty(stored)) {
                toast("解析源还没填接口地址，先在下面「接口地址」里填一个");
            }
        }
        CommonSetting.markConfigDirty();
        toast(mode == 0 ? "已切到内置源" : "已切到解析源");
        refresh();
    }

    private String textOf(EditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void confirm(String title, String msg, final Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        action.run();
                        refresh();
                    }
                })
                .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("退出后默认改用你自己填的解析源，确定退出？")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MobileUser.logout();
                        toast("已退出登录，返回首页后使用自填源");
                        refresh();
                    }
                })
                .show();
    }

    /** 切换点播站点（与设置页同一逻辑：写入 HOME_API 后回首页自动重载） */
    private void showSourcePicker() {
        if (CommonSetting.useBuiltinSource()) {
            toast("当前是内置源，切到「解析源」后可选站点");
            return;
        }
        List<SourceBean> all = ApiConfig.get().getSourceBeanList();
        final List<SourceBean> shown = new ArrayList<>();
        if (all != null) {
            for (SourceBean bean : all) {
                if (bean != null && bean.getHide() != 1) shown.add(bean);
            }
        }
        if (shown.isEmpty()) {
            toast("还没有可用站点，请先在设置里填「接口地址」");
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
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 清理 cacheDir，返回释放的字节数 */
    private int clearCache() {
        int total = 0;
        try {
            File dir = getCacheDir();
            if (dir != null && dir.exists()) {
                total += sizeOf(dir);
                File[] kids = dir.listFiles();
                if (kids != null) {
                    for (File f : kids) {
                        FileUtils.recursiveDelete(f);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return total;
    }

    private int sizeOf(File dir) {
        int total = 0;
        File[] kids = dir.listFiles();
        if (kids == null) return 0;
        for (File f : kids) {
            total += f.isDirectory() ? sizeOf(f) : (int) f.length();
        }
        return total;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
