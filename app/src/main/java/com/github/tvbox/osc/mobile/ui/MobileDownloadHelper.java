package com.github.tvbox.osc.mobile.ui;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 手机端视频源文件下载 + 自动封装成 mp4。
 *
 * 流程：
 * 1) 直链 mp4：直接落盘，就是 mp4，无需再处理；
 * 2) 直链其它（flv/ts 等）：落盘后尝试 remux 成 mp4；
 * 3) m3u8：拉 playlist（自动下钻 master playlist）→ 顺序下所有分片合并成临时 .ts
 *    → 用 MediaExtractor + MediaMuxer 重新封装成标准 mp4（不重编码，秒级完成）。
 * remux 失败时保留原文件（.ts/.flv），不会丢数据。
 * 保存目录：/Android/data/<包名>/files/download/（免存储权限，文件管理器可访问）。
 */
public final class MobileDownloadHelper {

    /** 分片下载缓冲 */
    private static final int BUF = 8192;
    /** remux 读写样本用的缓冲，关键帧可能较大，给足 8MB */
    private static final int SAMPLE_BUF = 8 * 1024 * 1024;

    private MobileDownloadHelper() {
    }

    public static void start(final Context context, final String title, final String url,
                             final HashMap<String, String> headers) {
        if (context == null) return;
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(context, "还没有拿到播放地址，先点一集开始播放再下载", Toast.LENGTH_SHORT).show();
            return;
        }
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        Toast.makeText(context, "开始下载…", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                String savedPath = null;
                boolean converted = false;
                try {
                    String[] ret = download(app, title, url, headers);
                    if (ret != null) {
                        savedPath = ret[0];
                        converted = "1".equals(ret[1]);
                    }
                } catch (Throwable th) {
                    savedPath = null;
                }
                final String path = savedPath;
                final boolean mp4 = converted;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (path == null) {
                            Toast.makeText(context, "下载失败：地址不可用或网络中断", Toast.LENGTH_LONG).show();
                        } else if (mp4) {
                            Toast.makeText(context, "下载完成，已转码为 mp4：\n" + path, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, "下载完成（转码 mp4 未成功，保留原文件）：\n" + path,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * 直播录制下载（v1.6.25）。
     * 直播 m3u8 没有 #EXT-X-ENDLIST（永远下不完），这里按分片累加时长截取 seconds 秒；
     * 回看 / 点播型 m3u8（带 ENDLIST）或普通直链则整段下载。
     */
    public static void startLive(final Context context, final String title, final String url,
                                 final HashMap<String, String> headers, final int seconds) {
        if (context == null) return;
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(context, "还没有拿到播放地址", Toast.LENGTH_SHORT).show();
            return;
        }
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final int secs = seconds <= 0 ? 60 : seconds;
        Toast.makeText(context, "开始录制 " + secs + " 秒…", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                String savedPath = null;
                boolean converted = false;
                try {
                    String[] ret = downloadLive(app, title, url, headers, secs);
                    if (ret != null) {
                        savedPath = ret[0];
                        converted = "1".equals(ret[1]);
                    }
                } catch (Throwable th) {
                    savedPath = null;
                }
                final String path = savedPath;
                final boolean mp4 = converted;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (path == null) {
                            Toast.makeText(context, "录制失败：直播地址不可用或网络中断", Toast.LENGTH_LONG).show();
                        } else if (mp4) {
                            Toast.makeText(context, "已录制 " + secs + " 秒，已封装为 mp4：\n" + path,
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, "已录制 " + secs + " 秒（mp4 封装未成功，保留原文件）：\n" + path,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private static String[] downloadLive(Context ctx, String title, String url,
                                         HashMap<String, String> headers, int seconds) throws Exception {
        String[] first = readText2(url, headers, 6000);
        String body = first == null ? null : first[0];
        String finalUrl = first == null ? url : first[1];
        if (body == null) throw new IllegalStateException("拿不到播放列表");
        if (body.trim().startsWith("#EXTM3U")) {
            if (body.contains("#EXT-X-ENDLIST")) {
                // 回看 / 点播型 m3u8 有结束标记，跟普通下载一样整段下完
                return download(ctx, title, url, headers);
            }
            String base = safeName(title);
            File ts = new File(downloadDir(ctx), base + ".ts");
            FileOutputStream fos = new FileOutputStream(ts, false);
            double total = 0;
            java.util.Set<String> got = new java.util.HashSet<>();
            try {
                // 302 跳转后真实地址才是分片相对路径的基准（直播源普遍是 302 到 CDN）
                String mediaUrl = resolveMediaPlaylist(finalUrl, body, headers);
                // 直播播放列表是滑动窗口（可能只含 20~40 秒），没录满就再拉几次清单接着录
                for (int pass = 0; pass < 8 && total < seconds; pass++) {
                    String playlist = (pass == 0 && mediaUrl.equals(url)) ? body
                            : readText(mediaUrl, headers, 6000);
                    if (playlist == null || !playlist.contains("#EXTINF")) break;
                    double pending = 0;
                    boolean downloaded = false;
                    for (String line : playlist.split("\n")) {
                        String t = line.trim();
                        if (t.isEmpty()) continue;
                        if (t.startsWith("#EXTINF:")) {
                            try {
                                pending = Double.parseDouble(t.substring(8).split(",")[0]);
                            } catch (Throwable th) {
                                pending = 0;
                            }
                            continue;
                        }
                        if (t.startsWith("#")) continue;
                        String seg = absUrl(mediaUrl, t);
                        if (got.contains(seg)) continue;
                        InputStream is = openStream(seg, headers);
                        byte[] buf = new byte[BUF];
                        int n;
                        while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                        is.close();
                        got.add(seg);
                        downloaded = true;
                        total += pending <= 0 ? 5 : pending;
                        if (total >= seconds) break;
                    }
                    if (!downloaded) break;
                }
            } finally {
                fos.close();
            }
            if (total <= 0) throw new IllegalStateException("没有录到任何分片");
            File mp4 = new File(downloadDir(ctx), base + ".mp4");
            if (remuxToMp4(ts, mp4)) {
                //noinspection ResultOfMethodCallIgnored
                ts.delete();
                return new String[]{mp4.getAbsolutePath(), "1"};
            }
            return new String[]{ts.getAbsolutePath(), "0"};
        }

        // 非 m3u8 直播流（直接 ts/flv）：按时间截断落盘
        String base = safeName(title);
        String ext = ".ts";
        int q = url.indexOf('?');
        String p = q > 0 ? url.substring(0, q) : url;
        int dot = p.lastIndexOf('.');
        if (dot > p.lastIndexOf('/')) {
            String e = p.substring(dot);
            if (e.length() <= 5) ext = e;
        }
        File out = new File(downloadDir(ctx), base + ext);
        InputStream is = openStream(url, headers);
        FileOutputStream fos = new FileOutputStream(out, false);
        long end = System.currentTimeMillis() + seconds * 1000L;
        try {
            byte[] buf = new byte[BUF];
            int n;
            while (System.currentTimeMillis() < end && (n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        } finally {
            try {
                is.close();
            } catch (Throwable ignored) {
            }
            fos.close();
        }
        File mp4 = new File(downloadDir(ctx), base + ".mp4");
        if (remuxToMp4(out, mp4)) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return new String[]{mp4.getAbsolutePath(), "1"};
        }
        return new String[]{out.getAbsolutePath(), "0"};
    }

    /** 下载目录（App 外部私有目录，免权限） */
    public static File downloadDir(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(null), "download");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 已下载文件，新的排前面 */
    public static List<File> listDownloads(Context ctx) {
        File dir = downloadDir(ctx);
        File[] files = dir.listFiles();
        List<File> list = new ArrayList<>();
        if (files == null) return list;
        for (File f : files) {
            if (f.isFile()) list.add(f);
        }
        java.util.Collections.sort(list, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return list;
    }

    private static String safeName(String title) {
        String name = TextUtils.isEmpty(title) ? "video" : title;
        name = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (name.length() > 60) name = name.substring(0, 60);
        return name + "_" + new SimpleDateFormat("MMddHHmm", Locale.US).format(new Date());
    }

    /**
     * @return String[]{最终文件绝对路径, 是否已经是 mp4("1"/"0")}；失败返回 null
     */
    private static String[] download(Context ctx, String title, String url,
                                    HashMap<String, String> headers) throws Exception {
        String body = readText(url, headers, 6000);

        // ---- m3u8：分片下载合并成 ts，再封装 mp4 ----
        if (body != null && body.trim().startsWith("#EXTM3U")) {
            String mediaUrl = resolveMediaPlaylist(url, body, headers);
            String playlist = mediaUrl.equals(url) ? body : readText(mediaUrl, headers, 6000);
            if (playlist == null || !playlist.contains("#EXTINF")) {
                throw new IllegalStateException("m3u8 内容不完整");
            }
            List<String> segs = new ArrayList<>();
            for (String line : playlist.split("\n")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                segs.add(absUrl(mediaUrl, t));
            }
            if (segs.isEmpty()) throw new IllegalStateException("m3u8 没有分片");

            String base = safeName(title);
            File ts = new File(downloadDir(ctx), base + ".ts");
            FileOutputStream fos = new FileOutputStream(ts, false);
            try {
                for (String seg : segs) {
                    InputStream is = openStream(seg, headers);
                    byte[] buf = new byte[BUF];
                    int n;
                    while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                    is.close();
                }
            } finally {
                fos.close();
            }
            File mp4 = new File(downloadDir(ctx), base + ".mp4");
            if (remuxToMp4(ts, mp4)) {
                // 转码成功后删掉临时 ts，避免重复占空间
                //noinspection ResultOfMethodCallIgnored
                ts.delete();
                return new String[]{mp4.getAbsolutePath(), "1"};
            }
            return new String[]{ts.getAbsolutePath(), "0"};
        }

        // ---- 直链 ----
        String ext = ".mp4";
        int q = url.indexOf('?');
        String path = q > 0 ? url.substring(0, q) : url;
        int dot = path.lastIndexOf('.');
        if (dot > path.lastIndexOf('/')) {
            ext = path.substring(dot);
            if (ext.length() > 6) ext = ".mp4";
        }
        String base = safeName(title);
        File out = new File(downloadDir(ctx), base + ext);
        InputStream is = openStream(url, headers);
        FileOutputStream fos = new FileOutputStream(out, false);
        try {
            byte[] buf = new byte[BUF];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
        } finally {
            try {
                is.close();
            } catch (Throwable ignored) {
            }
            fos.close();
        }
        if (".mp4".equalsIgnoreCase(ext)) {
            return new String[]{out.getAbsolutePath(), "1"};
        }
        // flv/ts/mkv 等：尝试封装成 mp4
        File mp4 = new File(downloadDir(ctx), base + ".mp4");
        if (remuxToMp4(out, mp4)) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return new String[]{mp4.getAbsolutePath(), "1"};
        }
        return new String[]{out.getAbsolutePath(), "0"};
    }

    /**
     * 用系统 MediaExtractor + MediaMuxer 把 ts/flv/mkv 重新封装为 mp4。
     * 不重新编码（秒级），只重排时间轴与容器；音轨为 mp2 这类 mp4 不支持的编码时会失败，返回 false。
     * 样本按解码时间戳顺序交错写入，保证播放器能正常拖动进度。
     */
    public static boolean remuxToMp4(File src, File dst) {
        MediaExtractor extractor = null;
        MediaMuxer muxer = null;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(src.getAbsolutePath());
            int trackCount = extractor.getTrackCount();
            if (trackCount <= 0) return false;

            int[] targetTrack = new int[trackCount];
            Arrays.fill(targetTrack, -1);
            muxer = new MediaMuxer(dst.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoTracks = 0;
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    targetTrack[i] = muxer.addTrack(format);
                    if (mime.startsWith("video/")) videoTracks++;
                }
            }
            if (videoTracks == 0) return false;

            muxer.start();
            for (int i = 0; i < trackCount; i++) {
                if (targetTrack[i] >= 0) extractor.selectTrack(i);
            }

            ByteBuffer buffer = ByteBuffer.allocate(SAMPLE_BUF);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int written = 0;
            while (true) {
                int track = extractor.getSampleTrackIndex();
                if (track < 0) break;
                if (targetTrack[track] < 0) {
                    extractor.advance();
                    continue;
                }
                buffer.clear();
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                info.set(0, size, extractor.getSampleTime(), extractor.getSampleFlags());
                muxer.writeSampleData(targetTrack[track], buffer, info);
                written++;
                if (!extractor.advance()) break;
            }
            return written > 0;
        } catch (Throwable th) {
            return false;
        } finally {
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (Throwable ignored) {
                }
                try {
                    muxer.release();
                } catch (Throwable ignored) {
                }
            }
            if (extractor != null) {
                try {
                    extractor.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String resolveMediaPlaylist(String baseUrl, String body,
                                               HashMap<String, String> headers) throws Exception {
        if (!body.contains("#EXT-X-STREAM-INF")) return baseUrl;
        // master playlist：取第一个码率变体
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            return absUrl(baseUrl, t);
        }
        return baseUrl;
    }

    private static String absUrl(String base, String rel) {
        try {
            return new URL(new URL(base), rel).toString();
        } catch (Throwable th) {
            return rel;
        }
    }

    /** 建连接（带默认 UA、超时、跟随 302），供 openStream / readText2 共用 */
    private static HttpURLConnection openConn(String url, HashMap<String, String> headers) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent",
                headers != null && headers.containsKey("User-Agent")
                        ? headers.get("User-Agent") : "Lavf/59.27.100");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                if ("User-Agent".equalsIgnoreCase(e.getKey())) continue;
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        return conn;
    }

    /**
     * 读文本并回传「302 之后的真实地址」。
     * 直播源多是 302 跳到 CDN 的 master playlist，相对分片地址必须按跳转后的地址解析，
     * 否则会把分片拼到原始域名上（拿不到数据，录制就是 0 字节）。
     *
     * @return String[]{正文, 最终地址}
     */
    private static String[] readText2(String url, HashMap<String, String> headers, int limit) throws Exception {
        HttpURLConnection conn = openConn(url, headers);
        InputStream is = conn.getInputStream();
        String finalUrl = conn.getURL().toString();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[BUF];
        int n;
        int total = 0;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
            total += n;
            if (total > limit) break;
        }
        try {
            is.close();
        } catch (Throwable ignored) {
        }
        return new String[]{bos.toString("UTF-8"), finalUrl};
    }

    private static InputStream openStream(String url, HashMap<String, String> headers) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent",
                headers != null && headers.containsKey("User-Agent")
                        ? headers.get("User-Agent") : "Lavf/59.27.100");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                if ("User-Agent".equalsIgnoreCase(e.getKey())) continue;
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        return conn.getInputStream();
    }

    private static String readText(String url, HashMap<String, String> headers, int limit) throws Exception {
        InputStream is = openStream(url, headers);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[BUF];
        int n;
        int total = 0;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
            total += n;
            if (total > limit) break;
        }
        try {
            is.close();
        } catch (Throwable ignored) {
        }
        return bos.toString("UTF-8");
    }
}
