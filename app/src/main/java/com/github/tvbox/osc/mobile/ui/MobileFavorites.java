package com.github.tvbox.osc.mobile.ui;

import com.github.tvbox.osc.cache.FavRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 手机端收藏的兼容外壳。
 *
 * 实际存储在 Room 表 vodCollect（见 {@link FavRepository}），与电视端共用同一份数据，
 * 同一台设备切换界面模式时收藏立即互通。这里保留原方法签名，避免改动各页面的调用点。
 */
public class MobileFavorites {

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
        // 手机端要显示直播频道收藏，所以取全量；电视端在 CollectActivity 里自行过滤 live 条目
        for (FavRepository.Item src : FavRepository.load()) {
            list.add(convert(src));
        }
        return list;
    }

    public static boolean has(String vodId, String sourceKey) {
        return FavRepository.has(vodId, sourceKey);
    }

    /** 收藏 / 取消收藏，返回操作后是否为已收藏状态 */
    public static boolean toggle(Item item) {
        if (item == null) return false;
        return FavRepository.toggle(toRepo(item));
    }

    /** 跨端同步用：本机没有才追加 */
    public static boolean add(Item item) {
        if (item == null) return false;
        return FavRepository.add(toRepo(item));
    }

    public static void remove(String vodId, String sourceKey) {
        FavRepository.remove(vodId, sourceKey);
    }

    private static Item convert(FavRepository.Item src) {
        Item it = new Item();
        it.vodId = src.vodId;
        it.sourceKey = src.sourceKey;
        it.name = src.name;
        it.pic = src.pic;
        it.note = src.note;
        it.time = src.time;
        return it;
    }

    private static FavRepository.Item toRepo(Item it) {
        FavRepository.Item src = new FavRepository.Item();
        src.vodId = it.vodId;
        src.sourceKey = it.sourceKey;
        src.name = it.name;
        src.pic = it.pic;
        src.note = it.note;
        src.time = it.time;
        return src;
    }
}
