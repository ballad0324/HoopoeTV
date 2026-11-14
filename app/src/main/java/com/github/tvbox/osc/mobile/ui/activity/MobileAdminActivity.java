package com.github.tvbox.osc.mobile.ui.activity;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.View;
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
import com.github.tvbox.osc.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理面板（v1.6.36）：账号状态、数据源切换、本地数据清理、版本信息。
 * 入口在首页右上角头像 → 用户面板里的「管理面板」。
 */
public class MobileAdminActivity extends BaseActivity {

    private TextView tvAccount;
    private TextView tvAccountHint;
    private TextView tvSource;
    private TextView tvSourceHint;
    private TextView tvVersion;

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
        if (MobileUser.isLoggedIn()) {
            tvAccount.setText("已登录：" + MobileUser.name());
            tvAccountHint.setText("当前固定使用内置源，点击可退出登录");
        } else {
            tvAccount.setText("未登录");
            tvAccountHint.setText("当前使用你自己填写的接口；登录后切到内置源");
        }

        boolean logged = MobileUser.isLoggedIn();
        String api = ApiConfig.getApiUrl();
        String home = "";
        SourceBean bean = ApiConfig.get().getHomeSourceBean();
        if (bean != null && !TextUtils.isEmpty(bean.getName())) home = bean.getName();
        tvSource.setText((logged ? "内置源" : "自填源") + (TextUtils.isEmpty(home) ? "" : " · " + home));
        tvSourceHint.setText(TextUtils.isEmpty(api) ? "还没有可用的接口地址" : api);

        try {
            String ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int code = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            tvVersion.setText("HoopoeTV " + ver + "（versionCode " + code + "）");
        } catch (Throwable ignored) {
            tvVersion.setText("");
        }
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
                .setMessage("退出后将改回使用你自己填的接口，确定退出？")
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
        if (MobileUser.isLoggedIn()) {
            toast("已登录固定使用内置源，退出登录后才能切换站点");
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
