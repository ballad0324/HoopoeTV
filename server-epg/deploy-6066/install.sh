#!/bin/bash
# EPG 服务部署（Linux / 宝塔）
# 放到 MacCMS 站点根目录（和 epg.php 同级），先改 epg_db.php，再执行本脚本。
set -e
cd "$(dirname "$0")"
PHP=${PHP:-php}

echo "== 1. 语法检查 =="
$PHP -l epg_db.php
$PHP -l epg_init.php
$PHP -l epg.php
$PHP -l epg_import.php

echo "== 2. 建表 =="
$PHP epg_init.php

echo "== 3. 首次导入源站节目单 =="
$PHP epg_import.php --src=${EPG_SRC:-http://43.226.44.105:6061/zytx/playback.xml} --days=3

echo "== 4. 自测接口 =="
curl -s -o /dev/null -w "epg.php?ch=东方卫视 -> %{http_code} %{size_download}B %{time_total}s\n" \
  "http://127.0.0.1/epg.php?ch=CCTV1" || true

cat <<'EOF'

== 5. 加定时任务（每 6 小时导一次，源站只下发当天，靠累积形成历史）==
  crontab -e 里加一行：
  17 */6 * * * cd /www/wwwroot/你的站点 && /www/server/php/74/bin/php epg_import.php --src=http://43.226.44.105:6061/zytx/playback.xml --days=3 >> epg_import.log 2>&1

完成。手机端 EPG 地址填：http://43.226.44.105:6066/epg.php?ch={name}
EOF