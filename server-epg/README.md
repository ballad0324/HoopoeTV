# MacCMS + MySQL 直播节目单（EPG）部署说明

目标：把 `playback.xml`（2.6MB、源站约 19KB/s、无缓存头）落到你自己的 MacCMS MySQL，
再由 `epg.php` 按频道即时输出 XMLTV，App 端从「分钟级」变成「秒级」。

## 文件
| 文件 | 作用 |
|---|---|
| `epg.sql` | 建表 `mac_epg_programme`（唯一索引 `channel+start_at+title` 天然去重） |
| `epg_import.php` | 定时把 XMLTV（URL 或本地文件）导入 MySQL，只保留最近 N 天 |
| `epg.php` | 对外 EPG 接口，支持 `ch` / `day` / `days`，带 gzip + ETag |

## 部署步骤
1. 建表（前缀按你的 MacCMS 改，默认 `mac_`）：
   ```bash
   mysql -uroot -p maccms < epg.sql
   ```
2. 改 `epg_import.php` 顶部/命令行里的库名、账号（默认 127.0.0.1 / maccms / root）。
3. 先手动导一次，确认条数对得上（源站约 14,906 条 programme）：
   ```bash
   php epg_import.php --src=http://43.226.44.105:6061/zytx/playback.xml --days=3 \
     --host=127.0.0.1 --db=maccms --user=root --pass=你的密码
   ```
   输出形如：`programme=14906 解析=14906 入库=14906 表内=14906`
4. 加 cron（源站每天只给当天，所以**每天必须重导一次**）：
   ```bash
   # 每天 06:10 导一次，保留 3 天
   10 6 * * * cd /www/wwwroot/你的站 && php epg_import.php --src=http://43.226.44.105:6061/zytx/playback.xml --days=3 >> /tmp/epg_import.log 2>&1
   ```
5. `epg.php` 放到站点目录，浏览器打开验证：
   - `http://你的域名/epg.php?ch=东方卫视` → 只回这个频道当天（约 5~20KB）
   - `http://你的域名/epg.php` → 全频道当天（大文件，别在 App 里填这个）
6. App 里「设置 → EPG 地址」填其一：
   - 推荐：`http://你的域名/epg.php?ch={name}`（需要 App 支持 `{name}` 占位符，目前**还没做**，见下）
   - 可用：`http://你的域名/epg.php`（全量当天，但走你自己的服务器 + gzip + ETag，也比源站快得多）

## 关键点 / 坑
- **频道名必须一致**：`channel` 字段来自 XMLTV 的 `channel` 属性（如「东方卫视」），App 用的也是 m3u 里的频道名。名字不一致就查不到节目，导入后可这样自查：
  ```sql
  SELECT channel, COUNT(*) FROM mac_epg_programme GROUP BY channel ORDER BY 2 DESC LIMIT 20;
  ```
- `start_at` 存的是**东八区本地时间**（`2026-11-13 06:00:00`），输出时补 ` +0800`。
- 源站只下发**当天**节目，所以「昨天/前天」回看面板拿不到节目名——自建也补不回来，除非换多天 XMLTV 源。
- 表结构独立，不动 MacCMS 的 `mac_vod` / `mac_type` 等表，升级模板不受影响。
- 如果站点有 CDN，`epg.php` 的 `Cache-Control: max-age=300` 会让 CDN 缓存 5 分钟，导库后最多 5 分钟生效。

## 还需要 App 端配合的一处（我可以改）
现在 `epg.php?ch={name}` 这种模板 App 不认——`MobileEpgXml.load()` 是把设置里的地址原样请求，
没有把 `{name}` / `{date}` 替换成当前频道。改法很小：
- `load()` 里对 URL 做 `replace("{name}", URLEncoder.encode(channelName))`、`replace("{date}", yyyyMMdd)`；
- 磁盘缓存按 URL 存，所以每个频道各自一份小缓存（本来就该这样）。

改完之后：单频道 EPG 只有 5~20KB，冷启动基本不用等，也不用再缓存 2.6MB 的大文件。
需要我直接落这版改动就说一声，我出 v1.5.3。
