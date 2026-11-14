<?php
/**
 * 把 XMLTV（playback.xml）导入本机 MacCMS 的 MySQL：表 zytx_epg_programme
 *
 * 用法（在 D:\wwwroot\MacCMS 下）：
 *   D:\BtSoft\php\74\php.exe epg_import.php --src=D:\Projects\Box\shots\playback.xml
 *   D:\BtSoft\php\74\php.exe epg_import.php --src=http://43.226.44.105:6061/zytx/playback.xml --days=3
 *
 * 建议每天跑一次（源站只下发当天节目）。
 */

$opts = getopt('', ['src:', 'days::']);
$src  = isset($opts['src']) ? $opts['src'] : '';
$days = isset($opts['days']) ? (int)$opts['days'] : 3;

if ($src === '') {
    fwrite(STDERR, "缺少 --src（XMLTV 路径或 URL）\n");
    exit(1);
}

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

$pdo = new PDO(
    "mysql:host={$DB['host']};port={$DB['port']};dbname={$DB['name']};charset=utf8mb4",
    $DB['user'],
    $DB['pass'],
    [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC]
);
$table = $DB['prefix'] . 'epg_programme';

// 1) 打开 XMLTV（PHP 7.4 的 XMLReader::open 只接受 3 个参数，URL 先落临时文件）
$xml = new XMLReader();
$xmlSrc = $src;
$tmpFile = null;
if (preg_match('#^https?://#i', $src)) {
    $ctx = stream_context_create(['http' => ['timeout' => 300, 'header' => "User-Agent: Lavf/59.27.100\r\n"]]);
    $raw = @file_get_contents($src, false, $ctx);
    if ($raw === false || strlen($raw) < 1000) {
        fwrite(STDERR, "下载失败：$src\n");
        exit(1);
    }
    $tmpFile = tempnam(sys_get_temp_dir(), 'epgurl');
    file_put_contents($tmpFile, $raw);
    unset($raw);
    $xmlSrc = $tmpFile;
} elseif (!is_file($src)) {
    fwrite(STDERR, "文件不存在：$src\n");
    exit(1);
}
if (!$xml->open($xmlSrc, null, LIBXML_NOCDATA | LIBXML_NOERROR)) {
    fwrite(STDERR, "打不开 XMLTV：$xmlSrc\n");
    exit(1);
}
// 2) 今天起的时段先清掉，重导不重复（唯一索引也会兜底）
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
    $node  = $xml->readOuterXml();
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
    if (strtotime($startAt) < $keepFrom) {
        continue;
    }
    $rows[] = [
        mb_substr($channel, 0, 64, 'UTF-8'),
        $startAt,
        $stopAt,
        mb_substr($title, 0, 255, 'UTF-8'),
    ];
}

if (!$rows) {
    fwrite(STDERR, "没有解析到节目（programme 总数 $total）\n");
    exit(1);
}

// 3) 分批 INSERT IGNORE
$inserted = 0;
foreach (array_chunk($rows, 500) as $chunk) {
    $place = rtrim(str_repeat('(?,?,?,?),', count($chunk)), ',');
    $sql   = "INSERT IGNORE INTO `$table` (`channel`,`start_at`,`stop_at`,`title`) VALUES $place";
    $flat  = [];
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
$pdo->prepare("DELETE FROM `$table` WHERE stop_at < ?")
    ->execute([date('Y-m-d 00:00:00', time() - $days * 86400)]);

printf("[%s] programme=%d 有效=%d 入库=%d 表内=%d\n",
    date('Y-m-d H:i:s'), $total, count($rows), $inserted,
    (int)$pdo->query("SELECT COUNT(*) FROM `$table`")->fetchColumn());

/** 20261113060000 或 20261113060000 +0800 → '2026-11-13 06:00:00' */
function xmltv2mysql($raw)
{
    $v = trim($raw);
    if (strlen($v) < 14) {
        return null;
    }
    $head = substr($v, 0, 14);
    $dt = DateTime::createFromFormat('YmdHis', $head, new DateTimeZone('UTC'));
    return $dt ? $dt->format('Y-m-d H:i:s') : null;
}
