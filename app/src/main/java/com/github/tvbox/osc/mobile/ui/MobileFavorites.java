package com.github.tvbox.osc.mobile.ui;

import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 我的收藏：本机 Hawk 存储，同源同 id 去重，最新收藏置顶，最多 100 条。
 */
public class MobileFavorites {

    private static final String KEY = "mobile_favorites";
    private static final int MAX = 100;

    public static class Item {
        public String vodId = "";
        public String sourceKey = "";
        public String name = "";
        public String pic = "";
        public String note = "";
        public long time = 0;
    }

    public static List<Item> load() {
        List<Item> list = new ArrayList<>();
        String raw = null;
        try {
            raw = Hawk.get(KEY, "");
        } catch (Throwable ignored) {
        }
        if (raw == null || raw.length() == 0) return list;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Item it = new Item();
                it.vodId = o.optString("vodId", "");
                it.sourceKey = o.optString("sourceKey", "");
                it.name = o.optString("name", "");
                it.pic = o.optString("pic", "");
                it.note = o.optString("note", "");
                it.time = o.optLong("time", 0);
                if (it.vodId.length() == 0) continue;
                list.add(it);
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    public static boolean has(String vodId, String sourceKey) {
        for (Item it : load()) {
            if (it.vodId.equals(vodId) && it.sourceKey.equals(sourceKey)) return true;
        }
        return false;
    }

    /** 收藏 / 取消收藏，返回操作后是否为已收藏状态 */
    public static boolean toggle(Item item) {
        if (item == null || item.vodId == null || item.vodId.length() == 0) return false;
        List<Item> list = load();
        for (int i = 0; i < list.size(); i++) {
            Item it = list.get(i);
            if (item.vodId.equals(it.vodId) && item.sourceKey.equals(it.sourceKey)) {
                list.remove(i);
                save(list);
                return false;
            }
        }
        item.time = System.currentTimeMillis();
        list.add(0, item);
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
        save(list);
        return true;
    }

    /** 跨端同步用：本机没有才追加，规则与 toggle 一致（置顶、上限 100） */
    public static boolean add(Item item) {
        if (item == null || item.vodId == null || item.vodId.length() == 0) return false;
        if (has(item.vodId, item.sourceKey)) return false;
        List<Item> list = load();
        if (item.time <= 0) item.time = System.currentTimeMillis();
        list.add(0, item);
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
        save(list);
        return true;
    }

    public static void remove(String vodId, String sourceKey) {
        List<Item> list = load();
        for (int i = list.size() - 1; i >= 0; i--) {
            Item it = list.get(i);
            if (it.vodId.equals(vodId) && it.sourceKey.equals(sourceKey)) {
                list.remove(i);
            }
        }
        save(list);
    }

    private static void save(List<Item> list) {
        JSONArray arr = new JSONArray();
        for (Item it : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("vodId", it.vodId);
                o.put("sourceKey", it.sourceKey);
                o.put("name", it.name == null ? "" : it.name);
                o.put("pic", it.pic == null ? "" : it.pic);
                o.put("note", it.note == null ? "" : it.note);
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
