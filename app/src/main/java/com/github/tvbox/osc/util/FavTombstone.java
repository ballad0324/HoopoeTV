package com.github.tvbox.osc.util;

import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 收藏删除墓碑：记录"用户主动取消收藏"的条目，
 * 让取消收藏也能跨端传播（否则从另一端拉回时，被删掉的条目会重新出现）。
 *
 * 只记"用户主动删除"的路径（详情页取消收藏、收藏列表删除），
 * 不记"清空全部收藏"和数据迁移类的批量删除。
 */
public final class FavTombstone {

    private static final String KEY = "fav_removed";
    /** 最多保留 2000 条墓碑，防止无限增长（单人收藏量远小于此） */
    private static final int MAX = 2000;
    /** 墓碑有效期 365 天：拉长到一年，避免久不联网的对端把旧收藏又拉回来 */
    private static final long KEEP_MS = 365L * 24 * 3600 * 1000;

    public static class Item {
        public String vodId = "";
        public String sourceKey = "";
        public long time = 0;
    }

    private FavTombstone() {
    }

    public static synchronized void record(String vodId, String sourceKey) {
        if (vodId == null || vodId.length() == 0) return;
        List<Item> list = load();
        for (Item it : list) {
            if (it.vodId.equals(vodId) && it.sourceKey.equals(sourceKey == null ? "" : sourceKey)) {
                it.time = System.currentTimeMillis();
                save(list);
                return;
            }
        }
        Item item = new Item();
        item.vodId = vodId;
        item.sourceKey = sourceKey == null ? "" : sourceKey;
        item.time = System.currentTimeMillis();
        list.add(0, item);
        save(list);
    }

    public static synchronized List<Item> load() {
        List<Item> list = new ArrayList<>();
        String raw = null;
        try {
            raw = Hawk.get(KEY, "");
        } catch (Throwable ignored) {
        }
        if (raw == null || raw.length() == 0) return list;
        long now = System.currentTimeMillis();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Item it = new Item();
                it.vodId = o.optString("vodId", "");
                it.sourceKey = o.optString("sourceKey", "");
                it.time = o.optLong("time", 0);
                if (it.vodId.length() == 0) continue;
                if (now - it.time > KEEP_MS) continue;
                list.add(it);
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    /** 判断某条收藏是否已被用户主动删除 */
    public static boolean isRemoved(String vodId, String sourceKey, List<Item> tombstones) {
        if (vodId == null || tombstones == null) return false;
        for (Item it : tombstones) {
            if (it.vodId.equals(vodId)
                    && it.sourceKey.equals(sourceKey == null ? "" : sourceKey)) {
                return true;
            }
        }
        return false;
    }

    private static void save(List<Item> list) {
        JSONArray arr = new JSONArray();
        for (Item it : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("vodId", it.vodId);
                o.put("sourceKey", it.sourceKey);
                o.put("time", it.time);
                arr.put(o);
            } catch (Throwable ignored) {
            }
        }
        try {
            Hawk.put(KEY, arr.toString());
        } catch (Throwable ignored) {
        }
    }
}
