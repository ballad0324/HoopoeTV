package com.github.tvbox.osc.mobile.ui;

import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页「继续观看」的历史记录：只存在本机 Hawk 里，同片源同 id 去重，最多 20 条，按时间倒序。
 * 只记录够回跳的最小信息（id / 源 / 封面 / 线路 / 集数 / 进度），不带完整剧集列表。
 */
public class MobilePlayHistory {

    private static final String KEY = "mobile_play_history";
    private static final int MAX = 20;

    public static class Item {
        public String vodId = "";
        public String sourceKey = "";
        public String name = "";
        public String pic = "";
        public String flag = "";
        public String seriesName = "";
        public int playIndex = 0;
        public long progress = 0;
        public long duration = 0;
        public long time = 0;

        /** 卡片副标题：第 N 集 · 45% */
        public String subtitle() {
            StringBuilder sb = new StringBuilder();
            if (seriesName != null && seriesName.length() > 0) {
                sb.append(seriesName);
            } else {
                sb.append("第").append(playIndex + 1).append("集");
            }
            if (duration > 0 && progress > 0) {
                long pct = progress * 100 / duration;
                if (pct > 100) pct = 100;
                if (pct > 0) sb.append(" · ").append(pct).append("%");
            }
            return sb.toString();
        }
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
                it.flag = o.optString("flag", "");
                it.seriesName = o.optString("seriesName", "");
                it.playIndex = o.optInt("playIndex", 0);
                it.progress = o.optLong("progress", 0);
                it.duration = o.optLong("duration", 0);
                it.time = o.optLong("time", 0);
                if (it.vodId.length() == 0) continue;
                list.add(it);
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    /** 播放时调用：写入或刷新一条记录，置顶 */
    public static void record(Item item) {
        if (item == null || item.vodId == null || item.vodId.length() == 0) return;
        List<Item> list = load();
        for (int i = 0; i < list.size(); i++) {
            Item it = list.get(i);
            if (item.vodId.equals(it.vodId) && item.sourceKey.equals(it.sourceKey)) {
                list.remove(i);
                break;
            }
        }
        item.time = System.currentTimeMillis();
        list.add(0, item);
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
        save(list);
        pushToCloud();
    }

    /** 删除一条记录（首页/历史页长按删除用） */
    public static void remove(String vodId, String sourceKey) {
        if (vodId == null || vodId.length() == 0) return;
        List<Item> list = load();
        for (int i = list.size() - 1; i >= 0; i--) {
            Item it = list.get(i);
            if (it.vodId.equals(vodId) && (sourceKey == null || sourceKey.equals(it.sourceKey))) {
                list.remove(i);
            }
        }
        save(list);
    }

    public static void clear() {
        try {
            Hawk.delete(KEY);
        } catch (Throwable ignored) {
        }
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
                o.put("flag", it.flag == null ? "" : it.flag);
                o.put("seriesName", it.seriesName == null ? "" : it.seriesName);
                o.put("playIndex", it.playIndex);
                o.put("progress", it.progress);
                o.put("duration", it.duration);
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

    /* ===================== 云端同步（v1.7） ===================== */

    /** 本地记录推到 MySQL（登录才推，静默失败，不阻塞播放） */
    public static void pushToCloud() {
        if (!com.github.tvbox.osc.mobile.ui.MobileUser.isLoggedIn()) return;
        final JSONArray arr = new JSONArray();
        for (Item it : load()) {
            try {
                JSONObject o = new JSONObject();
                o.put("site", it.sourceKey == null ? "" : it.sourceKey);
                o.put("vod_id", it.vodId);
                o.put("name", it.name == null ? "" : it.name);
                o.put("pic", it.pic == null ? "" : it.pic);
                o.put("flag", it.flag == null ? "" : it.flag);
                o.put("pos", it.progress);
                o.put("dur", it.duration);
                o.put("ep", it.playIndex);
                arr.put(o);
            } catch (Throwable ignored) {
            }
        }
        if (arr.length() == 0) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    com.github.tvbox.osc.util.CloudBackupHelper.pushHistory(arr);
                } catch (Throwable ignored) {
                }
            }
        }).start();
    }

    /**
     * 从云端拉回并以「时间新的优先」合并进本地（换设备登录后首页就能看到继续观看）。
     * 只在登录态执行，静默失败。
     */
    public static void pullFromCloud() {
        if (!com.github.tvbox.osc.mobile.ui.MobileUser.isLoggedIn()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    com.github.tvbox.osc.util.CloudBackupHelper.Res r =
                            com.github.tvbox.osc.util.CloudBackupHelper.pullHistory(100);
                    if (r == null || !r.ok || r.data == null) return;
                    JSONArray items = r.data.optJSONArray("items");
                    if (items == null || items.length() == 0) return;
                    List<Item> local = load();
                    java.util.Map<String, Long> localTime = new java.util.HashMap<>();
                    for (Item it : local) localTime.put(it.sourceKey + "/" + it.vodId, it.time);
                    List<Item> merged = new ArrayList<>(local);
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject o = items.optJSONObject(i);
                        if (o == null) continue;
                        String vod = o.optString("vod_id", "");
                        if (vod.length() == 0) continue;
                        String site = o.optString("site", "");
                        String key = site + "/" + vod;
                        Long lt = localTime.get(key);
                        if (lt != null && lt > 0) continue; // 本地已有，交给 push 覆盖云端
                        Item it = new Item();
                        it.vodId = vod;
                        it.sourceKey = site;
                        it.name = o.optString("name", "");
                        it.pic = o.optString("pic", "");
                        it.flag = o.optString("flag", "");
                        it.playIndex = o.optInt("ep", 0);
                        it.progress = o.optLong("pos", 0);
                        it.duration = o.optLong("dur", 0);
                        it.time = System.currentTimeMillis();
                        merged.add(it);
                    }
                    while (merged.size() > 50) merged.remove(merged.size() - 1);
                    save(merged);
                } catch (Throwable ignored) {
                }
            }
        }).start();
    }
}
