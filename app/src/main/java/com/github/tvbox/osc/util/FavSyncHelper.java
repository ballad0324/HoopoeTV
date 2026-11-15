package com.github.tvbox.osc.util;

import com.github.tvbox.osc.cache.FavRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import com.orhanobut.hawk.Hawk;

/**
 * 收藏跨端同步（手机 ⇄ 电视）。
 *
 * 收藏现在统一存在 Room 表 vodCollect（见 {@link FavRepository}），
 * 所以这里只需要把本机一份收藏打包/合并，不用再对齐两套存储。
 *
 * 传输层有三条：云备份通道（异地）、局域网直连（同一 WiFi）、文件导入导出。
 * 三条共用下面的 buildPack / applyPack，合并规则只有一份。
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

    /** 汇总本机收藏（单一份存储），跳过本机墓碑 */
    public static JSONArray collect() {
        Map<String, JSONObject> map = new LinkedHashMap<>();
        java.util.List<FavTombstone.Item> tombstones = FavTombstone.load();
        try {
            for (FavRepository.Item it : FavRepository.load()) {
                if (FavTombstone.isRemoved(it.vodId, it.sourceKey, tombstones)) continue;
                String key = idOf(it.vodId, it.sourceKey);
                if (!map.containsKey(key)) {
                    map.put(key, item(it.vodId, it.sourceKey, it.name, it.pic, it.note, it.time));
                }
            }
        } catch (Throwable ignored) {
        }
        JSONArray arr = new JSONArray();
        for (JSONObject o : map.values()) arr.put(o);
        return arr;
    }

    /** 本机墓碑（用户主动取消收藏的记录），随包上传，让对端也删掉 */
    private static JSONArray collectRemoved() {
        JSONArray arr = new JSONArray();
        for (FavTombstone.Item it : FavTombstone.load()) {
            JSONObject o = new JSONObject();
            try {
                o.put("vodId", it.vodId);
                o.put("sourceKey", it.sourceKey);
                o.put("time", it.time);
                arr.put(o);
            } catch (Throwable ignored) {
            }
        }
        return arr;
    }

    /** 打包本机收藏（云端上传与局域网直连共用同一份结构） */
    public static JSONObject buildPack() {
        JSONObject pack = new JSONObject();
        try {
            pack.put("items", collect());
            pack.put("removed", collectRemoved());
        } catch (Throwable ignored) {
        }
        return pack;
    }

    /**
     * 把对端传来的收藏包并入本机（并集 + 先执行删除），
     * 返回形如「对端 N 条收藏，新增 M 条，移除 K 条」的提示。
     */
    public static String applyPack(JSONObject pack) {
        JSONArray arr = pack.optJSONArray("items");
        // 先执行删除：对端记录的"用户主动取消收藏"同步到本机，否则取消的条目会复活
        JSONArray removedArr = pack.optJSONArray("removed");
        int removedCount = 0;
        if (removedArr != null) {
            for (int i = 0; i < removedArr.length(); i++) {
                JSONObject o = removedArr.optJSONObject(i);
                if (o == null) continue;
                String vodId = o.optString("vodId", "");
                if (vodId.length() == 0) continue;
                String sourceKey = o.optString("sourceKey", "");
                if (FavRepository.has(vodId, sourceKey)) removedCount++;
                FavRepository.remove(vodId, sourceKey);
                FavTombstone.record(vodId, sourceKey);
            }
        }
        java.util.List<FavTombstone.Item> tombstones = FavTombstone.load();
        int n = arr == null ? 0 : arr.length();
        int added = 0;
        for (int i = 0; i < n; i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String vodId = o.optString("vodId", "");
            if (vodId.length() == 0) continue;
            String sourceKey = o.optString("sourceKey", "");
            // 已在本机墓碑里的条目不再加回来
            if (FavTombstone.isRemoved(vodId, sourceKey, tombstones)) continue;
            FavRepository.Item it = new FavRepository.Item();
            it.vodId = vodId;
            it.sourceKey = sourceKey;
            it.name = o.optString("name", "");
            it.pic = o.optString("pic", "");
            it.note = o.optString("note", "");
            it.time = o.optLong("time", 0);
            if (FavRepository.add(it)) added++;
        }
        stamp();
        return "对端 " + n + " 条收藏，新增 " + added + " 条"
                + (removedCount > 0 ? "，移除 " + removedCount + " 条" : "");
    }

    /** 上传本机收藏到云端 */
    public static CloudBackupHelper.Res push() {
        if (CloudBackupHelper.getToken().length() == 0) {
            CloudBackupHelper.Res login = CloudBackupHelper.login();
            if (!login.ok) return CloudBackupHelper.Res.fail("请先登录云备份账号：" + login.msg);
        }
        try {
            JSONObject pack = buildPack();
            File dir = new File(BackupHelper.root(), PACK);
            if (dir.exists()) FileUtils.recursiveDelete(dir);
            if (!dir.mkdirs() && !dir.exists()) return CloudBackupHelper.Res.fail("无法写入本地目录");
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
                res.msg = "已上传 " + pack.optJSONArray("items").length() + " 条收藏";
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
            String msg = applyPack(pack);
            FileUtils.recursiveDelete(dir);
            CloudBackupHelper.Res ok = new CloudBackupHelper.Res();
            ok.ok = true;
            ok.msg = "云端" + msg;
            ok.data = pack;
            return ok;
        } catch (Throwable t) {
            FileUtils.recursiveDelete(dir);
            return CloudBackupHelper.Res.fail("合并收藏失败：" + t.getMessage());
        }
    }
}
