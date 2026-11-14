package com.github.tvbox.osc.util;

import android.text.TextUtils;
import android.util.Base64;

import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 云端备份（MySQL）：把本地 tvbox_backup 的备份目录打成 zip → Base64 → POST 给
 * MacCMS 那台服务器上的 PHP 接口，落到 MySQL 的 LONGTEXT 字段里。
 *
 * App 从不直连 MySQL：
 *  - 安卓直连 3306 要把库账号密码塞进 APK，反编译即泄露；
 *  - 多数云主机 3306 不对公网开放，或有防火墙/白名单；
 *  - JDBC 驱动会额外增加包体，且没有事务与重试控制。
 * 所以统一走 HTTPS + PHP 接口，MySQL 只在服务端本地被 PHP 访问。
 *
 * 接口约定（服务端 index.php，action 走 query）：
 *  ping    无鉴权           -> {ok:1,ver:"1.0"}
 *  login   user/pass        -> {ok:1,token:"..."}
 *  list    token            -> {ok:1,items:[{name,size,time}]}
 *  push    token/name/payload(b64) -> {ok:1,name}
 *  pull    token/name(可空) -> {ok:1,name,payload(b64)}
 *  delete  token/name       -> {ok:1}
 *  sources token            -> {ok:1,api,key,epg}
 */
public final class CloudBackupHelper {

    private static final String K_URL = "cloud_bk_url";
    private static final String K_USER = "cloud_bk_user";
    private static final String K_PASS = "cloud_bk_pass";
    private static final String K_TOKEN = "cloud_bk_token";

    private static final long MAX_UPLOAD_BYTES = 8L * 1024 * 1024;

    private CloudBackupHelper() {
    }

    /* ===================== 配置 ===================== */

    public static String getBaseUrl() {
        String s = Hawk.get(K_URL, "");
        return s == null ? "" : s.trim();
    }

    public static String getUser() {
        String s = Hawk.get(K_USER, "");
        return s == null ? "" : s.trim();
    }

    public static String getPass() {
        String s = Hawk.get(K_PASS, "");
        return s == null ? "" : s;
    }

    public static boolean isConfigured() {
        return !TextUtils.isEmpty(getBaseUrl()) && !TextUtils.isEmpty(getUser()) && !TextUtils.isEmpty(getPass());
    }

    public static void save(String url, String user, String pass) {
        Hawk.put(K_URL, url == null ? "" : url.trim());
        Hawk.put(K_USER, user == null ? "" : user.trim());
        Hawk.put(K_PASS, pass == null ? "" : pass);
        // 账号或地址一变，旧 token 立即作废，下次操作重新登录
        Hawk.put(K_TOKEN, "");
    }

    public static String getToken() {
        String s = Hawk.get(K_TOKEN, "");
        return s == null ? "" : s;
    }

    public static void clearToken() {
        Hawk.put(K_TOKEN, "");
    }

    /* ===================== 结果 ===================== */

    public static class Res {
        public boolean ok;
        public String msg = "";
        public JSONObject data;

        static Res fail(String m) {
            Res r = new Res();
            r.ok = false;
            r.msg = m == null ? "未知错误" : m;
            return r;
        }
    }

    /* ===================== 网络 ===================== */

    private static String endpoint(String action) {
        String base = getBaseUrl();
        if (TextUtils.isEmpty(base)) return "";
        if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://" + base;
        if (base.contains("index.php")) {
            return base + (base.contains("?") ? "&" : "?") + "action=" + action;
        }
        if (!base.endsWith("/")) base = base + "/";
        return base + "index.php?action=" + action;
    }

    private static OkHttpClient client() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build();
    }

    private static Res post(String action, FormBody.Builder form) {
        String url = endpoint(action);
        if (TextUtils.isEmpty(url)) return Res.fail("没有填写服务器地址");
        try {
            Request request = new Request.Builder().url(url).post(form.build()).build();
            Response response = client().newCall(request).execute();
            String body = response.body() == null ? "" : response.body().string();
            response.close();
            if (TextUtils.isEmpty(body)) return Res.fail("服务器没有返回内容(HTTP " + response.code() + ")");
            JSONObject json = new JSONObject(body.trim());
            Res r = new Res();
            r.ok = json.optInt("ok", 0) == 1 || json.optBoolean("ok", false);
            r.msg = json.optString("msg", "");
            r.data = json;
            return r;
        } catch (Throwable t) {
            return Res.fail("网络失败：" + t.getMessage());
        }
    }

    /* ===================== 接口 ===================== */

    public static Res ping() {
        return post("ping", new FormBody.Builder());
    }

    public static Res login() {
        if (TextUtils.isEmpty(getBaseUrl())) return Res.fail("请先填服务器地址");
        if (TextUtils.isEmpty(getUser())) return Res.fail("请先填用户名");
        Res r = post("login", new FormBody.Builder()
                .add("user", getUser())
                .add("pass", getPass()));
        if (r.ok) Hawk.put(K_TOKEN, r.data == null ? "" : r.data.optString("token", ""));
        return r;
    }

    private static boolean ensureToken() {
        if (!TextUtils.isEmpty(getToken())) return true;
        return login().ok;
    }

    /** 注册（v1.7）：用户名 + 密码 + 邀请码；成功后直接拿到 token，等同登录 */
    public static Res register(String url, String user, String pass, String invite) {
        if (TextUtils.isEmpty(url)) return Res.fail("请先填服务器地址");
        save(url, user, pass);
        Res r = post("register", new FormBody.Builder()
                .add("user", user == null ? "" : user.trim())
                .add("pass", pass == null ? "" : pass)
                .add("invite", invite == null ? "" : invite.trim()));
        if (r.ok) {
            Hawk.put(K_TOKEN, r.data == null ? "" : r.data.optString("token", ""));
        }
        return r;
    }

    /** 登录用户把自己的 接口 / 直播 / EPG 推到云端（换手机自动带回来） */
    public static Res saveSources(String api, String live, String epg) {
        if (!ensureToken()) return Res.fail("登录已失效");
        return post("save_sources", new FormBody.Builder()
                .add("token", getToken())
                .add("api", api == null ? "" : api.trim())
                .add("live", live == null ? "" : live.trim())
                .add("epg", epg == null ? "" : epg.trim()));
    }

    /** 播放记录上云 */
    public static Res pushHistory(JSONArray items) {
        if (!ensureToken()) return Res.fail("登录已失效");
        return post("history_push", new FormBody.Builder()
                .add("token", getToken())
                .add("items", items == null ? "[]" : items.toString()));
    }

    /** 拉云端播放记录 */
    public static Res pullHistory(int limit) {
        if (!ensureToken()) return Res.fail("登录已失效");
        return post("history_pull", new FormBody.Builder()
                .add("token", getToken())
                .add("limit", String.valueOf(limit)));
    }

    /** 远端已有备份列表 */
    public static Res listRemote() {
        if (!isConfigured()) return Res.fail("还没配置云端账号");
        if (!ensureToken()) return Res.fail("登录失败，请检查用户名/密码");
        return post("list", new FormBody.Builder().add("token", getToken()));
    }

    /** 上传一份本地备份（dirName 就是 tvbox_backup 下的目录名） */
    public static Res push(String dirName) {
        if (TextUtils.isEmpty(dirName)) return Res.fail("本地没有可上传的备份");
        if (!isConfigured()) return Res.fail("还没配置云端账号");
        File dir = BackupHelper.dirOf(dirName);
        if (!dir.exists()) return Res.fail("本地备份不存在：" + dirName);
        try {
            byte[] zip = zipDir(dir);
            if (zip.length > MAX_UPLOAD_BYTES) {
                return Res.fail("备份太大(" + (zip.length / 1024 / 1024) + "MB)，服务端上限 8MB");
            }
            if (!ensureToken()) return Res.fail("登录失败，请检查用户名/密码");
            Res r = post("push", new FormBody.Builder()
                    .add("token", getToken())
                    .add("name", dirName)
                    .add("payload", Base64.encodeToString(zip, Base64.NO_WRAP)));
            return r;
        } catch (Throwable t) {
            return Res.fail("打包失败：" + t.getMessage());
        }
    }

    /** 拉取远端最新（或指定）备份，解到本地 tvbox_backup 下，返回目录名 */
    public static Res pull(String remoteName) {
        if (!isConfigured()) return Res.fail("还没配置云端账号");
        if (!ensureToken()) return Res.fail("登录失败，请检查用户名/密码");
        FormBody.Builder form = new FormBody.Builder().add("token", getToken());
        if (!TextUtils.isEmpty(remoteName)) form.add("name", remoteName);
        Res r = post("pull", form);
        if (!r.ok || r.data == null) return r;
        String name = r.data.optString("name", remoteName);
        String payload = r.data.optString("payload", "");
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(payload)) return Res.fail("服务器返回的备份为空");
        try {
            byte[] zip = Base64.decode(payload, Base64.NO_WRAP);
            File out = new File(BackupHelper.root(), name);
            if (out.exists()) FileUtils.recursiveDelete(out);
            if (!out.mkdirs() && !out.exists()) return Res.fail("无法写入本地备份目录");
            unzip(zip, out);
            r.msg = name;
            return r;
        } catch (Throwable t) {
            return Res.fail("解包失败：" + t.getMessage());
        }
    }

    /** 删远端一份备份 */
    public static Res deleteRemote(String name) {
        if (!isConfigured()) return Res.fail("还没配置云端账号");
        if (!ensureToken()) return Res.fail("登录失败，请检查用户名/密码");
        return post("delete", new FormBody.Builder().add("token", getToken()).add("name", name == null ? "" : name));
    }

    /** 从云端拉内置源三件套 */
    public static Res fetchSources() {
        if (!isConfigured()) return Res.fail("还没配置云端账号");
        if (!ensureToken()) return Res.fail("登录失败，请检查用户名/密码");
        return post("sources", new FormBody.Builder().add("token", getToken()));
    }

    /* ===================== zip ===================== */

    private static byte[] zipDir(File dir) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(bos);
        zipInto(dir, dir, zos);
        zos.close();
        return bos.toByteArray();
    }

    private static void zipInto(File root, File cur, ZipOutputStream zos) throws IOException {
        File[] files = cur.listFiles();
        if (files == null) return;
        for (File f : files) {
            String entryName = root.toURI().relativize(f.toURI()).getPath();
            if (f.isDirectory()) {
                zipInto(root, f, zos);
            } else {
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                FileInputStream in = new FileInputStream(f);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
                in.close();
                zos.closeEntry();
            }
        }
    }

    private static void unzip(byte[] data, File outDir) throws IOException {
        ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(data));
        ZipEntry entry;
        byte[] buf = new byte[8192];
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName().replace('\\', '/');
            if (name.contains("../")) continue;
            File out = new File(outDir, name);
            if (entry.isDirectory()) {
                out.mkdirs();
            } else {
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                FileOutputStream fos = new FileOutputStream(out);
                int n;
                while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close();
            }
            zis.closeEntry();
        }
        zis.close();
    }

    /* ===================== 展示辅助 ===================== */

    public static String summary() {
        if (!isConfigured()) return "未配置（点此绑定你的服务器）";
        String base = getBaseUrl();
        base = base.replace("https://", "").replace("http://", "");
        int slash = base.indexOf('/');
        if (slash > 0) base = base.substring(0, slash);
        return base + " · " + getUser();
    }
}
