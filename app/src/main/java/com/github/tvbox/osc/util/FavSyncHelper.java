package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.mobile.ui.MobileFavorites;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 收藏跨端同步（手机 ⇄ 电视）。
 *
 * 手机端收藏存在 Hawk（MobileFavorites），电视端收藏存在 Room（vodCollect 表），
 * 同一台设备切界面模式时两份数据互不相通，跨设备更不通。
 *
 * 这里把两份收藏统一成一份 JSON 放进云备份通道（不新增服务端接口）：
 *   push：合并本机两份收藏 → 打包成 fav_sync 目录 → CloudBackupHelper.push
 *   pull：下载 fav_sync → 逐条写入本机两份收藏（已存在的跳过，只做并集，不删本机已有项）
 */
public final class FavSyncHelper {

    public static final String PACK = "fav_sync";
    private static final String FILE_NAME = "favorites.json";
    private static final String KEY_LAST = "fav_sync_time";

    private FavSyncHelper() {
    }

    public static String lastTime() {
        return Hawk.get(KEY_LAST, "");
    }

    private static void stamp() {
        Hawk.put(KEY_LAST, new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
    }

    private static String idOf(String vodId, String sourceKey) {
        return (vodId == null ? "" : vodId) + "|" + (sourceKey == null ? "" : sourceKey);
    }

    private static JSONObject item(String vodId, String sourceKey, String name, String pic,
                                   String note, long time) {
        JSONObject o = new JSONObject();
        try {
            o.put("vodId", vodId == null ? "" : vodId);
            o.put("sourceKey", sourceKey == null ? "" : sourceKey);
            o.put("name", name == null ? "" : name);
            o.put("pic", pic == null ? "" : pic);
            o.put("note", note == null ? "" : note);
            o.put("time", time);
        } catch (Throwable ignored) {
        }
        return o;
    }

    /** 汇总本机两份收藏（手机端 Hawk + 电视端数据库），按 vodId+sourceKey 去重 */
    public static JSONArray collect() {
        Map<String, JSONObject> map = new LinkedHashMap<>();
        try {
            for (MobileFavorites.Item it : MobileFavorites.load()) {
                String key = idOf(it.vodId, it.sourceKey);
                if (!map.containsKey(key)) {
                    map.put(key, item(it.vodId, it.sourceKey, it.name, it.pic, it.note, it.time));
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            for (VodCollect it : RoomDataManger.getAllVodCollect()) {
                String key = idOf(it.vodId, it.sourceKey);
                if (!map.containsKey(key)) {
                    map.put(key, item(it.vodId, it.sourceKey, it.name, it.pic, "", it.updateTime));
                }
            }
        } catch (Throwable ignored) {
        }
        JSONArray arr = new JSONArray();
        for (JSONObject o : map.values()) arr.put(o);
        return arr;
    }

    /** 上传本机收藏到云端 */
    public static CloudBackupHelper.Res push() {
        if (CloudBackupHelper.getToken().length() == 0) {
            CloudBackupHelper.Res login = CloudBackupHelper.login();
            if (!login.ok) return CloudBackupHelper.Res.fail("请先登录云备份账号：" + login.msg);
        }
        try {
            JSONArray arr = collect();
            File dir = new File(BackupHelper.root(), PACK);
            if (dir.exists()) FileUtils.recursiveDelete(dir);
            if (!dir.mkdirs() && !dir.exists()) return CloudBackupHelper.Res.fail("无法写入本地目录");
            JSONObject pack = new JSONObject();
            pack.put("items", arr);
            if (!FileUtils.writeSimple(pack.toString().getBytes("UTF-8"), new File(dir, FILE_NAME))) {
                FileUtils.recursiveDelete(dir);
                return CloudBackupHelper.Res.fail("写入收藏包失败");
            }
            try {
                CloudBackupHelper.deleteRemote(PACK);
            } catch (Throwable ignored) {
            }
            CloudBackupHelper.Res res = CloudBackupHelper.push(PACK);
            FileUtils.recursiveDelete(dir);
            if (res.ok) {
                stamp();
                res.msg = "已上传 " + arr.length() + " 条收藏";
            }
            return res;
        } catch (Throwable t) {
            return CloudBackupHelper.Res.fail("打包失败：" + t.getMessage());
        }
    }

    /** 从云端拉回收藏（并入本机，不删除本机已有项） */
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
                return CloudBackupHelper.Res.fail("云端还没有收藏同步包，请先在另一端上传");
            }
            byte[] data = FileUtils.readSimple(f);
            if (data == null) {
                FileUtils.recursiveDelete(dir);
                return CloudBackupHelper.Res.fail("读取收藏包失败");
            }
            JSONObject pack = new JSONObject(new String(data, "UTF-8"));
            JSONArray arr = pack.optJSONArray("items");
            int n = arr == null ? 0 : arr.length();
            int added = 0;
            for (int i = 0; i < n; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String vodId = o.optString("vodId", "");
                if (vodId.length() == 0) continue;
                String sourceKey = o.optString("sourceKey", "");
                String name = o.optString("name", "");
                String pic = o.optString("pic", "");
                String note = o.optString("note", "");
                long time = o.optLong("time", 0);
                // 手机端那份（Hawk）
                try {
                    if (!MobileFavorites.has(vodId, sourceKey)) {
                        MobileFavorites.Item it = new MobileFavorites.Item();
                        it.vodId = vodId;
                        it.sourceKey = sourceKey;
                        it.name = name;
                        it.pic = pic;
                        it.note = note;
                        it.time = time;
                        MobileFavorites.add(it);
                        added++;
                    }
                } catch (Throwable ignored) {
                }
                // 电视端那份（Room）
                try {
                    if (!RoomDataManger.isVodCollect(sourceKey, vodId)) {
                        VodInfo info = new VodInfo();
                        info.id = vodId;
                        info.name = name;
                        info.pic = pic;
                        RoomDataManger.insertVodCollect(sourceKey, info);
                    }
                } catch (Throwable ignored) {
                }
            }
            FileUtils.recursiveDelete(dir);
            stamp();
            CloudBackupHelper.Res ok = new CloudBackupHelper.Res();
            ok.ok = true;
            ok.msg = "云端 " + n + " 条收藏，新增 " + added + " 条";
            ok.data = pack;
            return ok;
        } catch (Throwable t) {
            FileUtils.recursiveDelete(dir);
            return CloudBackupHelper.Res.fail("合并收藏失败：" + t.getMessage());
        }
    }
}
