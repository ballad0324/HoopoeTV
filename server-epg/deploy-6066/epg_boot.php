<?php
/**
 * 无人值守部署入口：没有 SSH / 没有 shell 时，用浏览器访问这个文件完成建表与导入。
 *
 *   epg_boot.php?key=KEY&do=init     建表
 *   epg_boot.php?key=KEY&do=import   导入（默认）
 *   epg_boot.php?key=KEY&do=all      建表 + 导入（首次部署用这个）
 *   epg_boot.php?key=KEY&do=import&src=http...&days=3
 *
 * key 在 epg_db.php 的 'key' 里，改掉它就换了访问口令。
 * 宝塔「计划任务 → 访问 URL」可以每 6 小时打一次 do=import，等同于 crontab。
 */
$DB  = require __DIR__ . '/epg_db.php';
$KEY = isset($DB['key']) ? (string)$DB['key'] : '';
$given = isset($_GET['key']) ? (string)$_GET['key'] : '';
if ($KEY === '' || !hash_equals($KEY, $given)) {
    header('HTTP/1.1 404 Not Found');
    header('Content-Type: text/plain; charset=utf-8');
    echo "404 Not Found\n";
    exit;
}

@set_time_limit(0);
@ini_set('memory_limit', '512M');
date_default_timezone_set('Asia/Shanghai');
header('Content-Type: text/plain; charset=utf-8');

$do   = isset($_GET['do']) ? (string)$_GET['do'] : 'import';
$src  = isset($_GET['src']) ? (string)$_GET['src'] : 'http://43.226.44.105:6061/zytx/playback.xml';
$days = isset($_GET['days']) ? max(1, (int)$_GET['days']) : 3;

try {
    $pdo = new PDO(
        "mysql:host={$DB['host']};port={$DB['port']};dbname={$DB['name']};charset=utf8mb4",
        $DB['user'],
        $DB['pass'],
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC]
    );
} catch (Throwable $e) {
    echo "FAIL db: " . $e->getMessage() . "\n";
    exit;
}
$table = $DB['prefix'] . 'epg_programme';
echo "db ok: {$DB['name']} / table {$table}\n";

if ($do === 'init' || $do === 'all') {
    $sql = file_get_contents(__DIR__ . '/epg.sql');
    if ($sql === false) {
        echo "FAIL 读不到 epg.sql\n";
        exit;
    }
    $sql = str_replace('zytx_epg_programme', $table, $sql);
    $pdo->exec($sql);
    echo "init ok\n";
}

if ($do !== 'import' && $do !== 'all') {
    echo "rows=" . (int)$pdo->query("SELECT COUNT(*) FROM `$table`")->fetchColumn() . "\n";
    exit;
}

// 下载源站 XMLTV（源站不支持 Range / gzip，只能整份拿，2.9MB 左右）
$t0 = microtime(true);
$ctx = stream_context_create(['http' => ['timeout' => 300, 'header' => "User-Agent: Lavf/59.27.100\r\n"]]);
if (preg_match('#^https?://#i', $src)) {
    $raw = @file_get_contents($src, false, $ctx);
    if ($raw === false || strlen($raw) < 1000) {
        echo "FAIL 下载源站失败：$src\n";
        exit;
    }
    $tmp = tempnam(sys_get_temp_dir(), 'epgurl');
    file_put_contents($tmp, $raw);
    $size = strlen($raw);
    unset($raw);
    $xmlSrc = $tmp;
} else {
    if (!is_file($src)) {
        echo "FAIL 文件不存在：$src\n";
        exit;
    }
    $size = filesize($src);
    $xmlSrc = $src;
    $tmp = null;
}
printf("download ok: %s (%d B, %.1fs)\n", $src, $size, microtime(true) - $t0);

$xml = new XMLReader();
if (!@$xml->open($xmlSrc, null, LIBXML_NOCDATA | LIBXML_NOERROR)) {
    echo "FAIL 打不开 XMLTV\n";
    exit;
}

$pdo->prepare("DELETE FROM `$table` WHERE start_at >= ?")->execute([date('Y-m-d 00:00:00')]);

$rows = [];
$total = 0;
$keepFrom = strtotime(date('Y-m-d 00:00:00')) - ($days - 1) * 86400;
while ($xml->read()) {
    if ($xml->nodeType !== XMLReader::ELEMENT || $xml->name !== 'programme') {
        continue;
    }
    $total++;
    $channel = (string)$xml->getAttribute('channel');
    $start   = (string)$xml->getAttribute('start');
    $stop    = (string)$xml->getAttribute('stop');
    if ($channel === '' || $start === '' || $stop === '') {
        continue;
    }
    $node = $xml->readOuterXml();
    $title = '';
    if (preg_match('#<title[^>]*>(.*?)</title>#is', $node, $m)) {
        $title = trim(html_entity_decode(strip_tags($m[1]), ENT_QUOTES, 'UTF-8'));
    }
    if ($title === '') {
        continue;
    }
    $startAt = bootXmltv2mysql($start);
    $stopAt  = bootXmltv2mysql($stop);
    if (!$startAt || !$stopAt || strtotime($startAt) < $keepFrom) {
        continue;
    }
    $rows[] = [mb_substr($channel, 0, 64, 'UTF-8'), $startAt, $stopAt, mb_substr($title, 0, 255, 'UTF-8')];
}
$xml->close();
if ($tmp) {
    @unlink($tmp);
}
if (!$rows) {
    echo "FAIL 没有解析到节目（programme 总数 $total）\n";
    exit;
}

$inserted = 0;
foreach (array_chunk($rows, 500) as $chunk) {
    $place = rtrim(str_repeat('(?,?,?,?),', count($chunk)), ',');
    $st = $pdo->prepare("INSERT IGNORE INTO `$table` (`channel`,`start_at`,`stop_at`,`title`) VALUES $place");
    $flat = [];
    foreach ($chunk as $r) {
        foreach ($r as $v) {
            $flat[] = $v;
        }
    }
    $st->execute($flat);
    $inserted += (int)$st->rowCount();
}

$pdo->prepare("DELETE FROM `$table` WHERE stop_at < ?")->execute([date('Y-m-d 00:00:00', time() - $days * 86400)]);

printf("programme=%d 有效=%d 入库=%d 表内=%d 用时=%.1fs\n", $total, count($rows), $inserted,
    (int)$pdo->query("SELECT COUNT(*) FROM `$table`")->fetchColumn(), microtime(true) - $t0);
echo "OK 频道数=" . (int)$pdo->query("SELECT COUNT(DISTINCT channel) FROM `$table`")->fetchColumn() . "\n";

function bootXmltv2mysql($raw)
{
    $v = trim($raw);
    if (strlen($v) < 14) {
        return null;
    }
    $dt = DateTime::createFromFormat('YmdHis', substr($v, 0, 14), new DateTimeZone('UTC'));
    return $dt ? $dt->format('Y-m-d H:i:s') : null;
}