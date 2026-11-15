package com.github.tvbox.osc.util;

import com.github.tvbox.osc.server.RemoteServer;

import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 局域网直连同步的客户端（手机 ⇄ 电视，同一 WiFi 下互传收藏与设置）。
 *
 * 服务端是双端都已在跑的 {@link RemoteServer}（NanoHTTPD，端口 9978 起），
 * 路由见 {@link com.github.tvbox.osc.server.SyncRequestProcess}。
 * 这里只做 HTTP 调用，合并/应用逻辑全在 FavSyncHelper / SettingsSyncHelper 里，
 * 与云备份通道共用同一套代码。
 *
 * 目标地址可填 "192.168.1.8"、"192.168.1.8:9978"、"http://192.168.1.8:9978"，都会归一化。
 */
public final class LanSyncHelper {

    public interface Callback {
        void onResult(boolean ok, String msg);
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private LanSyncHelper() {
    }

    /** 本机局域网地址，形如 http://192.168.1.8:9978；未连 WiFi 时返回 null */
    public static String localBase() {
        String ip = localIp();
        if (ip == null) return null;
        // 手机端原本没有启动点，这里补启动一次，保证「对方也能连过来」
        try {
            com.github.tvbox.osc.server.ControlManager.get().startIfNeeded();
        } catch (Throwable ignored) {
        }
        return "http://" + ip + ":" + RemoteServer.serverPort;
    }

    /** 本机服务是否在跑（不跑时对方连不上，面板会给出提示） */
    public static boolean serverRunning() {
        try {
            return com.github.tvbox.osc.server.ControlManager.get().isServerRunning();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 本机局域网 IPv4（优先 wlan 网段，跳过回环与虚拟网卡） */
    public static String localIp() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            String fallback = null;
            for (NetworkInterface ni : all) {
                if (ni == null || !ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName() == null ? "" : ni.getName().toLowerCase();
                if (name.startsWith("rmnet") || name.startsWith("dummy") || name.startsWith("tun")) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) continue;
                    String host = addr.getHostAddress();
                    if (host == null || host.startsWith("127.")) continue;
                    if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("eth")) return host;
                    if (fallback == null) fallback = host;
                }
            }
            return fallback;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 把用户输入归一化成 http://host:port */
    public static String normalize(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.length() == 0) return null;
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        int schemeEnd = s.indexOf("://") + 3;
        // 没有写端口时补上本机默认端口
        String rest = s.substring(schemeEnd);
        int slash = rest.indexOf('/');
        String hostPart = slash >= 0 ? rest.substring(0, slash) : rest;
        if (hostPart.indexOf(':') < 0) {
            s = s.substring(0, schemeEnd) + hostPart + ":" + RemoteServer.serverPort
                    + (slash >= 0 ? rest.substring(slash) : "");
        }
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** 从对端拉收藏并并入本机（异步，回调在主线程之外） */
    public static void pullFav(final String peer, final Callback cb) {
        run(peer, cb, new Job() {
            @Override
            public String run(String base) throws Throwable {
                String body = get(base + "/sync/fav");
                return FavSyncHelper.applyPack(new JSONObject(body));
            }
        }, "拉取收藏");
    }

    /** 把本机收藏推给对端（对端执行合并，本机不动） */
    public static void pushFav(final String peer, final Callback cb) {
        run(peer, cb, new Job() {
            @Override
            public String run(String base) throws Throwable {
                String body = post(base + "/sync/fav", FavSyncHelper.buildPack().toString());
                JSONObject o = new JSONObject(body);
                return o.optString("msg", "已推送到对端");
            }
        }, "推送收藏");
    }

    /** 从对端拉设置并写回本机（覆盖白名单项，需重启生效） */
    public static void pullSettings(final String peer, final Callback cb) {
        run(peer, cb, new Job() {
            @Override
            public String run(String base) throws Throwable {
                String body = get(base + "/sync/settings");
                int n = SettingsSyncHelper.apply(new JSONObject(body));
                return "已同步 " + n + " 项设置，重启后生效";
            }
        }, "拉取设置");
    }

    /** 把本机设置推给对端 */
    public static void pushSettings(final String peer, final Callback cb) {
        run(peer, cb, new Job() {
            @Override
            public String run(String base) throws Throwable {
                String body = post(base + "/sync/settings", SettingsSyncHelper.collect().toString());
                JSONObject o = new JSONObject(body);
                return o.optString("msg", "已推送到对端");
            }
        }, "推送设置");
    }

    /** 探测对端是否可连（先确认地址填对，再谈同步） */
    public static void ping(final String peer, final Callback cb) {
        run(peer, cb, new Job() {
            @Override
            public String run(String base) throws Throwable {
                JSONObject o = new JSONObject(get(base + "/sync/info"));
                return "已连接 " + o.optString("name", "对端") + "（收藏 "
                        + o.optString("favTime", "未同步") + "）";
            }
        }, "连接测试");
    }

    private interface Job {
        String run(String base) throws Throwable;
    }

    private static void run(String peer, final Callback cb, final Job job, final String label) {
        final String base = normalize(peer);
        if (base == null) {
            if (cb != null) cb.onResult(false, "请先填写对端地址");
            return;
        }
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                String msg;
                boolean ok = true;
                try {
                    msg = job.run(base);
                } catch (Throwable th) {
                    ok = false;
                    msg = label + "失败：" + describe(th);
                }
                if (cb != null && msg != null) cb.onResult(ok, msg);
            }
        });
    }

    /** 把常见的网络异常翻译成用户能看懂的话 */
    private static String describe(Throwable th) {
        String name = th.getClass().getSimpleName();
        String m = th.getMessage() == null ? "" : th.getMessage();
        if (name.contains("ConnectException") || m.contains("ECONNREFUSED")) {
            return "无法连接，请确认对端 App 正在运行、地址填对、两台在同一 WiFi";
        }
        if (name.contains("SocketTimeout")) return "连接超时，检查是否在同一局域网，或路由器开了「AP 隔离」";
        if (name.contains("UnknownHost")) return "地址无法解析，请直接填 IP";
        if (name.contains("JSON")) return "对端返回内容异常，确认对方是本 App";
        return name + (m.length() > 0 ? "：" + m : "");
    }

    private static String get(String url) throws Throwable {
        Request req = new Request.Builder().url(url).get().build();
        Response resp = CLIENT.newCall(req).execute();
        return readAndClose(resp);
    }

    private static String post(String url, String json) throws Throwable {
        Request req = new Request.Builder().url(url).post(RequestBody.create(JSON, json)).build();
        Response resp = CLIENT.newCall(req).execute();
        return readAndClose(resp);
    }

    private static String readAndClose(Response resp) throws Throwable {
        try {
            if (!resp.isSuccessful()) {
                throw new java.io.IOException("HTTP " + resp.code());
            }
            if (resp.body() == null) throw new java.io.IOException("响应为空");
            return resp.body().string();
        } finally {
            resp.close();
        }
    }
}
