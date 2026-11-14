package com.github.tvbox.osc.mobile.play;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.ParseBean;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.HttpHeaders;
import com.lzy.okgo.model.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;

/**
 * 手机端播放页的解析引擎（不再把 parse/jx 线路丢给 TV 的 PlayActivity）。
 *
 * 覆盖三种 ParseBean：
 *  - type 1：json 解析接口（OkGo 直接请求，最快最稳）
 *  - type 0：普通嗅探（内置 1x1 隐藏 WebView，拦截 m3u8/mp4 请求 + 页面加载完成后扫 video/source）
 *  - 其它（2/3/4 混合、聚合）：不在手机端实现，回调失败由调用方兜底
 */
public final class MobileParseHelper {

    private static final int SNIFF_TIMEOUT_MS = 25000;

    public interface Callback {
        void onParsed(String url, HashMap<String, String> headers);

        void onFail(String reason);
    }

    private MobileParseHelper() {
    }

    public static void parse(Activity act, boolean userJx, String playUrl, String webUrl, Callback cb) {
        try {
            ParseBean pb = null;
            if (userJx) {
                pb = ApiConfig.get().getDefaultParse();
            } else {
                if (playUrl != null && playUrl.startsWith("json:")) {
                    pb = new ParseBean();
                    pb.setType(1);
                    pb.setUrl(playUrl.substring(5));
                } else if (playUrl != null && playUrl.startsWith("parse:")) {
                    String redirect = playUrl.substring(6);
                    for (ParseBean p : ApiConfig.get().getParseBeanList()) {
                        if (redirect.equals(p.getName())) {
                            pb = p;
                            break;
                        }
                    }
                }
                if (pb == null) {
                    pb = new ParseBean();
                    pb.setType(0);
                    pb.setUrl(playUrl == null ? "" : playUrl);
                }
            }
            if (pb == null) {
                cb.onFail("没有可用的解析配置");
                return;
            }
            String target = webUrl == null ? "" : webUrl;
            if (pb.getType() == 1) {
                jsonParse(pb, target, cb);
            } else if (pb.getType() == 0) {
                sniff(act, pb, target, cb);
            } else {
                cb.onFail("该线路使用混合解析（type=" + pb.getType() + "），手机端暂未内置");
            }
        } catch (Throwable t) {
            cb.onFail("解析异常：" + t.getMessage());
        }
    }

    private static String encode(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (Exception e) {
            return url;
        }
    }

    /** type 1：json 接口 */
    private static void jsonParse(final ParseBean pb, String webUrl, final Callback cb) {
        HttpHeaders headers = new HttpHeaders();
        try {
            if (pb.getExt() != null && !pb.getExt().isEmpty()) {
                JSONObject ext = new JSONObject(pb.getExt());
                if (ext.has("header")) {
                    JSONObject h = ext.optJSONObject("header");
                    if (h != null) {
                        java.util.Iterator<String> keys = h.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            headers.put(k, h.optString(k, ""));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        OkGo.<String>get(pb.getUrl() + encode(webUrl))
                .tag("mobile_parse_json")
                .headers(headers)
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) return response.body().string();
                        throw new IllegalStateException("解析接口返回为空");
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            String body = response.body();
                            JSONObject json = new JSONObject(body);
                            String url;
                            if (json.has("data")) url = json.getJSONObject("data").getString("url");
                            else url = json.getString("url");
                            if (url.startsWith("//")) url = "http:" + url;
                            if (!url.startsWith("http")) {
                                cb.onFail("解析结果不是可播放地址");
                                return;
                            }
                            HashMap<String, String> hs = new HashMap<>();
                            String ua = json.optString("user-agent", "");
                            if (!ua.trim().isEmpty()) hs.put("User-Agent", ua.trim());
                            String referer = json.optString("referer", "");
                            if (!referer.trim().isEmpty()) hs.put("Referer", referer.trim());
                            cb.onParsed(url, hs);
                        } catch (Throwable t) {
                            cb.onFail("解析结果无法识别");
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        Throwable t = response.getException();
                        cb.onFail("解析接口请求失败" + (t == null ? "" : "：" + t.getMessage()));
                    }
                });
    }

    /** type 0：内置隐藏 WebView 嗅探 */
    private static void sniff(final Activity act, final ParseBean pb, final String webUrl, final Callback cb) {
        if (act == null || act.isFinishing()) {
            cb.onFail("页面已关闭");
            return;
        }
        final Handler main = new Handler(Looper.getMainLooper());
        final boolean[] done = new boolean[]{false};
        final WebView wv = new WebView(act);
        final String pageUrl = pb.getUrl() + (webUrl == null ? "" : webUrl);

        final Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                try {
                    ViewGroup parent = (ViewGroup) wv.getParent();
                    if (parent != null) parent.removeView(wv);
                    wv.stopLoading();
                    wv.loadUrl("about:blank");
                    wv.destroy();
                } catch (Throwable ignored) {
                }
            }
        };

        final Runnable timeout = new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                cleanup.run();
                cb.onFail("嗅探超时，未找到播放地址");
            }
        };

        try {
            wv.getSettings().setJavaScriptEnabled(true);
            wv.getSettings().setDomStorageEnabled(true);
            wv.getSettings().setMediaPlaybackRequiresUserGesture(false);
            if (pb.getExt() != null && !pb.getExt().isEmpty()) {
                try {
                    JSONObject ext = new JSONObject(pb.getExt());
                    JSONObject h = ext.optJSONObject("header");
                    if (h != null && h.has("user-agent")) {
                        wv.getSettings().setUserAgentString(h.optString("user-agent"));
                    }
                } catch (Throwable ignored) {
                }
            }
            wv.setWebViewClient(new WebViewClient() {
                private void hit(String u) {
                    if (u == null || done[0]) return;
                    String lower = u.toLowerCase();
                    if (lower.startsWith("data:") || lower.startsWith("blob:")) return;
                    boolean media = false;
                    if (lower.contains(".m3u8")) media = true;
                    else if (lower.contains(".mp4") && !lower.contains("poster") && !lower.contains("cover")) media = true;
                    else if (lower.contains(".flv") || lower.contains(".mpd")) media = true;
                    if (!media) return;
                    done[0] = true;
                    main.removeCallbacks(timeout);
                    cleanup.run();
                    HashMap<String, String> hs = new HashMap<>();
                    hs.put("Referer", pageUrl);
                    cb.onParsed(u, hs);
                }

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if (request != null) hit(request.getUrl().toString());
                    return null;
                }

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                    hit(url);
                    return null;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (done[0]) return;
                    try {
                        view.evaluateJavascript("(function(){var o=[];var n=document.querySelectorAll('video,source');for(var i=0;i<n.length;i++){if(n[i].src)o.push(n[i].src);}return JSON.stringify(o);})()",
                                new android.webkit.ValueCallback<String>() {
                                    @Override
                                    public void onReceiveValue(String value) {
                                        if (done[0] || value == null) return;
                                        try {
                                            if (!value.contains("m3u8") && !value.contains("mp4") && !value.contains("flv")) return;
                                            JSONArray arr = new JSONArray(value);
                                            for (int i = 0; i < arr.length(); i++) {
                                                String u = arr.optString(i, "");
                                                if (u.contains("m3u8") || u.contains("mp4") || u.contains("flv")) {
                                                    if (done[0]) return;
                                                    done[0] = true;
                                                    main.removeCallbacks(timeout);
                                                    cleanup.run();
                                                    HashMap<String, String> hs = new HashMap<>();
                                                    hs.put("Referer", pageUrl);
                                                    cb.onParsed(u, hs);
                                                    return;
                                                }
                                            }
                                        } catch (Throwable ignored) {
                                        }
                                    }
                                });
                    } catch (Throwable ignored) {
                    }
                }
            });

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
            wv.setAlpha(0.01f);
            ViewGroup content = act.findViewById(android.R.id.content);
            if (content != null) content.addView(wv, lp);
            main.postDelayed(timeout, SNIFF_TIMEOUT_MS);
            wv.loadUrl(pageUrl);
        } catch (Throwable t) {
            cleanup.run();
            cb.onFail("嗅探器启动失败：" + t.getMessage());
        }
    }
}