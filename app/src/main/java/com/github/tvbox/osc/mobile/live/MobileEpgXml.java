package com.github.tvbox.osc.mobile.live;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.setting.CommonSetting;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 手机端直播回看节目单：解析 EPG 地址给出的 XMLTV（内置源是 playback.xml）。
 *
 * 只解析目标频道，避免 2.6MB 文件全量建模；整份原文按 EPG 地址缓存一次，
 * 换频道时只重新扫描字符串，不再重复下载。
 */
public class MobileEpgXml {

    public static class Program {
        public String title;
        public Date start;
        public Date stop;

        public String label() {
            return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(start)
                    + " - " + new SimpleDateFormat("HH:mm", Locale.CHINA).format(stop)
                    + "  " + title;
        }
    }

    public interface Callback {
        void onLoaded(List<Program> programs);

        void onFail(String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 自建 EPG 接口（云服务器 MacCMS 上的 epg.php），{name} 会替换成频道名 */
    public static final String CLOUD_EPG_TEMPLATE = "http://43.226.44.105:6066/epg.php?ch={name}";

    private static String cachedUrl = "";
    private static String cachedBody = null;
    private static final Map<String, List<Program>> channelCache = new HashMap<>();
    /** 已经确认「EPG 里没有这个频道节目单」的频道名，用来把回看按钮提前置灰，省一次无用下载等待 */
    private static final java.util.Set<String> KNOWN_NONE = new java.util.HashSet<>();

    public static boolean isKnownNoEpg(String channelName) {
        return channelName != null && KNOWN_NONE.contains(channelName);
    }

    /** 已解析过的频道节目单（未解析过返回 null，不触发下载） */
    public static List<Program> cachedPrograms(String channelName) {
        return channelName == null ? null : channelCache.get(channelName);
    }

    /** 当前 EPG 原文是否已在内存里（节目单信息条用） */
    public static boolean hasCachedBody() {
        return cachedBody != null;
    }

    /* ---------------- 本地磁盘缓存：避免每次冷启动都重下 2.6MB ---------------- */

    private static final long DISK_TTL_MS = 12L * 60 * 60 * 1000; // EPG 一天一版，12 小时内直接复用

    /** 每个 EPG 地址（含 ?ch= 模板展开后的实际地址）各自一个缓存文件，自建单频道接口时互不冲刷 */
    private static String diskFile(String url) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder("epg_");
            for (byte b : dig) {
                int v = b & 0xFF;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.append(".cache").toString();
        } catch (Throwable th) {
            return "epg_body.cache";
        }
    }

    private static String diskText(String url) {
        try {
            java.io.File f = new java.io.File(App.getInstance().getCacheDir(), diskFile(url));
            if (!f.exists() || f.length() <= 0) return null;
            if (System.currentTimeMillis() - f.lastModified() > DISK_TTL_MS) return null;
            String body = readAll(f);
            if (body == null || body.length() < 200) return null;
            int nl = body.indexOf('\n');
            if (nl <= 0) return null;
            if (!body.substring(0, nl).equals(url)) return null;
            return body.substring(nl + 1);
        } catch (Throwable th) {
            return null;
        }
    }

    private static void saveDisk(String url, String body) {
        try {
            java.io.File f = new java.io.File(App.getInstance().getCacheDir(), diskFile(url));
            writeAll(f, url + "\n" + body);
        } catch (Throwable ignored) {
        }
    }

    private static String readAll(java.io.File f) throws Throwable {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n <= 0) break;
                off += n;
            }
            return new String(buf, 0, off, "UTF-8");
        } finally {
            in.close();
        }
    }

    private static void writeAll(java.io.File f, String text) throws Throwable {
        java.io.File tmp = new java.io.File(f.getAbsolutePath() + ".tmp");
        java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
        try {
            byte[] data = text.getBytes("UTF-8");
            out.write(data);
            out.flush();
        } finally {
            out.close();
        }
        if (f.exists()) f.delete();
        tmp.renameTo(f);
    }

    /** 把设置里的 EPG 模板展开成这次真正要请求的地址：
     *  {name}/{channel} → 频道名（URL 编码），{date} → yyyyMMdd。
     *  自建接口 http://域名/epg.php?ch={name} 走这条，返回只有一个频道的小文件。 */
    private static String resolveUrl(String template, String channelName) {
        if (template == null) return template;
        if (!template.contains("{name}") && !template.contains("{channel}") && !template.contains("{date}")) {
            return template;
        }
        String name = channelName == null ? "" : channelName;
        String enc;
        try {
            enc = java.net.URLEncoder.encode(name, "UTF-8");
        } catch (Throwable th) {
            enc = name;
        }
        String date = new java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        return template.replace("{name}", enc).replace("{channel}", enc).replace("{date}", date);
    }

    /** 直播页初始化时预取一次 EPG（只把原文装进内存，不解析），让第一次点「回看」不再等下载 */
    public static void prefetch() {
        final String epgUrl;
        try {
            epgUrl = CommonSetting.getEpgUrl();
        } catch (Throwable th) {
            return;
        }
        // v1.6.37：EPG 默认留空，用户没填就完全不发请求（不再拿内置 playback.xml 兜底）
        final String template = epgUrl;
        if (TextUtils.isEmpty(template)) return;
        // 带 {name} 的模板必须知道频道才能请求，预取交给第一次点「回看」
        if (template.contains("{name}") || template.contains("{channel}") || template.contains("{date}")) return;
        if (cachedBody != null && template.equals(cachedUrl)) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!template.equals(cachedUrl)) {
                    channelCache.clear();
                    cachedUrl = template;
                }
                String body = diskText(template);
                if (body == null) {
                    body = fetchNetwork(template);
                    if (body != null) saveDisk(template, body);
                }
                if (body != null) cachedBody = body;
            }
        }).start();
    }

    private static String fetchNetwork(String url) {
        try {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Lavf/59.27.100")
                    .build();
            okhttp3.Response resp = client.newCall(req).execute();
            try {
                if (!resp.isSuccessful()) return null;
                okhttp3.ResponseBody rb = resp.body();
                return rb == null ? null : rb.string();
            } finally {
                resp.close();
            }
        } catch (Throwable th) {
            return null;
        }
    }

    public static void load(final String channelName, final Callback callback) {
        final String epgUrl;
        try {
            epgUrl = CommonSetting.getEpgUrl();
        } catch (Throwable th) {
            callback.onFail("EPG 地址读取失败");
            return;
        }
        // v1.6.37：EPG 默认空 —— 设置里没填直接给出提示，不偷偷回退到内置 playback.xml
        String useUrl = epgUrl;
        if (TextUtils.isEmpty(useUrl) || channelName == null || channelName.isEmpty()) {
            callback.onFail(TextUtils.isEmpty(useUrl) ? "没有配置 EPG 地址" : "频道名为空");
            return;
        }
        final String[] candidates = new String[]{
                resolveUrl(useUrl, channelName)
        };
        final String finalUrl = candidates[0];
        if (!finalUrl.equals(cachedUrl)) {
            cachedBody = null;
            channelCache.clear();
            cachedUrl = finalUrl;
        }
        List<Program> hit = channelCache.get(channelName);
        if (hit != null) {
            callback.onLoaded(hit);
            return;
        }
        final String body = cachedBody;
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (body != null && looksLikeXmltv(body)) {
                    finishParse(channelName, body, callback);
                    return;
                }
                String fromDisk = null;
                for (int i = 0; i < candidates.length; i++) {
                    String url = candidates[i];
                    if (i > 0 && url.equals(candidates[0])) continue;
                    // 每一档都先看本地磁盘，再走网络；命中合法的 XMLTV 就停
                    fromDisk = diskText(url);
                    if (!looksLikeXmltv(fromDisk)) {
                        fromDisk = fetchNetwork(url);
                        if (looksLikeXmltv(fromDisk)) saveDisk(url, fromDisk);
                        else fromDisk = null;
                    }
                    if (fromDisk != null) break;
                }
                if (fromDisk == null) {
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFail("节目单下载失败，检查设置里的 EPG 地址");
                        }
                    });
                    return;
                }
                cachedBody = fromDisk;
                finishParse(channelName, fromDisk, callback);
            }
        }).start();
    }

    private static boolean looksLikeXmltv(String body) {
        return body != null && body.contains("<tv") && body.contains("<programme");
    }

    private static void finishParse(String channelName, String body, Callback callback) {
        List<Program> programs;
        try {
            programs = parseForChannel(body, channelName);
        } catch (Throwable th) {
            final String msg = "节目单解析失败：" + th.getClass().getSimpleName();
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFail(msg);
                }
            });
            return;
        }
        channelCache.put(channelName, programs);
        if (programs.isEmpty()) KNOWN_NONE.add(channelName);
        else KNOWN_NONE.remove(channelName);
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                callback.onLoaded(programs);
            }
        });
    }

    private static List<Program> parseForChannel(String xml, String channelName) throws Exception {
        // 快路径：直接在原文里定位 channel="频道名" 的 <programme> 块。
        // 2.6MB / 1.49 万条节目时，比 XmlPullParser 逐事件扫全文档快一个量级。
        List<Program> fast = scanForChannel(xml, channelName);
        if (!fast.isEmpty()) return sortAndWindow(fast);
        // 兜底：快路径没命中（频道名转义/换行异常等）时仍按 XML 解析，保证不回归
        return sortAndWindow(parseByPull(xml, channelName));
    }

    private static List<Program> scanForChannel(String xml, String channelName) {
        List<Program> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        String needle = "channel=\"" + channelName + "\"";
        int from = 0;
        int len = xml.length();
        while (true) {
            int ch = xml.indexOf(needle, from);
            if (ch < 0) break;
            from = ch + needle.length();
            int tagStart = xml.lastIndexOf("<programme", ch);
            if (tagStart < 0) continue;
            int tagEnd = xml.indexOf('>', ch);
            if (tagEnd < 0) break;
            String tag = xml.substring(tagStart, tagEnd);
            String startRaw = attr(tag, "start");
            String stopRaw = attr(tag, "stop");
            int close = xml.indexOf("</programme>", tagEnd);
            if (close < 0) break;
            int t0 = xml.indexOf("<title", tagEnd);
            String title = "";
            if (t0 > 0 && t0 < close) {
                int tStart = xml.indexOf('>', t0);
                int tEnd = xml.indexOf("</title>", tStart);
                if (tStart > 0 && tEnd > tStart) {
                    title = unescape(xml.substring(tStart + 1, tEnd));
                }
            }
            from = close + 12;
            Date start = parseXmltvTime(startRaw);
            Date stop = parseXmltvTime(stopRaw);
            if (start == null || stop == null || title.trim().isEmpty()) continue;
            String key = start.getTime() + "|" + stop.getTime() + "|" + title.trim();
            if (!seen.add(key)) continue;
            Program p = new Program();
            p.start = start;
            p.stop = stop;
            p.title = title.trim();
            result.add(p);
        }
        return result;
    }

    private static String attr(String tag, String name) {
        String key = name + "=\"";
        int i = tag.indexOf(key);
        if (i < 0) return null;
        int s = i + key.length();
        int e = tag.indexOf('"', s);
        return e < 0 ? null : tag.substring(s, e);
    }

    private static String unescape(String raw) {
        if (raw.indexOf('&') < 0) return raw;
        return raw.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    /** 只保留「最近 72 小时已开始」到「未来 24 小时待播」的节目，并按开始时间正序 */
    private static List<Program> sortAndWindow(List<Program> result) {
        long now = System.currentTimeMillis();
        long earliest = now - 72L * 60 * 60 * 1000;
        long latest = now + 24L * 60 * 60 * 1000;
        List<Program> keep = new ArrayList<>();
        for (Program p : result) {
            long start = p.start.getTime();
            if (start >= earliest && start <= latest) keep.add(p);
        }
        Collections.sort(keep, new Comparator<Program>() {
            @Override
            public int compare(Program a, Program b) {
                return Long.compare(a.start.getTime(), b.start.getTime());
            }
        });
        return keep;
    }

    private static List<Program> parseByPull(String xml, String channelName) throws Exception {
        List<Program> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(xml));

        Program current = null;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            String tag = parser.getName();
            if (event == XmlPullParser.START_TAG) {
                if ("programme".equals(tag)) {
                    String channel = parser.getAttributeValue(null, "channel");
                    String startRaw = parser.getAttributeValue(null, "start");
                    String stopRaw = parser.getAttributeValue(null, "stop");
                    if (channel != null && channel.trim().equals(channelName)) {
                        current = new Program();
                        current.start = parseXmltvTime(startRaw);
                        current.stop = parseXmltvTime(stopRaw);
                        current.title = "";
                    } else {
                        current = null;
                    }
                } else if (current != null && "title".equals(tag)) {
                    current.title = parser.nextText();
                }
            } else if (event == XmlPullParser.END_TAG) {
                if ("programme".equals(tag) && current != null) {
                    if (current.start != null && current.stop != null && current.title != null
                            && !current.title.trim().isEmpty()) {
                        // EPG 原文里同一时段经常重复两条（双源合并导致），这里按 起止+标题 去重
                        String key = current.start.getTime() + "|" + current.stop.getTime()
                                + "|" + current.title.trim();
                        if (seen.add(key)) result.add(current);
                    }
                    current = null;
                }
            }
            event = parser.next();
        }
        return result;
    }

    /** XMLTV 时间两种写法：20261113060000 +0800（带时区）、20261113060000（按本机时区） */
    private static Date parseXmltvTime(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.length() < 14) return null;
        String head = value.substring(0, 14);
        try {
            if (value.length() > 15) {
                String zone = value.substring(14).trim();
                // 复用 SimpleDateFormat：一次解析 2.9 万个时间点，每次 new 曾是最大 CPU 开销
                SimpleDateFormat fmt = ZONE_FMT.get();
                return fmt.parse(head + " " + zone);
            }
            return PLAIN_FMT.get().parse(head);
        } catch (Throwable th) {
            return null;
        }
    }

    private static final ThreadLocal<SimpleDateFormat> ZONE_FMT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
        }
    };

    private static final ThreadLocal<SimpleDateFormat> PLAIN_FMT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        }
    };
}
