package com.github.tvbox.osc.mobile.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 手机端投屏到安卓电视（DLNA/UPnP MediaRenderer）。
 *
 * 流程：SSDP M-SEARCH 找附近的 MediaRenderer → 拉设备描述 XML 取 AVTransport 的 controlURL →
 * SOAP SetAVTransportURI + Play 把当前播放地址直接推给电视播放。
 * 只依赖 HttpURLConnection / DatagramSocket，不引入额外库。
 */
public final class MobileCastHelper {

    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String ST_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1";

    private MobileCastHelper() {
    }

    public static class CastDevice {
        public String name;
        public String controlUrl;

        @Override
        public String toString() {
            return name == null ? controlUrl : name;
        }
    }

    public static void cast(final Activity activity, final String title, final String url) {
        if (activity == null) return;
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(activity, "还没有拿到播放地址，先点一集开始播放再投屏", Toast.LENGTH_SHORT).show();
            return;
        }
        final AlertDialog searching = new AlertDialog.Builder(activity)
                .setTitle("正在搜索附近的电视")
                .setMessage("请确认手机和电视在同一个 Wi-Fi 下…")
                .setNegativeButton("取消", null)
                .create();
        searching.show();

        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<CastDevice> devices;
                try {
                    devices = discover();
                } catch (Throwable th) {
                    devices = new ArrayList<>();
                }
                final List<CastDevice> found = devices;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        searching.dismiss();
                        if (found.isEmpty()) {
                            Toast.makeText(activity, "没有找到可投屏的设备，请检查电视是否开启投屏/同网 Wi-Fi", Toast.LENGTH_LONG).show();
                            return;
                        }
                        CharSequence[] names = new CharSequence[found.size()];
                        for (int i = 0; i < found.size(); i++) names[i] = found.get(i).toString();
                        new AlertDialog.Builder(activity)
                                .setTitle("选择投屏设备")
                                .setItems(names, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        CastDevice device = found.get(which);
                                        doCast(activity, device, title, url);
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    private static void doCast(final Activity activity, final CastDevice device,
                               final String title, final String url) {
        Toast.makeText(activity, "正在投到「" + device.name + "」…", Toast.LENGTH_SHORT).show();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    // 1) 设置播放地址；2) 开始播放
                    soap(device.controlUrl, "SetAVTransportURI",
                            "<InstanceID>0</InstanceID><CurrentURI>" + escapeXml(url) + "</CurrentURI>"
                                    + "<CurrentURIMetaData></CurrentURIMetaData>");
                    soap(device.controlUrl, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>");
                    ok = true;
                } catch (Throwable ignored) {
                }
                final boolean result = ok;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result) {
                            Toast.makeText(activity, "已推送到「" + device.name + "」", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(activity, "投屏失败，设备可能不支持拉取该地址", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** SSDP 搜索 + 解析每个设备描述 */
    private static List<CastDevice> discover() throws Exception {
        List<CastDevice> list = new ArrayList<>();
        List<String> locations = new ArrayList<>();
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(1200);
        String msg = "M-SEARCH * HTTP/1.1\r\n"
                + "HOST: " + SSDP_ADDR + ":" + SSDP_PORT + "\r\n"
                + "MAN: \"ssdp:discover\"\r\n"
                + "MX: 3\r\n"
                + "ST: " + ST_RENDERER + "\r\n\r\n";
        byte[] data = msg.getBytes("UTF-8");
        InetAddress addr = InetAddress.getByName(SSDP_ADDR);
        DatagramPacket packet = new DatagramPacket(data, data.length, addr, SSDP_PORT);
        socket.send(packet);
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            try {
                byte[] buf = new byte[2048];
                DatagramPacket resp = new DatagramPacket(buf, buf.length);
                socket.receive(resp);
                String text = new String(buf, 0, resp.getLength(), "UTF-8");
                String loc = header(text, "LOCATION");
                if (!TextUtils.isEmpty(loc) && !locations.contains(loc)) locations.add(loc.trim());
            } catch (SocketTimeoutException ignore) {
                if (locations.size() > 0) break;
            } catch (Throwable ignore) {
                break;
            }
        }
        socket.close();

        for (String loc : locations) {
            try {
                String xml = httpGet(loc);
                String name = tag(xml, "friendlyName");
                String control = tag(xml, "controlURL");
                if (TextUtils.isEmpty(control)) continue;
                if (control.startsWith("/") && !loc.contains(control)) {
                    // controlURL 为相对路径，拼到设备描述的 host 上
                    String host = loc.substring(0, loc.indexOf("/", 8) > 0 ? loc.indexOf("/", 8) : loc.length());
                    control = host + control;
                }
                CastDevice d = new CastDevice();
                d.name = TextUtils.isEmpty(name) ? loc : name;
                d.controlUrl = control;
                list.add(d);
            } catch (Throwable ignored) {
            }
        }
        return list;
    }

    private static String header(String text, String key) {
        for (String line : text.split("\r\n")) {
            int idx = line.indexOf(':');
            if (idx > 0 && line.substring(0, idx).trim().equalsIgnoreCase(key)) {
                return line.substring(idx + 1).trim();
            }
        }
        return null;
    }

    private static String tag(String xml, String name) {
        if (xml == null) return null;
        String open = "<" + name + ">";
        String close = "</" + name + ">";
        int s = xml.indexOf(open);
        if (s < 0) return null;
        int e = xml.indexOf(close, s);
        if (e < 0) return null;
        return xml.substring(s + open.length(), e).trim();
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(5000);
        InputStream is = conn.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        conn.disconnect();
        return bos.toString("UTF-8");
    }

    private static void soap(String controlUrl, String action, String inner) throws Exception {
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" "
                + "xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>"
                + "<u:" + action + " xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">"
                + inner
                + "</u:" + action + "></s:Body></s:Envelope>";
        HttpURLConnection conn = (HttpURLConnection) new URL(controlUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
        conn.setRequestProperty("SOAPACTION",
                String.format(Locale.US,
                        "\"urn:schemas-upnp-org:service:AVTransport:1#%s\"", action));
        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.flush();
        os.close();
        int code = conn.getResponseCode();
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("SOAP " + code);
        }
    }
}
