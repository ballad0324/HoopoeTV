package com.github.tvbox.osc.mobile.discover;

/** 「发现」页的条目模型：只保留页面要显示的字段 */
public class DiscoverItem {
    public String id = "";
    public String name = "";
    public String pic = "";
    public String year = "";
    public String area = "";
    public String cls = "";
    public String remarks = "";
    public String actor = "";
    public String director = "";
    public String pubdate = "";
    public String blurb = "";
    public String content = "";
    public double score = 0;
    public long hits = 0;
    public long hitsDay = 0;
    public long hitsWeek = 0;
    public long hitsMonth = 0;

    /** 卡片副标题：年份 · 地区 */
    public String meta() {
        StringBuilder sb = new StringBuilder();
        if (year != null && !year.trim().isEmpty()) sb.append(year.trim());
        if (area != null && !area.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(area.trim());
        }
        return sb.toString();
    }

    public long hitsOf(int period) {
        switch (period) {
            case 0:
                return hitsDay;
            case 1:
                return hitsWeek;
            case 2:
                return hitsMonth;
            default:
                return hits;
        }
    }
}
