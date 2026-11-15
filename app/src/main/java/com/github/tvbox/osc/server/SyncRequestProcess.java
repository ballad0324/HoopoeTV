package com.github.tvbox.osc.server;

import com.github.tvbox.osc.util.FavSyncHelper;
import com.github.tvbox.osc.util.SettingsSyncHelper;

import org.json.JSONObject;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 局域网直连同步（不依赖任何服务器、数据库或第三方服务）。
 *
 * 双端 App 启动时都会跑 {@link RemoteServer}，所以任意一台设备都可以当「被连的那台」，
 * 另一台填上它的 IP:端口即可在同一 WiFi 下互传收藏与设置。
 *
 *   GET  /sync/info      设备信息（确认连的是哪台设备、上次同步时间）
 *   GET  /sync/fav       本机收藏包（items + removed 墓碑）
 *   POST /sync/fav       把对端收藏包并入本机（并集，墓碑先生效）
 *   GET  /sync/settings  本机设置包（白名单项）
 *   POST /sync/settings  把对端设置包写回本机
 *
 * 合并逻辑完全复用 {@link FavSyncHelper#buildPack()} / {@link FavSyncHelper#applyPack(JSONObject)}
 * 与 {@link SettingsSyncHelper#collect()} / {@link SettingsSyncHelper#apply(JSONObject)}，
 * 与云备份通道走的是同一套代码，不存在两套合并规则跑偏的问题。
 */
public class SyncRequestProcess implements RequestProcess {

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        return fileName != null && fileName.startsWith("/sync/");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName,
                                         Map<String, String> params, Map<String, String> files) {
        try {
            if ("/sync/info".equals(fileName)) {
                JSONObject o = new JSONObject();
                o.put("app", "HoopoeTV");
                o.put("name", android.os.Build.MODEL == null ? "" : android.os.Build.MODEL);
                o.put("port", RemoteServer.serverPort);
                o.put("favTime", FavSyncHelper.lastTime());
                o.put("setTime", SettingsSyncHelper.lastTime());
                return ok(o.toString());
            }
            if ("/sync/fav".equals(fileName)) {
                if (session.getMethod() == NanoHTTPD.Method.POST) {
                    String body = readBody(files);
                    if (body == null) return bad("请求体为空");
                    String msg = FavSyncHelper.applyPack(new JSONObject(body));
                    JSONObject o = new JSONObject();
                    o.put("ok", true);
                    o.put("msg", msg);
                    return ok(o.toString());
                }
                return ok(FavSyncHelper.buildPack().toString());
            }
            if ("/sync/settings".equals(fileName)) {
                if (session.getMethod() == NanoHTTPD.Method.POST) {
                    String body = readBody(files);
                    if (body == null) return bad("请求体为空");
                    int n = SettingsSyncHelper.apply(new JSONObject(body));
                    JSONObject o = new JSONObject();
                    o.put("ok", true);
                    o.put("msg", "已同步 " + n + " 项设置，重启后生效");
                    return ok(o.toString());
                }
                return ok(SettingsSyncHelper.collect().toString());
            }
            return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found.");
        } catch (Throwable th) {
            th.printStackTrace();
            return bad("同步失败：" + th.getMessage());
        }
    }

    /** NanoHTTPD 把普通 POST 的原始体放在 files["postData"] 里 */
    private String readBody(Map<String, String> files) {
        if (files == null) return null;
        String body = files.get("postData");
        return body == null || body.length() == 0 ? null : body;
    }

    private NanoHTTPD.Response ok(String body) {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                "application/json; charset=utf-8", body);
    }

    private NanoHTTPD.Response bad(String msg) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("msg", msg);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                    "application/json; charset=utf-8", o.toString());
        } catch (Throwable ignored) {
            return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, msg);
        }
    }
}
