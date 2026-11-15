package com.github.tvbox.osc.cache;

import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 收藏的唯一存储入口。
 *
 * 以前手机端把收藏放在 Hawk（mobile_favorites），电视端放在 Room（vodCollect），
 * 同一台设备切界面模式时两份数据互不可见。现在统一到 Room 一张表：
 * 电视端原有代码不变，手机端改成调用这里。
 *
 * 兼容说明：
 * - 字段结构与原 MobileFavorites.Item 一致（vodId/sourceKey/name/pic/note/time），
 *   调用方不需要改数据结构；
 * - 直播频道收藏的 sourceKey 固定为 {@link #LIVE_SOURCE_KEY}，电视端收藏列表会过滤掉，
 *   避免出现「点开找不到数据源」的空壳条目；
 * - 老数据在首次读取时一次性从 Hawk 导入，原值改名留作备份，不直接删。
 */
public final class FavRepository {

    /** 直播频道收藏的固定 sourceKey（与手机直播播放页保持一致） */
    public static final String LIVE_SOURCE_KEY = "live";

    /** 旧手机端存储键（仅用于一次性迁移） */
    private static final String LEGACY_KEY = "mobile_favorites";
    private static final String LEGACY_BACKUP_KEY = "mobile_favorites_legacy";
    private static final String KEY_MIGRATED = "fav_hawk_migrated";

    public static class Item {
        public String vodId = "";
        public String sourceKey = "";
        public String name = "";
        public String pic = "";
        public String note = "";
        public long time = 0;
    }

    private FavRepository() {
    }

    /** 是否直播频道类收藏（电视端收藏页要过滤掉这类条目） */
    public static boolean isLiveItem(String sourceKey) {
        return LIVE_SOURCE_KEY.equals(sourceKey);
    }

    public static List<Item> load() {
        ensureMigrated();
        List<Item> list = new ArrayList<>();
        try {
            for (VodCollect c : RoomDataManger.getAllVodCollect()) {
                if (c == null || c.vodId == null) continue;
                Item it = new Item();
                it.vodId = c.vodId;
                it.sourceKey = c.sourceKey == null ? "" : c.sourceKey;
                it.name = c.name == null ? "" : c.name;
                it.pic = c.pic == null ? "" : c.pic;
                it.note = c.note == null ? "" : c.note;
                it.time = c.updateTime;
                list.add(it);
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    /** 手机端收藏列表用：排除直播频道条目 */
    public static List<Item> loadVodOnly() {
        List<Item> all = load();
        List<Item> list = new ArrayList<>();
        for (Item it : all) {
            if (!isLiveItem(it.sourceKey)) list.add(it);
        }
        return list;
    }

    public static boolean has(String vodId, String sourceKey) {
        if (vodId == null || vodId.length() == 0) return false;
        try {
            return RoomDataManger.isVodCollect(sourceKey == null ? "" : sourceKey, vodId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 收藏 / 取消收藏，返回操作后是否为已收藏状态（与旧 MobileFavorites.toggle 行为一致） */
    public static boolean toggle(Item item) {
        if (item == null || item.vodId == null || item.vodId.length() == 0) return false;
        if (has(item.vodId, item.sourceKey)) {
            remove(item.vodId, item.sourceKey);
            return false;
        }
        insert(item);
        return true;
    }

    /** 跨端同步用：本机没有才写入 */
    public static boolean add(Item item) {
        if (item == null || item.vodId == null || item.vodId.length() == 0) return false;
        if (has(item.vodId, item.sourceKey)) return false;
        insert(item);
        return true;
    }

    public static void remove(String vodId, String sourceKey) {
        if (vodId == null || vodId.length() == 0) return;
        boolean removed = false;
        try {
            if (RoomDataManger.isVodCollect(sourceKey == null ? "" : sourceKey, vodId)) {
                com.github.tvbox.osc.bean.VodInfo info = new com.github.tvbox.osc.bean.VodInfo();
                info.id = vodId;
                RoomDataManger.deleteVodCollect(sourceKey == null ? "" : sourceKey, info);
                removed = true;
            }
        } catch (Throwable ignored) {
        }
        if (removed) {
            // 记墓碑：让"取消收藏"也能跨端传播
            com.github.tvbox.osc.util.FavTombstone.record(vodId, sourceKey);
        }
    }

    private static void insert(Item item) {
        try {
            VodCollect c = new VodCollect();
            c.vodId = item.vodId;
            c.sourceKey = item.sourceKey == null ? "" : item.sourceKey;
            c.name = item.name == null ? "" : item.name;
            c.pic = item.pic == null ? "" : item.pic;
            c.note = item.note == null ? "" : item.note;
            c.updateTime = item.time > 0 ? item.time : System.currentTimeMillis();
            com.github.tvbox.osc.data.AppDataManager.get().getVodCollectDao().insert(c);
        } catch (Throwable ignored) {
        }
    }

    /** 首次运行时把 Hawk 里的老收藏导入 Room（原值改名留档，不直接删） */
    private static void ensureMigrated() {
        try {
            if (Hawk.get(KEY_MIGRATED, false)) return;
            String raw = Hawk.get(LEGACY_KEY, "");
            if (raw != null && raw.length() > 0) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String vodId = o.optString("vodId", "");
                    if (vodId.length() == 0) continue;
                    String sourceKey = o.optString("sourceKey", "");
                    if (has(vodId, sourceKey)) continue;
                    Item it = new Item();
                    it.vodId = vodId;
                    it.sourceKey = sourceKey;
                    it.name = o.optString("name", "");
                    it.pic = o.optString("pic", "");
                    it.note = o.optString("note", "");
                    android.util.Log.i("FavRepository", "migrate " + vodId + " / " + sourceKey);
                    it.time = o.optLong("time", 0);
                    insert(it);
                }
                Hawk.put(LEGACY_BACKUP_KEY, raw);
                Hawk.delete(LEGACY_KEY);
            }
            Hawk.put(KEY_MIGRATED, true);
        } catch (Throwable ignored) {
        }
    }
}
