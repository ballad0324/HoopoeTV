package com.github.tvbox.osc.mobile.live;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 直播回看地址构造。
 *
 * 内置直播源的 m3u 头声明：
 *   catchup="append"
 *   catchup-source="?playbackbegin=${(b)yyyyMMddHHmmss}&playbackend=${(e)yyyyMMddHHmmss}"
 * 即把原频道地址后面拼上起止时间参数（时间格式由模板里的 yyyyMMddHHmmss 决定）。
 * 这里兼容三种写法：${(b)格式} / ${(e)格式}（自定义时间格式）、${b} / ${e}（epoch 秒）、{start} / {end}。
 */
public class LiveCatchup {

    // 模板替换全部用字符串扫描，不走正则：正则里未转义的 { } 在部分 ROM 上会抛 PatternSyntaxException

    /**
     * @param channelUrl 频道直播地址
     * @param template   catchup-source 模板
     * @param start      节目开始时间
     * @param end        节目结束时间
     * @return 可直接播放的回看地址；模板为空时返回 null
     */
    public static String build(String channelUrl, String template, Date start, Date end) {
        if (channelUrl == null || channelUrl.isEmpty()) return null;
        if (template == null || template.isEmpty()) return null;

        String result = template;

        result = replaceWithFormat(result, start, end);

        result = replaceEpoch(result, start, end);

        result = result.replace("{start}", String.valueOf(start.getTime() / 1000L))
                .replace("{end}", String.valueOf(end.getTime() / 1000L))
                .replace("${start}", String.valueOf(start.getTime() / 1000L))
                .replace("${end}", String.valueOf(end.getTime() / 1000L));

        if (result.startsWith("?") || result.startsWith("&")) {
            return channelUrl + result;
        }
        if (result.contains("{url}")) {
            return result.replace("{url}", channelUrl);
        }
        return result;
    }

    /** 处理 ${(b)格式} / ${(e)格式}：括号里是 b 或 e，紧跟到 } 之间是 SimpleDateFormat 格式串 */
    private static String replaceWithFormat(String template, Date start, Date end) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            int idx = template.indexOf("${(", i);
            if (idx < 0) {
                out.append(template.substring(i));
                break;
            }
            out.append(template, i, idx);
            int flagEnd = template.indexOf(")", idx + 3);
            int fmtEnd = flagEnd < 0 ? -1 : template.indexOf("}", flagEnd + 1);
            if (flagEnd < 0 || fmtEnd < 0) {
                // 结构不完整，原样保留，避免吞字符
                out.append(template.substring(idx));
                break;
            }
            String flag = template.substring(idx + 3, flagEnd).trim();
            String fmt = template.substring(flagEnd + 1, fmtEnd);
            Date date = "b".equalsIgnoreCase(flag) ? start : end;
            String value;
            try {
                value = new SimpleDateFormat(fmt, Locale.US).format(date);
            } catch (Throwable th) {
                value = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(date);
            }
            out.append(value);
            i = fmtEnd + 1;
        }
        return out.toString();
    }

    private static String replaceEpoch(String template, Date start, Date end) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            int idx = template.indexOf("${", i);
            if (idx < 0) {
                out.append(template.substring(i));
                break;
            }
            out.append(template, i, idx);
            if (template.startsWith("${b}", idx)) {
                out.append(start.getTime() / 1000L);
                i = idx + 4;
            } else if (template.startsWith("${e}", idx)) {
                out.append(end.getTime() / 1000L);
                i = idx + 4;
            } else {
                out.append("${");
                i = idx + 2;
            }
        }
        return out.toString();
    }
}
