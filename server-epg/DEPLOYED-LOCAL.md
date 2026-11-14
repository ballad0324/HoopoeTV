# 本机 MacCMS 已改动清单（2026-11-13 21:43）

## 环境探测结果
- 本机是 **宝塔 Windows 面板**：nginx（80/888）、MySQL 5.7（3306）、btPanel（8888）、PHP 7.4（`D:\BtSoft\php\74\php.exe`）
- 站点根目录：`D:\wwwroot\MacCMS`，nginx vhost `127.0.0.1.conf` → `listen 80; server_name 127.0.0.1; root D:/wwwroot/MacCMS`
- 数据库：`maccms10` / 用户 `maccms10` / **表前缀 `zytx_`**（不是 MacCMS 默认的 `mac_`），现有 84 张表
- 站内已有自定义入口：`zytx_tvbox.php`、`zytx_sky.php`、`hoopoe.php`，其中站点地址写的是 `http://43.226.44.105:6066`
- 注意：`43.226.44.105:6066` **不是这台机器**（本机公网 IP 是 223.88.26.124，nginx 只监听 80/888 且只认 127.0.0.1）。所以本机是开发/镜像环境，线上那台要单独部署。

## 本次新增（全部是新增文件/新增表，未改动任何既有文件）
| 位置 | 说明 |
|---|---|
| `D:\wwwroot\MacCMS\epg.sql` | 建表脚本，表名 `zytx_epg_programme` |
| `D:\wwwroot\MacCMS\epg_import.php` | 导入脚本（XMLTV → MySQL） |
| `D:\wwwroot\MacCMS\epg.php` | 对外 XMLTV 接口（支持 `ch` / `day` / `days`，gzip + ETag） |
| 数据库 `maccms10.zytx_epg_programme` | 已建表并导入 |

## 已执行的验证
```
# 建表
Get-Content D:\wwwroot\MacCMS\epg.sql -Raw | mysql --host=127.0.0.1 --user=maccms10 --database=maccms10

# 导入（用本地已下好的 playback.xml，省掉 2 分钟下载）
php.exe epg_import.php --src=D:\Projects\Box\shots\playback.xml --days=3
→ [2026-11-13 21:43:33] programme=14906 有效=14902 入库=14811 表内=14811

# 数据规模
→ 249 个频道 / 14,811 行（原始 2,644,944 B 的 playback.xml）
```

接口实测（本机 nginx，`127.0.0.1:80`）：

| 请求 | 体积 | 耗时 |
|---|---|---|
| `/epg.php?ch=东方卫视` | 6,053 B | 0.011 s |
| `/epg.php`（全频道当天） | 1,504,753 B | 0.35 s |
| `/epg.php` + gzip | 137,743 B | 0.28 s |
| 源站 `playback.xml` 直连 | 2,644,944 B | ≈19 KB/s，全量 2 分钟级 |

单频道接口返回样例（东方卫视）：
```xml
<programme start="20261113003700 +0800" stop="20261113011900 +0800" channel="东方卫视">
  <title lang="zh">三大队(22)</title>
</programme>
```

## 要在线上服务器（43.226.44.105:6066）生效，还差这几步
1. 把 `epg.php`、`epg_import.php` 上传到那台机器的站点根目录；
2. 在那台机器执行 `epg.sql`（表前缀同样是 `zytx_`，库名 maccms10）；
3. 导一次数据 + 加每日计划任务：
   ```
   php epg_import.php --src=http://43.226.44.105:6061/zytx/playback.xml --days=3
   ```
   （宝塔「计划任务」里加 shell 脚本，每天 06:10）
4. App「设置 → EPG 地址」填：
   - `http://43.226.44.105:6066/epg.php?ch={name}`（需要 App 支持 `{name}` 占位符 —— 目前还没做，我改完出 v1.5.3）
   - 或 `http://43.226.44.105:6066/epg.php`（全量当天，137KB gzip，比 2.6MB 源站快十几倍）

## 回滚方式（如需）
```
DROP TABLE zytx_epg_programme;
删除 epg.php / epg_import.php / epg.sql 三个文件
```
不影响 MacCMS 任何原有表与文件。
