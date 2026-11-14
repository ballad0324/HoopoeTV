<?php
/**
 * 一次性小工具：建账号 / 改密码 / 设置内置源。
 * 用法：http://你的域名/api/hoopoe/setup.php?key=config.php 里的 SETUP_KEY
 *
 * 注意：用完请把本文件删掉，或把 SETUP_KEY 改掉，防止别人猜地址改你的数据。
 */

require __DIR__ . '/config.php';

if (!isset($_GET['key']) || $_GET['key'] !== SETUP_KEY) {
    header('Content-Type: text/plain; charset=utf-8');
    echo "denied：key 不正确\n";
    exit;
}

header('Content-Type: text/html; charset=utf-8');

$dsn = 'mysql:host=' . DB_HOST . ';port=' . DB_PORT . ';dbname=' . DB_NAME . ';charset=utf8mb4';
try {
    $pdo = new PDO($dsn, DB_USER, DB_PASS, array(
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ));
} catch (Exception $e) {
    echo "数据库连不上：" . htmlspecialchars($e->getMessage());
    exit;
}

$msg = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $user = trim(isset($_POST['user']) ? $_POST['user'] : '');
    $pass = isset($_POST['pass']) ? $_POST['pass'] : '';
    $api  = trim(isset($_POST['api']) ? $_POST['api'] : '');
    $live = trim(isset($_POST['live']) ? $_POST['live'] : '');
    $epg  = trim(isset($_POST['epg']) ? $_POST['epg'] : '');

    if ($user === '' || $pass === '') {
        $msg = '用户名和密码都要填';
    } else {
        $hash = password_hash($pass, PASSWORD_DEFAULT);
        $st = $pdo->prepare('SELECT id FROM ' . T_USER . ' WHERE username = ? LIMIT 1');
        $st->execute(array($user));
        $row = $st->fetch();
        if ($row) {
            $up = $pdo->prepare('UPDATE ' . T_USER . ' SET pass_hash = ?, token = NULL, token_expire = 0 WHERE id = ?');
            $up->execute(array($hash, $row['id']));
            $uid = $row['id'];
            $msg = '账号已更新密码（旧 token 已失效）';
        } else {
            $in = $pdo->prepare('INSERT INTO ' . T_USER . ' (username, pass_hash) VALUES (?,?)');
            $in->execute(array($user, $hash));
            $uid = $pdo->lastInsertId();
            $msg = '账号已创建';
        }
        if ($api !== '') {
            $st = $pdo->prepare('SELECT id FROM ' . T_SOURCE . ' WHERE user_id = ? LIMIT 1');
            $st->execute(array($uid));
            $srow = $st->fetch();
            if ($srow) {
                $up = $pdo->prepare('UPDATE ' . T_SOURCE . ' SET api_url = ?, live_url = ?, epg_url = ? WHERE user_id = ?');
                $up->execute(array($api, $live, $epg, $uid));
            } else {
                $in = $pdo->prepare('INSERT INTO ' . T_SOURCE . ' (user_id, api_url, live_url, epg_url) VALUES (?,?,?,?)');
                $in->execute(array($uid, $api, $live, $epg));
            }
            $msg .= '；内置源已写入';
        }
    }
}

// 回显当前账号与内置源，方便核对
$users = $pdo->query('SELECT id, username, created_at FROM ' . T_USER)->fetchAll();
$src = $pdo->query('SELECT * FROM ' . T_SOURCE)->fetchAll();
?>
<!doctype html>
<meta charset="utf-8">
<title>HoopoeTV 云端备份 · 安装</title>
<style>
body{font-family:-apple-system,"Microsoft YaHei",sans-serif;background:#f4f6fa;color:#0f1720;margin:0;padding:24px}
.card{max-width:640px;margin:0 auto;background:#fff;border-radius:14px;padding:20px;box-shadow:0 6px 24px rgba(15,23,32,.08)}
h2{margin:0 0 6px;font-size:18px}
p{color:#6b7686;font-size:13px;margin:6px 0}
label{display:block;margin:12px 0 4px;font-size:13px;color:#33405a}
input{width:100%;height:40px;border:1px solid #dbe2ec;border-radius:10px;padding:0 10px;font-size:14px;box-sizing:border-box}
button{margin-top:16px;width:100%;height:42px;border:0;border-radius:12px;background:#3a7bd5;color:#fff;font-size:15px}
pre{background:#f4f6fa;border-radius:10px;padding:10px;font-size:12px;overflow:auto}
</style>
<div class="card">
  <h2>HoopoeTV 云端备份 · 安装工具</h2>
  <p>在这里建 App 要用的账号（对应表 <?php echo T_USER; ?>），顺手把内置源写进 <?php echo T_SOURCE; ?>。</p>
  <?php if ($msg): ?><p style="color:#1a7f37"><?php echo htmlspecialchars($msg); ?></p><?php endif; ?>
  <form method="post">
    <label>App 登录用户名</label>
    <input name="user" placeholder="例如 hoopoe">
    <label>App 登录密码</label>
    <input name="pass" type="password" placeholder="自己定，别用弱口令">
    <label>内置源 · 接口地址（可空）</label>
    <input name="api" placeholder="http://43.226.44.105:6066/api.php/tvboxcfg">
    <label>内置源 · 直播 m3u（可空）</label>
    <input name="live" placeholder="http://43.226.44.105:6061/zytx/interface.m3u">
    <label>内置源 · EPG（可空）</label>
    <input name="epg" placeholder="http://43.226.44.105:6061/zytx/playback.xml">
    <button type="submit">保存</button>
  </form>
  <p>当前账号：</p>
  <pre><?php echo htmlspecialchars(json_encode($users, JSON_UNESCAPED_UNICODE)); ?></pre>
  <p>当前内置源：</p>
  <pre><?php echo htmlspecialchars(json_encode($src, JSON_UNESCAPED_UNICODE)); ?></pre>
</div>
