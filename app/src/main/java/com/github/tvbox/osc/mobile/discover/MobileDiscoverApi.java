package com.github.tvbox.osc.mobile.discover;

import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 「发现」页的数据层：直接读当前数据源的 MacCMS 兼容接口
 * （ac=videolist&t=类型&pg=页码&year=年份），字段比 TVBox 列表接口更全：
 * vod_pubdate / vod_hits(_day/_week/_month) / vod_score / vod_blurb 都在，
 * 所以上映表、排行榜都能在端上现算，不需要服务端额外加接口。
 */
public class MobileDiscoverApi {

    /** 根分类 id（ac=list 里的 class 数组，实测该源：1 电影 / 2 剧集 / 3 综艺 / 4 动漫 / 5 短剧 / 8 纪录片） */
    public static final int TYPE_MOVIE = 1;
    public static final int TYPE_SERIES = 2;
    public static final int TYPE_SHOW = 3;
    public static final int TYPE_ANIME = 4;
    public static final int TYPE_SHORT = 5;
    public static final int TYPE_DOC = 8;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private MobileDiscoverApi() {
    }

    /** 当前数据源的接口地址；拿不到时返回空串 */
    public static String baseUrl() {
        try {
            SourceBean sb = ApiConfig.get().getHomeSourceBean();
            if (sb != null && !TextUtils.isEmpty(sb.getApi())) return sb.getApi();
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * 取一页影片。type ≤ 0 表示不限类型（全站最新）。
     * year 为空表示不限年份。
     */
    public static List<DiscoverItem> fetch(int type, int page, String year) {
        return fetchWithMeta(type, page, year).items;
    }

    /** 一页数据 + 服务端返回的 pagecount/total，供分类浏览做「滚到底自动续页」 */
    public static class Paged {
        public final List<DiscoverItem> items = new ArrayList<>();
        public int page = 1;
        public int pageCount;
        public int total;

        public boolean hasMore() {
            return pageCount <= 0 || page < pageCount;
        }
    }

    /** 取一页影片并带上 pagecount，供浏览列表判断还有没有下一页 */
    public static Paged fetchWithMeta(int type, int page, String year) {
        Paged out = new Paged();
        out.page = Math.max(1, page);
        String base = baseUrl();
        if (TextUtils.isEmpty(base)) return out;
        StringBuilder url = new StringBuilder(base);
        url.append(base.contains("?") ? "&" : "?");
        url.append("ac=videolist&pg=").append(out.page);
        if (type > 0) url.append("&t=").append(type);
        if (!TextUtils.isEmpty(year)) url.append("&year=").append(year);
        String body = get(url.toString());
        if (TextUtils.isEmpty(body)) return out;
        out.items.addAll(parse(body));
        try {
            JSONObject root = new JSONObject(body);
            out.pageCount = root.optInt("pagecount", 0);
            out.total = root.optInt("total", 0);
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String get(String url) {
        try {
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Lavf/59.27.100")
                    .build();
            Response resp = CLIENT.newCall(req).execute();
            try {
                if (!resp.isSuccessful()) return null;
                ResponseBody rb = resp.body();
                return rb == null ? null : rb.string();
            } finally {
                resp.close();
            }
        } catch (Throwable th) {
            return null;
        }
    }

    private static List<DiscoverItem> parse(String body) {
        List<DiscoverItem> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(body);
            JSONArray list = root.optJSONArray("list");
            if (list == null) return out;
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                if (o == null) continue;
                DiscoverItem it = new DiscoverItem();
                it.id = o.optString("vod_id", "");
                it.name = o.optString("vod_name", "");
                it.pic = o.optString("vod_pic", "");
                it.year = o.optString("vod_year", "");
                it.area = o.optString("vod_area", "");
                it.cls = o.optString("vod_class", "");
                it.remarks = o.optString("vod_remarks", "");
                it.actor = o.optString("vod_actor", "");
                it.director = o.optString("vod_director", "");
                it.pubdate = o.optString("vod_pubdate", "");
                it.blurb = o.optString("vod_blurb", "");
                it.content = o.optString("vod_content", "");
                it.score = o.optDouble("vod_score", 0);
                it.hits = o.optLong("vod_hits", 0);
                it.hitsDay = o.optLong("vod_hits_day", 0);
                it.hitsWeek = o.optLong("vod_hits_week", 0);
                it.hitsMonth = o.optLong("vod_hits_month", 0);
                if (TextUtils.isEmpty(it.id) || TextUtils.isEmpty(it.name)) continue;
                out.add(it);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 上映日期（vod_pubdate 去掉括号里的地区；没有就退回 vod_time 的日期部分） */
    public static String pubDate(DiscoverItem it) {
        if (it == null) return "";
        String raw = it.pubdate == null ? "" : it.pubdate.trim();
        if (raw.length() >= 10 && raw.charAt(4) == '-') return raw.substring(0, 10);
        return "";
    }
}
