package com.github.tvbox.osc.mobile.ui;

import android.os.Handler;
import android.os.Looper;

import com.github.tvbox.osc.util.CloudBackupHelper;
import com.orhanobut.hawk.Hawk;

/**
 * 手机端账号状态（v1.6.36）。
 *
 * 账号体系直接复用「云端备份」那套 PHP + MySQL 账号（服务端 index.php 的 login 接口），
 * App 端不直连 3306，只在本地存 token 与用户名，因此不需要给 MySQL 加新表：
 *  - 登录成功 → 记 token + 用户名，全局进入「已登录」；
 *  - 未登录 → token 为空。
 *
 * 规则（写进 ApiConfig.getApiUrl）：登录用户固定走内置源；未登录走用户自填接口。
 */
public final class MobileUser {

    private static final String K_TOKEN = "mobile_user_token";
    private static final String K_NAME = "mobile_user_name";

    private MobileUser() {
    }

    public static boolean isLoggedIn() {
        String token = Hawk.get(K_TOKEN, "");
        return token != null && !token.trim().isEmpty();
    }

    public static String name() {
        String name = Hawk.get(K_NAME, "");
        return name == null ? "" : name;
    }

    public static String token() {
        String token = Hawk.get(K_TOKEN, "");
        return token == null ? "" : token;
    }

    /** 登录结果回调（已在主线程） */
    public interface LoginCallback {
        void onDone(boolean ok, String msg);
    }

    /**
     * 登录：把地址/账号/密码写进云端备份配置后调 login，成功后本地记 token。
     * 网络请求放子线程，回调切回主线程。
     */
    public static void login(final String baseUrl, final String user, final String pass,
                             final LoginCallback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String msg;
                final boolean ok;
                try {
                    CloudBackupHelper.save(baseUrl, user, pass);
                    CloudBackupHelper.Res res = CloudBackupHelper.login();
                    if (res != null && res.ok) {
                        String token = CloudBackupHelper.getToken();
                        Hawk.put(K_TOKEN, token == null ? "" : token);
                        Hawk.put(K_NAME, user == null ? "" : user.trim());
                        ok = true;
                        msg = "登录成功";
                    } else {
                        ok = false;
                        msg = res == null ? "登录失败" : res.msg;
                    }
                } catch (Throwable t) {
                    final String err = t.getMessage();
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) callback.onDone(false, "网络失败：" + err);
                        }
                    });
                    return;
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onDone(ok, msg);
                    }
                });
            }
        }).start();
    }

    /** 注册：用户名 + 密码 + 邀请码；服务端校验通过后直接记 token（等同登录） */
    public static void register(final String baseUrl, final String user, final String pass,
                                final String invite, final LoginCallback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String msg;
                final boolean ok;
                try {
                    CloudBackupHelper.Res res = CloudBackupHelper.register(baseUrl, user, pass, invite);
                    if (res != null && res.ok) {
                        String token = CloudBackupHelper.getToken();
                        Hawk.put(K_TOKEN, token == null ? "" : token);
                        Hawk.put(K_NAME, user == null ? "" : user.trim());
                        ok = true;
                        msg = "注册成功";
                    } else {
                        ok = false;
                        msg = res == null ? "注册失败" : res.msg;
                    }
                } catch (Throwable t) {
                    final String err = t.getMessage();
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) callback.onDone(false, "网络失败：" + err);
                        }
                    });
                    return;
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onDone(ok, msg);
                    }
                });
            }
        }).start();
    }

    /** 退出登录：清掉 token（地址与账号密码保留，方便下次一键再登） */
    public static void logout() {
        Hawk.put(K_TOKEN, "");
        Hawk.put(K_NAME, "");
        CloudBackupHelper.clearToken();
    }
}
