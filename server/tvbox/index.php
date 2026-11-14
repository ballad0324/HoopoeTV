<?php
/**
 * HoopoeTV 云端备份接口 —— 部署在 MacCMS 同一台服务器上
 *
 * 目录结构建议：
 *   /www/wwwroot/你的站点/api/hoopoe/
 *       index.php     本文件
 *       config.php    数据库与私钥配置（自己填）
 *       install.sql   建表语句
 *       setup.php     建账号 / 改密码 / 设置内置源的小工具
 *
 * 接口一律 POST（action 用 query 传）：
 *   index.php?action=ping      无需鉴权，探活
 *   index.php?action=login     user / pass        -> token
 *   index.php?action=list      token
 *   index.php?action=push      token / name / payload(Base64 zip)
 *   index.php?action=pull      token / name(可空)
 *   index.php?action=delete    token / name
 *   index.php?action=sources   token              -> api / live / epg
 *
 * 所有返回都是 {"ok":1 ...} 或 {"ok":0,"msg":"..."}
 */

require __DIR__ . '/config.php';

// 兼容旧 config.php：只覆盖 index.php 时也能跑（这些常量缺失就用默认值）
if (!defined('T_INVITE')) define('T_INVITE', 'hoopoe_app_invite');
if (!defined('T_HISTORY')) define('T_HISTORY', 'hoopoe_app_history');
if (!defined('BUILTIN_PASS')) define('BUILTIN_PASS', 'hoopoe2026');
if (!defined('SETUP_KEY')) define('SETUP_KEY', 'change-me-please-2026');

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

function out($arr)
{
    echo json_encode($arr, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function fail($msg, $code = 0)
{
    out(array('ok' => 0, 'msg' => $msg, 'code' => $code));
}

function db()
{
    static $pdo = null;
    if ($pdo === null) {
        $dsn = 'mysql:host=' . DB_HOST . ';port=' . DB_PORT . ';dbname=' . DB_NAME . ';charset=utf8mb4';
        try {
            $pdo = new PDO($dsn, DB_USER, DB_PASS, array(
                PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            ));
        } catch (Exception $e) {
            fail('数据库连接失败，请检查 config.php');
        }
    }
    return $pdo;
}

function post($k, $default = '')
{
    return isset($_POST[$k]) ? (string)$_POST[$k] : $default;
}

function auth()
{
    $token = post('token');
    if ($token === '') fail('缺少 token', 401);
    $st = db()->prepare('SELECT u.id,u.username FROM ' . T_USER . ' u WHERE u.token = ? AND u.token_expire > ?');
    $st->execute(array($token, time()));
    $row = $st->fetch();
    if (!$row) fail('登录已失效，请重新登录', 401);
    return $row;
}

function human_size($n)
{
    $n = (int)$n;
    if ($n >= 1048576) return round($n / 1048576, 2) . 'MB';
    if ($n >= 1024) return round($n / 1024, 1) . 'KB';
    return $n . 'B';
}

$action = isset($_GET['action']) ? $_GET['action'] : post('action');

switch ($action) {

    case 'ping':
        out(array('ok' => 1, 'ver' => '1.0', 'time' => date('Y-m-d H:i:s')));

    case 'login':
        $user = trim(post('user'));
        $pass = post('pass');
        if ($user === '' || $pass === '') fail('用户名或密码为空');
        $st = db()->prepare('SELECT id,username,pass_hash FROM ' . T_USER . ' WHERE username = ? LIMIT 1');
        $st->execute(array($user));
        $row = $st->fetch();
        if (!$row || !password_verify($pass, $row['pass_hash'])) fail('用户名或密码不正确', 403);
        $token = bin2hex(random_bytes(24));
        $expire = time() + 86400 * 30;
        $up = db()->prepare('UPDATE ' . T_USER . ' SET token = ?, token_expire = ? WHERE id = ?');
        $up->execute(array($token, $expire, $row['id']));
        out(array('ok' => 1, 'token' => $token, 'user' => $row['username'], 'expire' => $expire));

    case 'list':
        $u = auth();
        $st = db()->prepare('SELECT name,size,created_at FROM ' . T_BACKUP . ' WHERE user_id = ? ORDER BY created_at DESC LIMIT 50');
        $st->execute(array($u['id']));
        $items = array();
        foreach ($st->fetchAll() as $r) {
            $items[] = array(
                'name' => $r['name'],
                'size' => (int)$r['size'],
                'size_text' => human_size($r['size']),
                'time' => $r['created_at'],
            );
        }
        out(array('ok' => 1, 'items' => $items, 'count' => count($items)));

    case 'push':
        $u = auth();
        $name = trim(post('name'));
        $payload = post('payload');
        if ($name === '' || $payload === '') fail('缺少备份名或内容');
        if (!preg_match('/^[0-9A-Za-z._\-]{1,64}$/', $name)) fail('备份名只能用数字、字母、- _ .');
        $size = strlen($payload);
        if ($size > 12 * 1024 * 1024) fail('单份备份超过 12MB 上限');
        $st = db()->prepare('SELECT id FROM ' . T_BACKUP . ' WHERE user_id = ? AND name = ? LIMIT 1');
        $st->execute(array($u['id'], $name));
        $exist = $st->fetch();
        if ($exist) {
            $up = db()->prepare('UPDATE ' . T_BACKUP . ' SET size = ?, payload = ?, created_at = ? WHERE id = ?');
            $up->execute(array($size, $payload, date('Y-m-d H:i:s'), $exist['id']));
        } else {
            $in = db()->prepare('INSERT INTO ' . T_BACKUP . ' (user_id,name,size,payload,created_at) VALUES (?,?,?,?,?)');
            $in->execute(array($u['id'], $name, $size, $payload, date('Y-m-d H:i:s')));
        }
        // 每个账号只保留最近 10 份，多的自动清掉
        $q = db()->prepare('SELECT id FROM ' . T_BACKUP . ' WHERE user_id = ? ORDER BY created_at DESC');
        $q->execute(array($u['id']));
        $ids = $q->fetchAll();
        if (count($ids) > 10) {
            $drop = array_slice($ids, 10);
            $del = db()->prepare('DELETE FROM ' . T_BACKUP . ' WHERE id = ?');
            foreach ($drop as $d) $del->execute(array($d['id']));
        }
        out(array('ok' => 1, 'name' => $name, 'size_text' => human_size($size)));

    case 'pull':
        $u = auth();
        $name = trim(post('name'));
        if ($name === '') {
            $st = db()->prepare('SELECT name,payload FROM ' . T_BACKUP . ' WHERE user_id = ? ORDER BY created_at DESC LIMIT 1');
            $st->execute(array($u['id']));
        } else {
            $st = db()->prepare('SELECT name,payload FROM ' . T_BACKUP . ' WHERE user_id = ? AND name = ? LIMIT 1');
            $st->execute(array($u['id'], $name));
        }
        $row = $st->fetch();
        if (!$row) fail('云端没有这份备份');
        out(array('ok' => 1, 'name' => $row['name'], 'payload' => $row['payload']));

    case 'delete':
        $u = auth();
        $name = trim(post('name'));
        if ($name === '') fail('缺少备份名');
        $st = db()->prepare('DELETE FROM ' . T_BACKUP . ' WHERE user_id = ? AND name = ?');
        $st->execute(array($u['id'], $name));
        out(array('ok' => 1, 'deleted' => $st->rowCount()));

    case 'sources':
        $u = auth();
        $st = db()->prepare('SELECT api_url, live_url, epg_url FROM ' . T_SOURCE . ' WHERE user_id = ? LIMIT 1');
        $st->execute(array($u['id']));
        $row = $st->fetch();
        if (!$row) out(array('ok' => 0, 'msg' => '云端还没有配置内置源'));
        out(array('ok' => 1, 'api' => $row['api_url'], 'live' => $row['live_url'], 'epg' => $row['epg_url']));

    case 'install':
        // 一键建表/升级表结构（用 config.php 里的 SETUP_KEY 当口令），不需要进 phpMyAdmin
        if (post('key') !== SETUP_KEY && (isset($_GET['key']) ? $_GET['key'] : '') !== SETUP_KEY) {
            fail('key 不正确', 403);
        }
        $sqls = array(
            "CREATE TABLE IF NOT EXISTS `" . T_USER . "` (
              `id` int unsigned NOT NULL AUTO_INCREMENT,
              `username` varchar(64) NOT NULL,
              `pass_hash` varchar(255) NOT NULL,
              `token` char(48) DEFAULT NULL,
              `token_expire` int unsigned NOT NULL DEFAULT 0,
              `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_username` (`username`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci",
            "CREATE TABLE IF NOT EXISTS `" . T_BACKUP . "` (
              `id` int unsigned NOT NULL AUTO_INCREMENT,
              `user_id` int unsigned NOT NULL,
              `name` varchar(64) NOT NULL,
              `size` int unsigned NOT NULL DEFAULT 0,
              `payload` longtext NOT NULL,
              `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_user_name` (`user_id`,`name`),
              KEY `idx_user_time` (`user_id`,`created_at`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci",
            "CREATE TABLE IF NOT EXISTS `" . T_SOURCE . "` (
              `id` int unsigned NOT NULL AUTO_INCREMENT,
              `user_id` int unsigned NOT NULL,
              `api_url` varchar(255) NOT NULL DEFAULT '',
              `live_url` varchar(255) NOT NULL DEFAULT '',
              `epg_url` varchar(255) NOT NULL DEFAULT '',
              `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_user` (`user_id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci",
            "CREATE TABLE IF NOT EXISTS `" . T_INVITE . "` (
              `id` int unsigned NOT NULL AUTO_INCREMENT,
              `code` varchar(64) NOT NULL,
              `max_uses` int unsigned NOT NULL DEFAULT 0,
              `used` int unsigned NOT NULL DEFAULT 0,
              `enabled` tinyint NOT NULL DEFAULT 1,
              `note` varchar(128) NOT NULL DEFAULT '',
              `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_code` (`code`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci",
            "CREATE TABLE IF NOT EXISTS `" . T_HISTORY . "` (
              `id` int unsigned NOT NULL AUTO_INCREMENT,
              `user_id` int unsigned NOT NULL,
              `site_key` varchar(64) NOT NULL DEFAULT '',
              `vod_id` varchar(64) NOT NULL DEFAULT '',
              `name` varchar(128) NOT NULL DEFAULT '',
              `pic` varchar(512) NOT NULL DEFAULT '',
              `flag` varchar(64) NOT NULL DEFAULT '',
              `pos` int unsigned NOT NULL DEFAULT 0,
              `dur` int unsigned NOT NULL DEFAULT 0,
              `ep` int unsigned NOT NULL DEFAULT 1,
              `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_user_vod` (`user_id`,`site_key`,`vod_id`),
              KEY `idx_user_time` (`user_id`,`updated_at`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci",
        );
        $done = array();
        foreach ($sqls as $s) {
            db()->exec($s);
            $done[] = 'ok';
        }
        // 表里还没有邀请码时，种一个总码（max_uses=0 表示不限次数）
        $st = db()->prepare('SELECT COUNT(*) AS c FROM ' . T_INVITE);
        $st->execute();
        $row = $st->fetch();
        $seeded = '';
        if ((int)$row['c'] === 0) {
            $in = db()->prepare('INSERT INTO ' . T_INVITE . ' (code,max_uses,used,enabled,note,created_at) VALUES (?,?,?,?,?,?)');
            $in->execute(array('98555576', 0, 0, 1, '固定邀请码', date('Y-m-d H:i:s')));
            $seeded = '98555576';
        }
        out(array('ok' => 1, 'tables' => count($done), 'seeded_invite' => $seeded, 'msg' => '表结构已就绪'));

    case 'register':
        $user = trim(post('user'));
        $pass = post('pass');
        $invite = trim(post('invite'));
        if ($user === '' || $pass === '' || $invite === '') fail('用户名 / 密码 / 邀请码都要填');
        if (!preg_match('/^[0-9A-Za-z_\x{4e00}-\x{9fa5}]{2,32}$/u', $user)) fail('用户名 2-32 位，只能用中英文、数字、下划线');
        if (strlen($pass) < 6) fail('密码至少 6 位');
        $st = db()->prepare('SELECT * FROM ' . T_INVITE . ' WHERE code = ? AND enabled = 1 LIMIT 1');
        $st->execute(array($invite));
        $inv = $st->fetch();
        if (!$inv) fail('邀请码无效', 403);
        if ((int)$inv['max_uses'] > 0 && (int)$inv['used'] >= (int)$inv['max_uses']) {
            fail('邀请码已用完', 403);
        }
        $st = db()->prepare('SELECT id FROM ' . T_USER . ' WHERE username = ? LIMIT 1');
        $st->execute(array($user));
        if ($st->fetch()) fail('用户名已被占用', 403);
        $hash = password_hash($pass, PASSWORD_DEFAULT);
        $token = bin2hex(random_bytes(24));
        $expire = time() + 86400 * 30;
        $in = db()->prepare('INSERT INTO ' . T_USER . ' (username,pass_hash,token,token_expire,created_at) VALUES (?,?,?,?,?)');
        $in->execute(array($user, $hash, $token, $expire, date('Y-m-d H:i:s')));
        $up = db()->prepare('UPDATE ' . T_INVITE . ' SET used = used + 1 WHERE id = ?');
        $up->execute(array($inv['id']));
        out(array('ok' => 1, 'token' => $token, 'user' => $user, 'expire' => $expire, 'msg' => '注册成功'));

    case 'save_sources':
        $u = auth();
        $api = trim(post('api'));
        $live = trim(post('live'));
        $epg = trim(post('epg'));
        $st = db()->prepare('SELECT id FROM ' . T_SOURCE . ' WHERE user_id = ? LIMIT 1');
        $st->execute(array($u['id']));
        if ($st->fetch()) {
            $up = db()->prepare('UPDATE ' . T_SOURCE . ' SET api_url = ?, live_url = ?, epg_url = ? WHERE user_id = ?');
            $up->execute(array($api, $live, $epg, $u['id']));
        } else {
            $in = db()->prepare('INSERT INTO ' . T_SOURCE . ' (user_id,api_url,live_url,epg_url) VALUES (?,?,?,?)');
            $in->execute(array($u['id'], $api, $live, $epg));
        }
        out(array('ok' => 1, 'api' => $api, 'live' => $live, 'epg' => $epg));

    case 'history_push':
        // 播放记录上云：items = JSON 数组，单条 {site,vod_id,name,pic,flag,pos,dur,ep}
        $u = auth();
        $raw = post('items');
        $items = json_decode($raw, true);
        if (!is_array($items)) fail('items 不是合法 JSON');
        $cnt = 0;
        $now = date('Y-m-d H:i:s');
        foreach ($items as $it) {
            if (!is_array($it)) continue;
            $vod = isset($it['vod_id']) ? (string)$it['vod_id'] : '';
            if ($vod === '') continue;
            $site = isset($it['site']) ? (string)$it['site'] : '';
            $name = mb_substr(isset($it['name']) ? (string)$it['name'] : '', 0, 60);
            $pic = mb_substr(isset($it['pic']) ? (string)$it['pic'] : '', 0, 500);
            $flag = mb_substr(isset($it['flag']) ? (string)$it['flag'] : '', 0, 60);
            $pos = isset($it['pos']) ? (int)$it['pos'] : 0;
            $dur = isset($it['dur']) ? (int)$it['dur'] : 0;
            $ep = isset($it['ep']) ? (int)$it['ep'] : 1;
            $st = db()->prepare('SELECT id FROM ' . T_HISTORY . ' WHERE user_id = ? AND site_key = ? AND vod_id = ? LIMIT 1');
            $st->execute(array($u['id'], $site, $vod));
            if ($st->fetch()) {
                $up = db()->prepare('UPDATE ' . T_HISTORY . ' SET name = ?, pic = ?, flag = ?, pos = ?, dur = ?, ep = ?, updated_at = ? WHERE user_id = ? AND site_key = ? AND vod_id = ?');
                $up->execute(array($name, $pic, $flag, $pos, $dur, $ep, $now, $u['id'], $site, $vod));
            } else {
                $in = db()->prepare('INSERT INTO ' . T_HISTORY . ' (user_id,site_key,vod_id,name,pic,flag,pos,dur,ep,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)');
                $in->execute(array($u['id'], $site, $vod, $name, $pic, $flag, $pos, $dur, $ep, $now));
            }
            $cnt++;
        }
        // 每个账号只保留最近 200 条
        $q = db()->prepare('SELECT id FROM ' . T_HISTORY . ' WHERE user_id = ? ORDER BY updated_at DESC');
        $q->execute(array($u['id']));
        $ids = $q->fetchAll();
        if (count($ids) > 200) {
            $del = db()->prepare('DELETE FROM ' . T_HISTORY . ' WHERE id = ?');
            foreach (array_slice($ids, 200) as $d) $del->execute(array($d['id']));
        }
        out(array('ok' => 1, 'count' => $cnt));

    case 'history_pull':
        $u = auth();
        $limit = (int)post('limit', '100');
        if ($limit <= 0 || $limit > 200) $limit = 100;
        $st = db()->prepare('SELECT site_key,vod_id,name,pic,flag,pos,dur,ep,updated_at FROM ' . T_HISTORY . ' WHERE user_id = ? ORDER BY updated_at DESC LIMIT ' . $limit);
        $st->execute(array($u['id']));
        $items = array();
        foreach ($st->fetchAll() as $r) {
            $items[] = array(
                'site' => $r['site_key'],
                'vod_id' => $r['vod_id'],
                'name' => $r['name'],
                'pic' => $r['pic'],
                'flag' => $r['flag'],
                'pos' => (int)$r['pos'],
                'dur' => (int)$r['dur'],
                'ep' => (int)$r['ep'],
                'time' => $r['updated_at'],
            );
        }
        out(array('ok' => 1, 'items' => $items, 'count' => count($items)));

    default:
        fail('未知 action');
}

