# EPG 服务已部署到云服务器 43.226.44.105

部署时间：2026-11-13 22:47~22:55（本机 Windows 经 FTP 推送）

## 通道与账号
- 入口：FTP `43.226.44.105:21`（Pure-FTPd，禁匿名，支持 AUTH TLS）
- 账号：`ballad0324` /（口令不在本文留档，见会话记录）
- 该 FTP 用户被 chroot 到站点根，登录后 `/` 即是 MacCMS 站点目录 `/www/wwwroot/MacCMS`
- 不可用通道：22(SSH 关) / 8888(宝塔面板关) / 3306(MySQL 关)；80 端口按来源返回 400；8080 是 kodbox 可道云

## 上传的文件（站点根）
| 文件 | 大小 | 作用 |
|---|---|---|
| epg.php | 3.1KB | 单频道查询接口，输出 XMLTV，带 ETag + gzip |
| epg_db.php | 0.5KB | 数据库配置 + 访问口令 `key` |
| epg.sql | 0.9KB | 建表 DDL（前缀 zytx_） |
| epg_init.php | 0.8KB | 建表脚本（需 shell） |
| epg_import.php | 4.5KB | 导入脚本（需 shell） |
| epg_boot.php | 5.3KB | 无 shell 部署入口（浏览器触发，key 保护） |
| README.txt | 2.8KB | 操作说明 |

## 数据库配置（读自站点 application/database.php，与本机一致）
host 127.0.0.1 / port 3306 / db `maccms10` / user `maccms10` / prefix `zytx_`

## 首次导入结果（HTTP 触发，实际执行在服务器）
```
db ok: maccms10 / table zytx_epg_programme
init ok
download ok: http://43.226.44.105:6061/zytx/playback.xml (2961818 B, 0.0s)
programme=16726 有效=16724 入库=16634 表内=16634 用时=2.3s
OK 频道数=254
```
关键点：服务器与 6061 同一台机器/同段网络，下载 2.9MB 几乎零耗时，整轮导入 2.3 秒。

## 接口实测
| 请求 | 结果 |
|---|---|
| `epg.php?ch=东方卫视` | 200，6,053 B，39 条，0.11s |
| `epg.php?ch=CCTV1综合` | 200，6,034 B，0.10s |
| `epg.php?ch=CCTV5体育` | 200，4,653 B，0.10s |
| `epg.php?ch=江苏卫视` | 200，4,394 B，0.12s |
| `epg.php?ch=湖南卫视` | 200，5,250 B，1.10s（首次空缓存） |
| `epg.php?ch=河南卫视` | 200，5,665 B，0.09s |
| `epg.php?ch=大湾区卫视` | 200，2,925 B，1.50s（首次空缓存） |
| `day=-1`（昨天） | 200，233 B（当天首导，历史还没攒，正常） |
| `days=3` | 200，10,727 B |

## 频道名匹配度（重要）
用新版 interface.m3u（580 个去重频道名）与源站 playback.xml（254 个 channel）比对：
- **精确命中 254 / 254，命中率 43.8%（对 m3u 全量）**
- 未命中的 326 个是源站本身没有节目单的频道（阿坝藏语、阿拉善、慢直播等地方台）
- 也就是说：只要源站给了节目单，名字就是完全一致的，不需要模糊匹配；App 里没有节目单的频道属于源站覆盖缺口

## 手机端
App 里 `MobileEpgXml.CLOUD_EPG_TEMPLATE` 已经是 `http://43.226.44.105:6066/epg.php?ch={name}`，
部署完**不需要改任何设置**，v1.5.3 / v1.5.4 打开直播页即走这个接口。
局域网兜底 `http://192.168.0.228/epg.php?ch={name}` 仍在候选链第二位，源站全量 playback.xml 是第三位。

## 待办（需要你在宝塔点两下）
定时刷新，每 6 小时打一次：
```
http://43.226.44.105:6066/epg_boot.php?key=x50k4cgqflvtiwm916j7&do=import
```
宝塔「计划任务 → 添加 → 任务类型：访问URL → 周期 6 小时」填进去即可。
没有这一步，表里只有今天这批数据；有了它，每天累积一天，7 天回看才会真正有内容。

## 回滚
FTP 删掉站点根这 7 个文件；数据库侧执行 `DROP TABLE zytx_epg_programme;` 即可，
不影响 MacCMS 原有 84 张表。

## 已知限制
1. 源站只下发当天（+次日少量），历史靠这里每天累积；刚部署时「昨天/前天」为空属正常。
2. epg_boot.php 是带 key 的运维入口，key 在 epg_db.php；不想留就删文件，改走 crontab。
3. 源站 playback.xml 不支持 ?ch= / Range / gzip，仍是整份下载——所以这层自建接口才是必要的。