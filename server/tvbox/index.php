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

    default:
        fail('未知 action');
}
