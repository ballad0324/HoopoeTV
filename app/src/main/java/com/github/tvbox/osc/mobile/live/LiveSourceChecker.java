package com.github.tvbox.osc.mobile.live;

import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 直播源有效性检测：只判断「这个地址还连不连得上」，不做解码。
 *
 * 做法：带 Range 的 GET，拿到任何 HTTP 响应即算源还在（403/405 也说明服务器活着），
 * 只有 DNS/连接失败、超时、404/410 才算失效。结果带 10 分钟缓存，避免反复探测。
 * 并发 4，全部在后台线程，回调统一回到调用方自己切线程。
 */
public final class LiveSourceChecker {

    private static final String TAG = "LiveSourceChecker";
    private static final long TTL_MS = 10 * 60 * 1000L;
    private static final int CONNECT_TIMEOUT = 5;
    private static final int READ_TIMEOUT = 6;

    public interface Callback {
        void onResult(String url, boolean alive);
    }

    private static final class Result {
        final boolean alive;
        final long at;

        Result(boolean alive, long at) {
            this.alive = alive;
            this.at = at;
        }
    }

    private static final Map<String, Result> CACHE = new ConcurrentHashMap<>();
    private static final List<String> IN_FLIGHT = new ArrayList<>();
    private static ExecutorService pool;
    private static OkHttpClient client;

    private LiveSourceChecker() {
    }

    private static synchronized ExecutorService pool() {
        if (pool == null) {
            final AtomicInteger seq = new AtomicInteger();
            pool = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "live-check-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });
        }
        return pool;
    }

    private static synchronized OkHttpClient client() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .retryOnConnectionFailure(false)
                    .build();
        }
        return client;
    }

    /** 缓存里是否已有有效结论 */
    public static Boolean cached(String url) {
        if (TextUtils.isEmpty(url)) return null;
        Result r = CACHE.get(url);
        if (r == null) return null;
        if (System.currentTimeMillis() - r.at > TTL_MS) {
            CACHE.remove(url);
            return null;
        }
        return r.alive;
    }

    public static void clearCache() {
        CACHE.clear();
        synchronized (IN_FLIGHT) {
            IN_FLIGHT.clear();
        }
    }

    /** 异步检测；同一 url 同时只会有一个探测在跑 */
    public static void check(final String url, final Callback cb) {
        if (TextUtils.isEmpty(url) || cb == null) return;
        Boolean hit = cached(url);
        if (hit != null) {
            cb.onResult(url, hit);
            return;
        }
        synchronized (IN_FLIGHT) {
            if (IN_FLIGHT.contains(url)) return;
            IN_FLIGHT.add(url);
        }
        pool().execute(new Runnable() {
            @Override
            public void run() {
                boolean alive;
                try {
                    alive = probe(url);
                } catch (Throwable t) {
                    Log.w(TAG, "probe failed " + url + " : " + t.getMessage());
                    alive = false;
                } finally {
                    synchronized (IN_FLIGHT) {
                        IN_FLIGHT.remove(url);
                    }
                }
                CACHE.put(url, new Result(alive, System.currentTimeMillis()));
                cb.onResult(url, alive);
            }
        });
    }

    private static boolean probe(String url) {
        String lower = url.toLowerCase();
        if (lower.startsWith("rtmp") || lower.startsWith("rtsp") || lower.startsWith("p2p")
                || lower.startsWith("mitv")) {
            // 非 HTTP 协议没法用 HEAD/GET 判断，按「未知=正常」处理
            return true;
        }
        Request request = new Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1")
                .header("User-Agent", "okhttp/3.12.0")
                .get()
                .build();
        Response response = null;
        try {
            response = client().newCall(request).execute();
            int code = response.code();
            if (code == 404 || code == 410 || code >= 500) return false;
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (response != null) response.close();
        }
    }
}