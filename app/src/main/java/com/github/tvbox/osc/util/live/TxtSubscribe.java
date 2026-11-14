package com.github.tvbox.osc.util.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.StringReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TxtSubscribe {

    private static final Pattern NAME_PATTERN = Pattern.compile(".*,(.+?)$");
    private static final Pattern GROUP_PATTERN = Pattern.compile("group-title=\"(.*?)\"");
    private static final Pattern CATCHUP_SRC_PATTERN = Pattern.compile("catchup-source=\"(.*?)\"");
    private static final Pattern CATCHUP_FLAG_PATTERN = Pattern.compile("catchup=\"(.*?)\"");

    /** m3u 回看模板：分组名 + "|" + 频道名 -> catchup-source（空串表示该频道不支持回看）
     *  键里必须带分组：同一个频道名在不同分组里回看能力可能不同（如「央视」组有、「央视频」组标 catchup="none"） */
    private static final LinkedHashMap<String, String> CATCHUP_MAP = new LinkedHashMap<>();

    private static String catchupKey(String group, String name) {
        return group + "\u0001" + name;
    }

    public static void parse(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        CATCHUP_MAP.clear();
        if (str.startsWith("#EXTM3U")) {
            parseM3u(linkedHashMap, str);
        } else {
            parseTxt(linkedHashMap, str);
        }
    }

    private static void parseM3u(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        ArrayList<String> urls;
        try {
            BufferedReader bufferedReader = new BufferedReader(new StringReader(str));
            LinkedHashMap<String, ArrayList<String>> channel = new LinkedHashMap<>();
            LinkedHashMap<String, ArrayList<String>> channelTemp = channel;
            String globalCatchup = "";
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.equals("")) continue;
                if (line.startsWith("#EXTM3U")) {
                    // 全局回看声明：#EXTM3U ... catchup="append" catchup-source="?playbackbegin=${(b)yyyyMMddHHmmss}&..."
                    String globalSrc = getAttr(CATCHUP_SRC_PATTERN, line);
                    if (globalSrc != null) globalCatchup = globalSrc;
                    continue;
                }
                if (line.startsWith("#EXTINF")) {
                    String name = getStrByRegex(NAME_PATTERN, line);
                    String group = getStrByRegex(GROUP_PATTERN, line);
                    // 单频道可以覆盖全局：catchup="none" 表示该频道无回看，自带 catchup-source 以自带为准
                    String ownFlag = getAttr(CATCHUP_FLAG_PATTERN, line);
                    String ownSrc = getAttr(CATCHUP_SRC_PATTERN, line);
                    String catchup;
                    if (ownSrc != null) {
                        catchup = ownSrc;
                    } else if (ownFlag != null) {
                        catchup = "none".equalsIgnoreCase(ownFlag) ? "" : globalCatchup;
                    } else {
                        catchup = globalCatchup;
                    }
                    CATCHUP_MAP.put(catchupKey(group, name), catchup == null ? "" : catchup);
                    // 此时再读取一行，就是对应的 url 链接了
                    String url = bufferedReader.readLine().trim();
                    if (linkedHashMap.containsKey(group)) {
                        channelTemp = linkedHashMap.get(group);
                    } else {
                        channelTemp = new LinkedHashMap<>();
                        linkedHashMap.put(group, channelTemp);
                    }
                    if (null != channelTemp && channelTemp.containsKey(name)) {
                        urls = channelTemp.get(name);
                    } else {
                        urls = new ArrayList<>();
                        channelTemp.put(name, urls);
                    }
                    if (null != urls && !urls.contains(url)) urls.add(url);
                }
            }
            bufferedReader.close();
            if (channel.isEmpty()) return;
            linkedHashMap.put("未分组", channel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 取属性值，取不到返回 null（与 getStrByRegex 的兜底语义区分开，回看属性需要「有没有」的判断） */
    private static String getAttr(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) return matcher.group(1);
        return null;
    }

    private static String getStrByRegex(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) return matcher.group(1);
        return pattern.pattern().equals(GROUP_PATTERN.pattern()) ? "未分组" : "未命名";
    }

    private static void parseTxt(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        ArrayList<String> arrayList;
        try {
            BufferedReader bufferedReader = new BufferedReader(new StringReader(str));
            String readLine = bufferedReader.readLine();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap2 = new LinkedHashMap<>();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap3 = linkedHashMap2;
            while (readLine != null) {
                if (readLine.trim().isEmpty()) {
                    readLine = bufferedReader.readLine();
                } else {
                    String[] split = readLine.split(",", 2);
                    if (split.length < 2) {
                        readLine = bufferedReader.readLine();
                    } else {
                        if (readLine.contains("#genre#")) {
                            String trim = split[0].trim();
                            if (!linkedHashMap.containsKey(trim)) {
                                linkedHashMap3 = new LinkedHashMap<>();
                                linkedHashMap.put(trim, linkedHashMap3);
                            } else {
                                linkedHashMap3 = linkedHashMap.get(trim);
                            }
                        } else {
                            String trim2 = split[0].trim();
                            for (String str2 : split[1].trim().split("#")) {
                                String trim3 = str2.trim();
                                if (!trim3.isEmpty() && (trim3.startsWith("http") || trim3.startsWith("rtsp") || trim3.startsWith("rtmp"))) {
                                    if (!linkedHashMap3.containsKey(trim2)) {
                                        arrayList = new ArrayList<>();
                                        linkedHashMap3.put(trim2, arrayList);
                                    } else {
                                        arrayList = linkedHashMap3.get(trim2);
                                    }
                                    if (!arrayList.contains(trim3)) {
                                        arrayList.add(trim3);
                                    }
                                }
                            }
                        }
                        readLine = bufferedReader.readLine();
                    }
                }
            }
            bufferedReader.close();
            if (linkedHashMap2.isEmpty()) {
                return;
            }
            linkedHashMap.put("未分组", linkedHashMap2);
        } catch (Throwable unused) {
        }
    }

    public static JsonArray live2JsonArray(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap) {
        JsonArray jsonarr = new JsonArray();
        for (String str : linkedHashMap.keySet()) {
            JsonArray jsonarr2 = new JsonArray();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap2 = linkedHashMap.get(str);
            if (!linkedHashMap2.isEmpty()) {
                for (String str2 : linkedHashMap2.keySet()) {
                    ArrayList<String> arrayList = linkedHashMap2.get(str2);
                    if (!arrayList.isEmpty()) {
                        JsonArray jsonarr3 = new JsonArray();
                        for (int i = 0; i < arrayList.size(); i++) {
                            jsonarr3.add(arrayList.get(i));
                        }
                        JsonObject jsonobj = new JsonObject();
                        try {
                            jsonobj.addProperty("name", str2);
                            jsonobj.add("urls", jsonarr3);
                            jsonobj.addProperty("catchup",
                                    CATCHUP_MAP.containsKey(catchupKey(str, str2))
                                            ? CATCHUP_MAP.get(catchupKey(str, str2)) : "");
                        } catch (Throwable e) {
                        }
                        jsonarr2.add(jsonobj);
                    }
                }
                JsonObject jsonobj2 = new JsonObject();
                try {
                    jsonobj2.addProperty("group", str);
                    jsonobj2.add("channels", jsonarr2);
                } catch (Throwable e) {
                }
                jsonarr.add(jsonobj2);
            }
        }
        return jsonarr;
    }
}
