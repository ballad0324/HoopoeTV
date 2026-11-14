<?php
/**
 * MacCMS 直播节目单接口：把 mac_epg_programme 按需输出成 XMLTV，直接给 HoopoeTV 当 EPG 地址用
 *
 * 请求示例：
 *   http://你的域名/epg.php?ch=东方卫视            → 只返回这个频道当天（约 5~20KB，秒回）
 *   http://你的域名/epg.php?ch=东方卫视&day=-1     → 昨天
 *   http://你的域名/epg.php?ch=东方卫视&days=3     → 今天起 3 天
 *   http://你的域名/epg.php                       → 全频道当天（大文件，不建议）
 *
 * App 设置页「EPG 地址」填：http://你的域名/epg.php?ch={name}
 * 注意：{name} 占位符需要 App 端支持替换（当前版本还没做，我可以加上；不做就把 ch 去掉，
 *      代价是每次仍要下全量）。
 */

// ---- 数据库配置：改成你自己的 MacCMS 库 ----
$DB_HOST = '127.0.0.1';
$DB_PORT = 3306;
$DB_NAME = 'maccms';
$DB_USER = 'root';
$DB_PASS = '';
$PREFIX  = 'mac_';

date_default_timezone_set('Asia/Shanghai');

$ch   = isset($_GET['ch']) ? trim($_GET['ch']) : '';
$day  = isset($_GET['day']) ? (int)$_GET['day'] : 0;       // 0=今天，-1=昨天
$days = isset($_GET['days']) ? max(1, (int)$_GET['days']) : 1;

$dayStart = date('Y-m-d 00:00:00', strtotime(($day >= 0 ? '+' : '') . $day . ' day'));
$dayEnd   = date('Y-m-d 00:00:00', strtotime($dayStart . ' +' . $days . ' day'));

try {
    $pdo = new PDO("mysql:host=$DB_HOST;port=$DB_PORT;dbname=$DB_NAME;charset=utf8mb4", $DB_USER, $DB_PASS, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
} catch (Throwable $e) {
    header('HTTP/1.1 500 Internal Server Error');
    header('Content-Type: text/plain; charset=utf-8');
    echo "DB error\n";
    exit;
}

$table = $PREFIX . 'epg_programme';
$sql = "SELECT channel, start_at, stop_at, title FROM `$table`
        WHERE start_at >= ? AND start_at < ?";
$args = [$dayStart, $dayEnd];
if ($ch !== '') {
    $sql .= " AND channel = ?";
    $args[] = $ch;
}
$sql .= " ORDER BY channel, start_at";

$st = $pdo->prepare($sql);
$st->execute($args);
$rows = $st->fetchAll();

// ---- 缓存协商：ETag + 5 分钟浏览器/客户端缓存，数据不变就 304 ----
$etag = '"' . md5($ch . '|' . $day . '|' . $days . '|' . count($rows) . '|' . ($rows ? $rows[count($rows) - 1]['stop_at'] : '')) . '"';
header('ETag: ' . $etag);
header('Cache-Control: public, max-age=300');
if (isset($_SERVER['HTTP_IF_NONE_MATCH']) && trim($_SERVER['HTTP_IF_NONE_MATCH']) === $etag) {
    header('HTTP/1.1 304 Not Modified');
    exit;
}

header('Content-Type: application/xml; charset=utf-8');

// ---- gzip：2.6MB → 约 600KB ----
$useGzip = function_exists('ob_gzhandler') && !ini_get('zlib.output_compression')
    && isset($_SERVER['HTTP_ACCEPT_ENCODING']) && stripos($_SERVER['HTTP_ACCEPT_ENCODING'], 'gzip') !== false;
if ($useGzip) {
    ob_start('ob_gzhandler');
}

echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
echo '<tv generator-info-name="maccms-epg">' . "\n";
foreach ($rows as $r) {
    echo '  <programme start="' . toXmltv($r['start_at']) . ' +0800" stop="' . toXmltv($r['stop_at'])
        . ' +0800" channel="' . esc($r['channel']) . '">' . "\n";
    echo '    <title lang="zh">' . esc($r['title']) . '</title>' . "\n";
    echo '  </programme>' . "\n";
}
echo '</tv>' . "\n";

if ($useGzip) {
    ob_end_flush();
}

/** '2026-11-13 06:00:00' → '20261113060000' */
function toXmltv($dt)
{
    return date('YmdHis', strtotime($dt));
}

function esc($s)
{
    return htmlspecialchars($s, ENT_QUOTES | ENT_XML1, 'UTF-8');
}
