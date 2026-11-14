<?php
/**
 * 数据库与表名配置：把下面 5 个常量改成你 MacCMS 那套 MySQL 的实际值。
 * 建议直接用 MacCMS 的同一个库、同一个账号，省得再建库授权。
 */

define('DB_HOST', '127.0.0.1');
define('DB_PORT', '3306');
define('DB_NAME', 'maccms');
define('DB_USER', 'maccms');
define('DB_PASS', '这里改成你的数据库密码');

// 表名：默认加 hoopoe_ 前缀，避免和 MacCMS 的 mac_ 表混在一起
define('T_USER',   'hoopoe_app_user');
define('T_BACKUP', 'hoopoe_app_backup');
define('T_SOURCE', 'hoopoe_app_source');
// v1.7：注册邀请码 + 播放记录
define('T_INVITE',  'hoopoe_app_invite');
define('T_HISTORY', 'hoopoe_app_history');

// 出厂内置源的解锁口令：App 里输入这串才会启用「内置源」
define('BUILTIN_PASS', 'hoopoe2026');

// setup.php 的安装口令：随便改成一段你自己的随机字符串，
// 打开 http://域名/api/hoopoe/setup.php?key=这段口令 才能建账号。
define('SETUP_KEY', 'change-me-please-2026');
