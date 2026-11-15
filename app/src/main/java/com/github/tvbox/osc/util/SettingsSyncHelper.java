package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.setting.CommonSetting;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.orhanobut.hawk.Hawk;

/**
 * 跨端设置同步（手机 ⇄ 电视）。
 *
 * 复用已验证的云备份通道，不新增服务端接口：
 *   push：把本机白名单设置打包成一个小目录 —— 走 {@link CloudBackupHelper#push} 上传，
 *         云端名固定为 {@link #PACK}，上传前先删同名旧包，保证只有一份。
 *   pull：走 {@link CloudBackupHelper#pull} 拉到本地同名目录，读 settings.json 后
 *         逐项写回 Hawk2（Hawk2 里所有值都是 String，与 BackupHelper 的恢复逻辑一致），
 *         最后提示重启生效。
 *
 * 只同步「跨端有意义」的配置：接口/直播/EPG、主题、语言、播放器偏好、搜索与字幕偏好、
 * 直播偏好。不同步设备形态（ui_mode）、播放位置、云账号密码与 token —— 那些每台设备各不相同。
 */
public final class SettingsSyncHelper {

    /** 云端备份名 + 本地目录名 */
    public static final String PACK = "settings_sync";
    private static final String FILE_NAME = "settings.json";
    private static final String KEY_LAST = "settings_sync_time";

    /** 同步白名单：每台设备之间应该一致的配置项 */
    private static final String[] WHITELIST = new String[]{
            // 数据源
            HawkConfig.API_URL, HawkConfig.LIVE_URL, HawkConfig.EPG_URL,
            HawkConfig.API_HISTORY, HawkConfig.LIVE_HISTORY, HawkConfig.EPG_HISTORY,
            // 外观 / 语言
            HawkConfig.THEME_SELECT, "hoopoe_skin_index",
            HawkConfig.HOME_LOCALE, CommonSetting.KEY_AGG_SEARCH,
            // 首页
            HawkConfig.HOME_REC, HawkConfig.HOME_REC_STYLE, HawkConfig.HOME_NUM,
            HawkConfig.HOME_SHOW_SOURCE, HawkConfig.HOME_SEARCH_POSITION,
            HawkConfig.HOME_MENU_POSITION, HawkConfig.HOME_DEFAULT_SHOW,
            // 播放器
            HawkConfig.PLAY_TYPE, HawkConfig.IJK_CODEC, HawkConfig.PLAY_RENDER, HawkConfig.PLAY_SCALE,
            HawkConfig.PLAY_TIME_STEP, HawkConfig.PIC_IN_PIC, HawkConfig.VIDEO_PURIFY,
            HawkConfig.IJK_CACHE_PLAY, HawkConfig.EXO_RENDERER, HawkConfig.EXO_RENDERER_MODE,
            HawkConfig.VOD_PLAYER_PREFERRED, HawkConfig.BACKGROUND_PLAY_TYPE,
            HawkConfig.DEFAULT_PARSE, HawkConfig.PARSE_WEBVIEW,
            // 网络
            HawkConfig.DOH_URL,
            // 搜索 / 字幕 / 列表
            HawkConfig.SEARCH_VIEW, HawkConfig.FAST_SEARCH_MODE, HawkConfig.SEARCH_FILTER_KEY,
            HawkConfig.SUBTITLE_TEXT_SIZE, HawkConfig.SUBTITLE_TEXT_STYLE, HawkConfig.SUBTITLE_TIME_DELAY,
            HawkConfig.SCREEN_DISPLAY, HawkConfig.STORAGE_DRIVE_SORT,
            // 直播偏好
            HawkConfig.LIVE_CHANNEL_REVERSE, HawkConfig.LIVE_CROSS_GROUP,
            HawkConfig.LIVE_CONNECT_TIMEOUT, HawkConfig.LIVE_SHOW_NET_SPEED,
            HawkConfig.LIVE_SHOW_TIME, HawkConfig.LIVE_SKIP_PASSWORD, HawkConfig.LIVE_PLAYER_TYPE,
    };

    private SettingsSyncHelper() {
    }

    private static SharedPreferences hawk2() {
        return App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE);
    }

    /** 上次同步时间（空 = 从未同步） */
    public static String lastTime() {
        return Hawk.get(KEY_LAST, "");
    }

    private static void stamp() {
        Hawk.put(KEY_LAST, new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
    }

    /** 收集本机白名单设置 */
    public static JSONObject collect() {
        JSONObject obj = new JSONObject();
        SharedPreferences sp = hawk2();
        for (String key : WHITELIST) {
            try {
                if (sp.contains(key)) obj.put(key, sp.getString(key, ""));
            } catch (Throwable ignored) {
            }
        }
        return obj;
    }

    /** 把对端设置包写回本机白名单项，返回写入条数（云端 pull 与局域网直连共用） */
    public static int apply(JSONObject obj) {
        SharedPreferences sp = hawk2();
        int n = 0;
        for (String key : WHITELIST) {
            if (!obj.has(key)) continue;
            sp.edit().putString(key, obj.optString(key, "")).commit();
            n++;
        }
        stamp();
        return n;
    }

    /** 上传本机设置到云端 */
    public static CloudBackupHelper.Res push() {
        if (CloudBackupHelper.getToken().length() == 0) {
            CloudBackupHelper.Res login = CloudBackupHelper.login();
            if (!login.ok) return CloudBackupHelper.Res.fail("请先登录云备份账号：" + login.msg);
        }
        try {
            JSONObject obj = collect();
            File dir = new File(BackupHelper.root(), PACK);
            if (dir.exists()) FileUtils.recursiveDelete(dir);
            if (!dir.mkdirs() && !dir.exists()) return CloudBackupHelper.Res.fail("无法写入本地目录");
            if (!FileUtils.writeSimple(obj.toString().getBytes("UTF-8"), new File(dir, FILE_NAME))) {
                FileUtils.recursiveDelete(dir);
                return CloudBackupHelper.Res.fail("写入设置包失败");
            }
            // 先删云端同名旧包，保证云端只有一份最新设置
            try {
                CloudBackupHelper.deleteRemote(PACK);
            } catch (Throwable ignored) {
            }
            CloudBackupHelper.Res res = CloudBackupHelper.push(PACK);
            FileUtils.recursiveDelete(dir);
            if (res.ok) {
                stamp();
                res.msg = "已上传 " + obj.length() + " 项设置";
            }
            return res;
        } catch (Throwable t) {
            return CloudBackupHelper.Res.fail("打包失败：" + t.getMessage());
        }
    }

    /** 从云端拉回设置（覆盖本机白名单项） */
    public static CloudBackupHelper.Res pull() {
        if (CloudBackupHelper.getToken().length() == 0) {
            CloudBackupHelper.Res login = CloudBackupHelper.login();
            if (!login.ok) return CloudBackupHelper.Res.fail("请先登录云备份账号：" + login.msg);
        }
        File dir = new File(BackupHelper.root(), PACK);
        CloudBackupHelper.Res res = CloudBackupHelper.pull(PACK);
        if (!res.ok) return res;
        try {
            File f = new File(dir, FILE_NAME);
            if (!f.exists()) {
                FileUtils.recursiveDelete(dir);
                return CloudBackupHelper.Res.fail("云端还没有设置同步包，请先在另一端上传");
            }
            byte[] data = FileUtils.readSimple(f);
            if (data == null) {
                FileUtils.recursiveDelete(dir);
                return CloudBackupHelper.Res.fail("读取设置包失败");
            }
            JSONObject obj = new JSONObject(new String(data, "UTF-8"));
            int n = apply(obj);
            FileUtils.recursiveDelete(dir);
            CloudBackupHelper.Res ok = new CloudBackupHelper.Res();
            ok.ok = true;
            ok.msg = "已同步 " + n + " 项设置";
            ok.data = obj;
            return ok;
        } catch (Throwable t) {
            FileUtils.recursiveDelete(dir);
            return CloudBackupHelper.Res.fail("应用设置失败：" + t.getMessage());
        }
    }
}
