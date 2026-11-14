# HoopoeTV 云端备份（MacCMS / MySQL）部署说明

**放哪里：MacCMS 站点根目录下新建一个 `tvbox` 文件夹，把这 4 个文件丢进去。**

```
/www/wwwroot/你的站点/tvbox/
    index.php     接口
    config.php    数据库与口令配置
    install.sql   建表语句
    setup.php     建账号 / 设置内置源（装完删掉）
```

不动 MacCMS 自身的任何目录和文件，只在自己的库里加 3 张 `hoopoe_app_*` 表。

App 里「接口地址」填：

```
https://你的域名/tvbox/
```

## 1. 改 config.php

```php
define('DB_HOST', '127.0.0.1');
define('DB_PORT', '3306');
define('DB_NAME', 'maccms10');      // 你 MacCMS 的库名
define('DB_USER', 'maccms10');      // 库账号
define('DB_PASS', '你的库密码');
define('SETUP_KEY', '换成你自己的随机串');
```

> 库名 / 账号 / 密码不要凭记忆填：直接打开 MacCMS 自己的 `application/database.php`，把 `database`、`username`、`password` 三项原样抄过来。填错账号会报 `SQLSTATE[HY000] [1045] Access denied`，填错库名会报 `Unknown database`。
> 密码里如果带 `@`、`#` 等符号不影响 PHP，但放进 URL 时要转义（`@` → `%40`）。

## 2. 建表

宝塔 → 数据库 → phpMyAdmin → SQL 里执行 `install.sql`；或命令行：

```bash
mysql -u 账号 -p 库名 < install.sql
```

## 3. 建 App 账号

浏览器打开：

```
https://你的域名/tvbox/setup.php?key=你在config里填的SETUP_KEY
```

填用户名、密码（内置源三件套可顺手填）→ 保存。**装完把这个文件删掉**，或至少换掉 SETUP_KEY。

## 4. 自检

```bash
curl -s "https://你的域名/tvbox/index.php?action=ping"
# {"ok":1,"ver":"1.0","time":"..."}
```

ok:1 就说明 PHP 与 MySQL 都通了。

## 5. 宝塔里要确认的三处

1. 站点「防跨站攻击(open_basedir)」保持开启没问题，`tvbox` 在站点目录内即可；
2. nginx 的 `client_max_body_size` 改到 `16m` 以上，否则大备份上传会被 413 截断；
3. 开了 HTTPS 就在 App 里填 `https://`，别混着写 http。

## 6. 接口一览

| action | 参数 | 作用 |
|---|---|---|
| ping | — | 探活 |
| login | user, pass | 换 token（30 天有效） |
| list | token | 云端备份列表 |
| push | token, name, payload(Base64) | 上传一份备份 |
| pull | token, name 可为空 | 下载（空 = 最新一份） |
| delete | token, name | 删除一份 |
| sources | token | 下发内置源 api / live / epg |

返回统一格式：`{"ok":1,...}` 或 `{"ok":0,"msg":"原因"}`。

## 7. 存储与边界

- 备份 = App 的 `sqlite`（收藏/历史/搜索）+ `hawk`（全部设置），zip 后 Base64 存进 `payload` LONGTEXT。
- 单份上限 12MB（App 端先卡 8MB），每个账号只保留最近 10 份，超出自动删最旧的。
- 只存配置类数据，不涉及用户隐私内容。
