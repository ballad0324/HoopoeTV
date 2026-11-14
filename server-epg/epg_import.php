<?php
/**
 * 把 XMLTV 的 playback.xml 导入 MacCMS 的 MySQL（表 mac_epg_programme）
 *
 * 用法：
 *   php epg_import.php --src=/path/playback.xml
 *   php epg_import.php --src=http://43.226.44.105:6061/zytx/playback.xml --days=3
 *
 * 建议 cron：每天 06:10 跑一次（源站只下发当天节目，所以每天必须导一次）
 *   10 6 * * * cd /www/wwwroot/你的站 && php epg_import.php --src=http://43.226.44.105:6061/zytx/playback.xml --days=3 >> /tmp/epg_import.log 2>&1
 */

$opts = getopt('', ['src:', 'days::', 'host::', 'db::', 'user::', 'pass::', 'prefix::', 'port::']);
$src    = isset($opts['src']) ? $opts['src'] : '';
$days   = isset($opts['days']) ? (int)$opts['days'] : 3;   // 保留最近几天（含今天）
$host   = isset($opts['host']) ? $opts['host'] : '127.0.0.1';
$db     = isset($opts['db']) ? $opts['db'] : 'maccms';
$user   = isset($opts['user']) ? $opts['user'] : 'root';
$pass   = isset($opts['pass']) ? $opts['pass'] : '';
$prefix = isset($opts['prefix']) ? $opts['prefix'] : 'mac_';
$port   = isset($opts['port']) ? (int)$opts['port'] : 3306;

if ($src === '') {
    fwrite(STDERR, "缺少 --src（XMLTV 文件路径或 URL）\n");
    exit(1);
}

date_default_timezone_set('Asia/Shanghai');

$pdo = new PDO("mysql:host=$host;port=$port;dbname=$db;charset=utf8mb4", $user, $pass, [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
]);
$table = $prefix . 'epg_programme';

// 1) 打开 XMLTV（本地文件或 URL 都支持）
$xml = new XMLReader();
if (preg_match('#^https?://#i', $src)) {
    // 源站很慢，给足超时；URL 由 curl 先落盘也行
    $ctx = stream_context_create(['http' => ['timeout' => 180, 'header' => "User-Agent: Lavf/59.27.100\r\n"] ]);
    $xml->open($src, null, LIBXML_NOCDATA | LIBXML_NOERROR, $ctx);
} else {
    if (!is_file($src)) {
        fwrite(STDERR, "文件不存在：$src\n");
        exit(1);
    }
    $xml->open($src, null, LIBXML_NOCDATA | LIBXML_NOERROR);
}

// 2) 清掉今天要重导的时段，避免重复（唯一索引也会兜底）
$todayStart = date('Y-m-d 00:00:00');
$pdo->prepare("DELETE FROM `$table` WHERE start_at >= ?")->execute([$todayStart]);

$rows = [];
$total = 0;
$inserted = 0;
$stamp = time();

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
    // 抓 program 块里的 <title>
    $node = $xml->readOuterXml();
    $title = '';
    if (preg_match('#<title[^>]*>(.*?)</title>#is', $node, $m)) {
        $title = trim(html_entity_decode(strip_tags($m[1]), ENT_QUOTES, 'UTF-8'));
    }
    if ($title === '') {
        continue;
    }
    $startAt = xmltv2mysql($start);
    $stopAt  = xmltv2mysql($stop);
    if (!$startAt || !$stopAt) {
        continue;
    }
    // 只留最近 $days 天（含今天），其余丢掉，省库
    if (strtotime($startAt) < strtotime(date('Y-m-d 00:00:00')) - ($days - 1) * 86400) {
        continue;
    }
    $channel = mb_substr($channel, 0, 64, 'UTF-8');
    $title   = mb_substr($title, 0, 255, 'UTF-8');
    $rows[] = [$channel, $startAt, $stopAt, $title];
}

if (!$rows) {
    fwrite(STDERR, "没有解析到节目（programme 总数 $total）\n");
    exit(1);
}

// 3) 分批 INSERT IGNORE（唯一索引自然去重）
foreach (array_chunk($rows, 500) as $chunk) {
    $place = rtrim(str_repeat('(?,?,?,?),', count($chunk)), ',');
    $sql = "INSERT IGNORE INTO `$table` (`channel`,`start_at`,`stop_at`,`title`) VALUES $place";
    $flat = [];
    foreach ($chunk as $r) {
        foreach ($r as $v) {
            $flat[] = $v;
        }
    }
    $st = $pdo->prepare($sql);
    $st->execute($flat);
    $inserted += (int)$st->rowCount();
}

// 4) 清过期
$cut = date('Y-m-d 00:00:00', time() - $days * 86400);
$pdo->prepare("DELETE FROM `$table` WHERE stop_at < ?")->execute([$cut]);

printf("[%s] programme=%d 解析=%d 入库=%d 表内=%d\n",
    date('Y-m-d H:i:s'), $total, count($rows), $inserted,
    (int)$pdo->query("SELECT COUNT(*) FROM `$table`")->fetchColumn());

/** 20261113060000 +0800 / 20261113060000 → '2026-11-13 06:00:00'（统一按东八区） */
function xmltv2mysql($raw)
{
    $v = trim($raw);
    if (strlen($v) < 14) {
        return null;
    }
    $head = substr($v, 0, 14);
    $dt = DateTime::createFromFormat('YmdHis', $head, new DateTimeZone('UTC'));
    if (!$dt) {
        return null;
    }
    // XMLTV 里的时间是「当地电视台时间」，服务端这里直接按 +0800 处理
    return $dt->format('Y-m-d H:i:s');
}
