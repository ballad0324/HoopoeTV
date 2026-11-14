# HoopoeTV 云端备份（MacCMS / MySQL）部署说明

App 端入口：设置 → 通用 · 存储与关于 → 「云端备份（MySQL）」「从云端拉内置源」。

## 1. 目录放哪

把本目录整个上传到 MacCMS 站点根目录下：

```
/www/wwwroot/你的站点/api/hoopoe/
    index.php
    config.php
    install.sql
    setup.php
```

App 里填的「接口地址」就是 `https://你的域名/api/hoopoe/`（末尾带不带 `/` 都可以，
不要填成 `.../index.php` 之外的其它层级）。

## 2. 改 config.php

只改前 6 行：

```php
define('DB_HOST', '127.0.0.1');
define('DB_PORT', '3306');
define('DB_NAME', 'maccms');        // 你 MacCMS 的库名
define('DB_USER', 'maccms');        // 库账号
define('DB_PASS', '你的库密码');
define('SETUP_KEY', '换成你自己的随机串');
```

表名前缀默认 `hoopoe_app_`，不会和 MacCMS 的 `mac_` 表冲突。

## 3. 建表

宝塔 → 数据库 → 你的库 → phpMyAdmin → SQL 页，粘贴 `install.sql` 执行；
或在服务器上：

```bash
mysql -u 账号 -p 库名 < install.sql
```

## 4. 建 App 账号

浏览器打开：

```
https://你的域名/api/hoopoe/setup.php?key=你在config里填的SETUP_KEY
```

填用户名、密码（可选填内置源三件套）→ 保存。
**装完把 setup.php 删掉**，或至少把 SETUP_KEY 换掉。

## 5. 自检

```bash
curl -s "https://你的域名/api/hoopoe/index.php?action=ping"
# {"ok":1,"ver":"1.0","time":"..."}
```

返回 ok:1 就说明 PHP 与数据库都通了。

## 6. 接口一览

| action | 参数 | 作用 |
|---|---|---|
| ping | — | 探活 |
| login | user, pass | 换 token（30 天） |
| list | token | 云端备份列表 |
| push | token, name, payload(Base64) | 上传一份备份 |
| pull | token, name 可空 | 下载（空=最新一份） |
| delete | token, name | 删除一份 |
| sources | token | 下发内置源 api/live/epg |

返回统一格式：`{"ok":1,...}` 或 `{"ok":0,"msg":"原因"}`。

## 7. 几条边界

- 单份备份上限 12MB（App 端先卡 8MB），每个账号只留最近 10 份，多的自动清。
- 备份内容是 App 的 `sqlite`（收藏/历史/搜索）+ `hawk`（全部设置），zip 后 Base64 存进 `payload` LONGTEXT。
- 走 HTTPS 更稳：宝塔里给站点开 SSL，App 里地址写 `https://`。
- PHP 版本 7.4 及以上；用到的扩展：`pdo_mysql`（宝塔默认装好）。
- 如果宝塔开了「防跨站攻击 / open_basedir」，确认 `api/hoopoe` 在站点目录内可执行 PHP。
- 反向代理注意：nginx 客户端最大请求体 `client_max_body_size` 要 ≥ 16m，否则大备份会被 413 截断。
