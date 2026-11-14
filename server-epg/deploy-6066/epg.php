<?php
/**
 * 本机 MacCMS 直播节目单接口（XMLTV），可直接当 HoopoeTV 的 EPG 地址用
 *
 *   /epg.php?ch=东方卫视            单频道当天（约 5~20KB）
 *   /epg.php?ch=东方卫视&day=-1     昨天
 *   /epg.php?ch=东方卫视&days=3     今天起 3 天
 *   /epg.php                        全频道当天（大文件，不建议）
 */

// 与 application/database.php 保持一致
$DB = is_file(__DIR__ . '/epg_db.php') ? (require __DIR__ . '/epg_db.php') : [
    'host'   => '127.0.0.1',
    'port'   => 3306,
    'name'   => 'maccms10',
    'user'   => 'maccms10',
    'pass'   => 'Zhao324',
    'prefix' => 'zytx_',
];

date_default_timezone_set('Asia/Shanghai');

$ch   = isset($_GET['ch']) ? trim($_GET['ch']) : '';
$day  = isset($_GET['day']) ? (int)$_GET['day'] : 0;
$days = isset($_GET['days']) ? max(1, (int)$_GET['days']) : 1;

$dayStart = date('Y-m-d 00:00:00', strtotime(($day >= 0 ? '+' : '') . $day . ' day'));
$dayEnd   = date('Y-m-d 00:00:00', strtotime($dayStart . ' +' . $days . ' day'));

try {
    $pdo = new PDO(
        "mysql:host={$DB['host']};port={$DB['port']};dbname={$DB['name']};charset=utf8mb4",
        $DB['user'],
        $DB['pass'],
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC]
    );
} catch (Throwable $e) {
    header('HTTP/1.1 500 Internal Server Error');
    header('Content-Type: text/plain; charset=utf-8');
    echo "DB error: " . $e->getMessage() . "\n";
    exit;
}

$table = $DB['prefix'] . 'epg_programme';
$sql   = "SELECT channel, start_at, stop_at, title FROM `$table` WHERE start_at >= ? AND start_at < ?";
$args  = [$dayStart, $dayEnd];
if ($ch !== '') {
    $sql .= " AND channel = ?";
    $args[] = $ch;
}
$sql .= " ORDER BY channel, start_at";

$st = $pdo->prepare($sql);
$st->execute($args);
$rows = $st->fetchAll();

$etag = '"' . md5($ch . '|' . $day . '|' . $days . '|' . count($rows) . '|' . ($rows ? end($rows)['stop_at'] : '')) . '"';
header('ETag: ' . $etag);
header('Cache-Control: public, max-age=300');
if (isset($_SERVER['HTTP_IF_NONE_MATCH']) && trim($_SERVER['HTTP_IF_NONE_MATCH']) === $etag) {
    header('HTTP/1.1 304 Not Modified');
    exit;
}

header('Content-Type: application/xml; charset=utf-8');

$useGzip = function_exists('ob_gzhandler') && !ini_get('zlib.output_compression')
    && isset($_SERVER['HTTP_ACCEPT_ENCODING']) && stripos($_SERVER['HTTP_ACCEPT_ENCODING'], 'gzip') !== false;
if ($useGzip) {
    ob_start('ob_gzhandler');
}

echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
echo '<tv generator-info-name="zytx-epg">' . "\n";
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

function toXmltv($dt)
{
    return date('YmdHis', strtotime($dt));
}

function esc($s)
{
    return htmlspecialchars($s, ENT_QUOTES | ENT_XML1, 'UTF-8');
}
