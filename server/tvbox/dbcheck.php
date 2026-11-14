<?php
// 临时诊断脚本：查看 MySQL 连接到底卡在哪一步。查完请删除本文件。
error_reporting(E_ALL);
ini_set('display_errors', '1');
header('Content-Type: text/plain; charset=utf-8');

if (!file_exists(__DIR__ . '/config.php')) {
    exit("找不到 config.php\n");
}
require __DIR__ . '/config.php';

echo "PHP " . PHP_VERSION . "\n";
echo "pdo_mysql 扩展: " . (extension_loaded('pdo_mysql') ? '已加载' : '未加载(致命)') . "\n";
echo "pdo_mysql 驱动: " . (in_array('mysql', PDO::getAvailableDrivers()) ? '可用' : '不可用(致命)') . "\n";
echo "config.php 配置: host=" . DB_HOST . " port=" . DB_PORT . " db=" . DB_NAME . " user=" . DB_USER . "\n";
echo "密码长度: " . strlen(DB_PASS) . " 字符（不显示内容，只校验是不是空/多个空格）\n";
if (trim(DB_PASS) === '') echo "!!! DB_PASS 是空的\n";
if (preg_match('/\s/', DB_PASS)) echo "!!! DB_PASS 里有空白字符\n";
if (DB_NAME === '' || DB_USER === '') echo "!!! DB_NAME 或 DB_USER 是空的\n";
echo "\n";

$hosts = array(DB_HOST, '127.0.0.1', 'localhost');
$seen = array();
foreach ($hosts as $h) {
    if (isset($seen[$h])) continue;
    $seen[$h] = 1;
    $dsn = 'mysql:host=' . $h . ';port=' . DB_PORT . ';dbname=' . DB_NAME . ';charset=utf8mb4';
    echo "===== 尝试 host=$h =====\n";
    try {
        $pdo = new PDO($dsn, DB_USER, DB_PASS, array(
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_TIMEOUT => 5,
        ));
        $ver = $pdo->query('SELECT VERSION()')->fetchColumn();
        echo "结果: 连接成功，MySQL $ver\n";
        $t = $pdo->query("SHOW TABLES LIKE 'hoopoe_app_%'")->fetchAll(PDO::FETCH_COLUMN);
        echo "hoopoe 表: " . (count($t) ? implode(', ', $t) : '一张都没有（需要执行 install.sql）') . "\n";
        try {
            $c = (int)$pdo->query('SELECT COUNT(*) FROM hoopoe_app_user')->fetchColumn();
            echo "hoopoe_app_user 行数: $c\n";
        } catch (Exception $e2) {
            echo "hoopoe_app_user 查询失败: " . $e2->getMessage() . "\n";
        }
    } catch (Exception $e) {
        echo "结果: 失败\n";
        echo "错误码: " . $e->getCode() . "\n";
        echo "错误信息: " . $e->getMessage() . "\n";
        echo "排查提示: " . hint($e->getMessage()) . "\n";
    }
    echo "\n";
}

function hint($msg)
{
    if (stripos($msg, 'could not find driver') !== false) return 'PHP 没装 pdo_mysql 扩展：宝塔→软件商店→PHP 设置→安装扩展 pdo_mysql';
    if (stripos($msg, 'Access denied') !== false) return '账号或密码不对：宝塔→数据库 里核对数据库名、用户名、密码三者是否与本 config.php 一致';
    if (stripos($msg, 'Unknown database') !== false) return '库名不对：宝塔→数据库 里看实际库名（大小写要一致）';
    if (stripos($msg, 'Connection refused') !== false || stripos($msg, 'No such file') !== false) return '连不上 MySQL 端口：宝塔→数据库→MySQL 是否运行；若是 Docker 部署，host 要填容器可达的地址';
    if (stripos($msg, "Can't connect") !== false) return 'MySQL 未启动或被防火墙拦截';
    return '把上面原始错误发我，我按错误码给下一步';
}
