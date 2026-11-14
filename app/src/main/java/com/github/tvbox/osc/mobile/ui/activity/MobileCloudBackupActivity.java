package com.github.tvbox.osc.mobile.ui.activity;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.mobile.ui.MobileUser;
import com.github.tvbox.osc.util.BackupHelper;
import com.github.tvbox.osc.util.CloudBackupHelper;

/**
 * 云备份（MySQL）：v1.6.37 从设置页搬到这里，入口在首页右上角用户面板。
 *
 * 账号就是登录用的那套账号（server/index.php login），已登录时地址/用户名/密码已经存好，
 * 进来直接点「上传 / 下载」即可；未登录也能单独填一份云备份账号。
 */
public class MobileCloudBackupActivity extends BaseActivity {

    private EditText etUrl;
    private EditText etUser;
    private EditText etPass;
    private TextView tvStatus;
    private TextView tvAccountHint;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_cloud_backup_mobile;
    }

    @Override
    protected void init() {
        etUrl = findViewById(R.id.etCloudUrl);
        etUser = findViewById(R.id.etCloudUser);
        etPass = findViewById(R.id.etCloudPass);
        tvStatus = findViewById(R.id.tvCloudStatus);
        tvAccountHint = findViewById(R.id.tvAccountHint);

        if (etUrl != null) etUrl.setText(CloudBackupHelper.getBaseUrl());
        if (etUser != null) etUser.setText(CloudBackupHelper.getUser());
        if (etPass != null) etPass.setText(CloudBackupHelper.getPass());
        if (tvStatus != null) tvStatus.setText(CloudBackupHelper.summary());

        findViewById(R.id.tvBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnCloudSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                status("正在连接…");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        CloudBackupHelper.Res ping = CloudBackupHelper.ping();
                        CloudBackupHelper.Res login = ping.ok ? CloudBackupHelper.login() : ping;
                        final String msg = ping.ok
                                ? (login.ok ? "连接正常，账号密码已验证" : "服务器可达，但登录失败：" + login.msg)
                                : "连接失败：" + ping.msg;
                        statusOnUi(msg);
                    }
                }).start();
            }
        });
        findViewById(R.id.btnCloudPush).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                doPush();
            }
        });
        findViewById(R.id.btnCloudPull).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                doPull(null);
            }
        });
        findViewById(R.id.btnCloudList).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                showList();
            }
        });

        if (tvAccountHint != null) {
            tvAccountHint.setText(MobileUser.isLoggedIn()
                    ? ("当前已登录：" + MobileUser.name() + "，云备份与账号共用同一份密码")
                    : "未登录：这里可以单独填一份云备份账号");
        }
    }

    private void save() {
        CloudBackupHelper.save(
                etUrl == null ? "" : etUrl.getText().toString(),
                etUser == null ? "" : etUser.getText().toString(),
                etPass == null ? "" : etPass.getText().toString());
        status("已保存：" + CloudBackupHelper.summary());
    }

    private void status(final String s) {
        if (tvStatus != null) tvStatus.setText(s);
    }

    private void statusOnUi(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                status(s);
            }
        });
    }

    private void doPush() {
        if (!CloudBackupHelper.isConfigured()) {
            toast("请先填写地址、用户名、密码");
            return;
        }
        final String latest = BackupHelper.list().isEmpty() ? null : BackupHelper.list().get(0);
        status("打包中…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String name = latest;
                if (name == null) name = BackupHelper.backup(MobileCloudBackupActivity.this);
                if (name == null) {
                    statusOnUi("本地备份失败，请检查存储空间");
                    return;
                }
                statusOnUi("上传中（" + name + "）…");
                CloudBackupHelper.Res res = CloudBackupHelper.push(name);
                statusOnUi(res.ok ? "已上传到云端：" + name : "上传失败：" + res.msg);
            }
        }).start();
    }

    private void doPull(final String remoteName) {
        if (!CloudBackupHelper.isConfigured()) {
            toast("请先填写地址、用户名、密码");
            return;
        }
        status("拉取中…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                CloudBackupHelper.Res res = CloudBackupHelper.pull(remoteName);
                if (!res.ok) {
                    statusOnUi("拉取失败：" + res.msg);
                    return;
                }
                final String localName = res.msg;
                statusOnUi("已下载到本地：" + localName);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(MobileCloudBackupActivity.this)
                                .setTitle("恢复这份云端备份？")
                                .setMessage(localName + "\n恢复会覆盖当前收藏、历史与设置，完成后 App 自动重启。")
                                .setPositiveButton("恢复", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        doRestore(localName);
                                    }
                                })
                                .setNegativeButton("先放着", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    private void showList() {
        if (!CloudBackupHelper.isConfigured()) {
            toast("请先填写地址、用户名、密码");
            return;
        }
        status("读取云端列表…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                CloudBackupHelper.Res res = CloudBackupHelper.listRemote();
                if (!res.ok) {
                    statusOnUi("读取失败：" + res.msg);
                    return;
                }
                final org.json.JSONArray items = res.data.optJSONArray("items");
                final int count = items == null ? 0 : items.length();
                final String[] names = new String[count];
                final String[] labels = new String[count];
                for (int i = 0; i < count; i++) {
                    org.json.JSONObject o = items.optJSONObject(i);
                    names[i] = o.optString("name", "");
                    labels[i] = names[i] + "  " + o.optString("size_text", "") + "  " + o.optString("time", "");
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        status("云端共 " + count + " 份");
                        if (count == 0) {
                            toast("云端还没有备份");
                            return;
                        }
                        new AlertDialog.Builder(MobileCloudBackupActivity.this)
                                .setTitle("云端备份")
                                .setItems(labels, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
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

    private void doRestore(final String name) {
        status("恢复中…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int result = BackupHelper.restore(MobileCloudBackupActivity.this, name);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result == BackupHelper.OK) {
                            toast("恢复成功，3 秒后重启");
                            new android.os.Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    BackupHelper.restartApp(MobileCloudBackupActivity.this);
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

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
