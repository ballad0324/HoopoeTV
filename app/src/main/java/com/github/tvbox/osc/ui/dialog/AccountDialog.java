package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.mobile.ui.MobileUser;
import com.github.tvbox.osc.util.BackupHelper;
import com.github.tvbox.osc.util.CloudBackupHelper;
import com.github.tvbox.osc.util.FavSyncHelper;
import com.github.tvbox.osc.util.SettingsSyncHelper;

/**
 * 电视端「账号与云备份」面板（填补 TV 端原先没有的账号体系）。
 * 与手机端同一套 PHP 账号与云备份服务：服务器地址与用户名内置，遥控器只需输入密码，
 * 登录成功后自动读取云端列表 → 选择一份 → 下载并恢复。
 */
public class AccountDialog {

    /** 内置账号：与手机端登录用的是同一个（CloudBackupHelper 里保存过就以保存的为准） */
    private static final String DEFAULT_USER = "ballad0324";

    private final Activity act;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private TextView tvState;
    private TextView tvTip;
    private TextView tvUser;
    private EditText etPass;

    public AccountDialog(Activity act) {
        this.act = act;
    }

    public void show() {
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_tv_account, null);
        tvState = (TextView) v.findViewById(R.id.tvAccountState);
        tvTip = (TextView) v.findViewById(R.id.tvAccountTip);
        tvUser = (TextView) v.findViewById(R.id.tvAccountUser);
        etPass = (EditText) v.findViewById(R.id.etAccountPass);
        if (tvUser != null) tvUser.setText("账号：" + currentUser() + "（与手机端同一个）");
        v.findViewById(R.id.btnAccountPull).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loginThenPull();
            }
        });
        v.findViewById(R.id.btnAccountPush).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doPush();
            }
        });
        v.findViewById(R.id.btnAccountClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (dialog != null) dialog.dismiss();
            }
        });
        v.findViewById(R.id.btnSettingsPush).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doSettingsPush();
            }
        });
        v.findViewById(R.id.btnSettingsPull).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askSettingsPull();
            }
        });
        v.findViewById(R.id.btnFavPush).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doFavPush();
            }
        });
        v.findViewById(R.id.btnFavPull).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doFavPull();
            }
        });
        dialog = new AlertDialog.Builder(act).setView(v).create();
        dialog.show();
        refreshState();
        if (etPass != null) etPass.requestFocus();
    }

    /** 账号：优先用云端配置里保存的用户名，其次手机端登录名，最后内置默认 */
    private String currentUser() {
        String u = CloudBackupHelper.getUser();
        if (!TextUtils.isEmpty(u)) return u;
        String n = MobileUser.name();
        if (!TextUtils.isEmpty(n)) return n;
        return DEFAULT_USER;
    }

    private void refreshState() {
        if (tvState == null) return;
        if (MobileUser.isLoggedIn()) {
            tvState.setText("已登录：" + MobileUser.name() + "，可直接上传或拉取备份");
        } else if (CloudBackupHelper.isConfigured()) {
            tvState.setText("未登录：输入密码即可登录并拉取云备份");
        } else {
            tvState.setText("未登录：输入密码即可登录并拉取云备份");
        }
    }

    private void tip(final String s) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (tvTip != null) tvTip.setText(s);
            }
        });
    }

    private void toast(final String s) {
        main.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(act, s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String readPass() {
        return etPass == null || etPass.getText() == null ? "" : etPass.getText().toString().trim();
    }

    /** 主流程：只输密码 → 登录 → 自动拉取云端列表 */
    private void loginThenPull() {
        final String pass = readPass();
        if (MobileUser.isLoggedIn() && TextUtils.isEmpty(pass)) {
            tip("已登录，正在读取云端备份…");
            doRemoteList();
            return;
        }
        if (TextUtils.isEmpty(pass)) {
            tip("请输入密码");
            return;
        }
        final String user = currentUser();
        tip("正在登录 " + user + " …");
        MobileUser.login(CloudBackupHelper.getBaseUrl(), user, pass, new MobileUser.LoginCallback() {
            @Override
            public void onDone(boolean ok, String msg) {
                refreshState();
                if (!ok) {
                    tip("登录失败：" + msg);
                    toast("登录失败，请检查密码");
                    return;
                }
                toast("登录成功");
                tip("登录成功，正在读取云端备份…");
                if (etPass != null) etPass.setText("");
                doRemoteList();
            }
        });
    }

    /** 上传本机设置到云端（接口/主题/播放器等白名单项，不含数据库） */
    private void doSettingsPush() {
        tip("正在上传本机设置…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final CloudBackupHelper.Res res = SettingsSyncHelper.push();
                tip(res.ok ? res.msg + "（手机端可拉回）" : "上传失败：" + res.msg);
            }
        }).start();
    }

    private void askSettingsPull() {
        new AlertDialog.Builder(act)
                .setTitle("拉回云端设置？")
                .setMessage("会用云端的接口地址、主题、播放器等设置覆盖本机，完成后 App 自动重启。\n不会覆盖收藏、播放历史和账号。")
                .setPositiveButton("拉回", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        doSettingsPull();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doSettingsPull() {
        tip("正在拉回云端设置…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final CloudBackupHelper.Res res = SettingsSyncHelper.pull();
                if (!res.ok) {
                    tip("拉回失败：" + res.msg);
                    return;
                }
                tip(res.msg + "，3 秒后重启");
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        BackupHelper.restartApp(act);
                    }
                }, 3000);
            }
        }).start();
    }
    /* ===================== 收藏跨端同步（电视 ⇄ 手机） ===================== */

    private void doFavPush() {
        tip("正在上传本机收藏…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final CloudBackupHelper.Res res = FavSyncHelper.push();
                tip(res.ok ? res.msg + "（手机端可拉回）" : "上传失败：" + res.msg);
            }
        }).start();
    }

    private void doFavPull() {
        tip("正在拉回云端收藏…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final CloudBackupHelper.Res res = FavSyncHelper.pull();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        tip(res.ok ? res.msg : "拉回失败：" + res.msg);
                    }
                });
            }
        }).start();
    }

    private void doPush() {
        if (!MobileUser.isLoggedIn() && !CloudBackupHelper.isConfigured()) {
            tip("请先输入密码登录");
            return;
        }
        tip("正在打包…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String name = BackupHelper.list().isEmpty() ? null : BackupHelper.list().get(0);
                if (name == null) name = BackupHelper.backup(act);
                if (name == null) {
                    tip("本地备份失败，检查存储空间");
                    return;
                }
                tip("正在上传 " + name + " …");
                CloudBackupHelper.Res res = CloudBackupHelper.push(name);
                tip(res.ok ? "已上传到云端：" + name : "上传失败：" + res.msg);
            }
        }).start();
    }

    private void doRemoteList() {
        if (!MobileUser.isLoggedIn()) {
            tip("请先输入密码登录");
            return;
        }
        tip("正在读取云端列表…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final CloudBackupHelper.Res res = CloudBackupHelper.listRemote();
                if (!res.ok) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            tip("读取失败：" + res.msg + "（密码登录态可能已过期，重新输入密码试试）");
                        }
                    });
                    return;
                }
                final org.json.JSONArray items = res.data.optJSONArray("items");
                final int n = items == null ? 0 : items.length();
                final String[] names = new String[n];
                final String[] labels = new String[n];
                for (int i = 0; i < n; i++) {
                    org.json.JSONObject o = items.optJSONObject(i);
                    names[i] = o.optString("name", "");
                    labels[i] = names[i] + "  " + o.optString("size_text", "") + "  " + o.optString("time", "");
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (n == 0) {
                            tip("云端还没有备份，可先点「上传当前备份」");
                            return;
                        }
                        tip("云端共 " + n + " 份备份，选择一份恢复");
                        new AlertDialog.Builder(act)
                                .setTitle("云端备份（" + n + " 份）")
                                .setItems(labels, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        doPull(names[which]);
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    private void doPull(final String remoteName) {
        tip("正在下载 " + remoteName + " …");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final CloudBackupHelper.Res res = CloudBackupHelper.pull(remoteName);
                if (!res.ok) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            tip("下载失败：" + res.msg);
                        }
                    });
                    return;
                }
                final String localName = res.msg;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        tip("已下载：" + localName);
                        new AlertDialog.Builder(act)
                                .setTitle("恢复这份云端备份？")
                                .setMessage(localName + "\n恢复会覆盖当前收藏、历史与设置，完成后 App 自动重启。")
                                .setPositiveButton("恢复", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        restore(localName);
                                    }
                                })
                                .setNegativeButton("先放着", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    private void restore(final String name) {
        tip("正在恢复…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int result = BackupHelper.restore(act, name);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result == BackupHelper.OK) {
                            tip("恢复成功，3 秒后重启");
                            main.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    BackupHelper.restartApp(act);
                                }
                            }, 3000);
                        } else if (result == BackupHelper.ERR_DB) {
                            tip("恢复失败：数据库还原出错");
                        } else if (result == BackupHelper.ERR_HAWK) {
                            tip("恢复失败：设置项文件缺失或损坏");
                        } else {
                            tip("恢复失败：备份不存在或已损坏");
                        }
                    }
                });
            }
        }).start();
    }
}
