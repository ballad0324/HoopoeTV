<?php
/**
 * 建表脚本：读同目录 epg.sql 里的 DDL，把表前缀换成 epg_db.php 里的 prefix 再执行。
 * 用法：php epg_init.php
 */
$DB = require __DIR__ . '/epg_db.php';
date_default_timezone_set('Asia/Shanghai');

$pdo = new PDO(
    "mysql:host={$DB['host']};port={$DB['port']};dbname={$DB['name']};charset=utf8mb4",
    $DB['user'],
    $DB['pass'],
    [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC]
);

$table = $DB['prefix'] . 'epg_programme';
$sql = file_get_contents(__DIR__ . '/epg.sql');
if ($sql === false) {
    fwrite(STDERR, "读不到 epg.sql\n");
    exit(1);
}
$sql = str_replace('zytx_epg_programme', $table, $sql);
$pdo->exec($sql);
printf("建表完成：%s\n", $table);
printf("当前行数：%d\n", (int)$pdo->query("SELECT COUNT(*) FROM `$table`")->fetchColumn());