HoopoeTV 直播节目单（EPG）服务 —— 部署包
=========================================

作用：把源站那份 2.9MB 的全量 playback.xml，改造成"按频道只给几十条"的接口，
手机端一秒内能拿到节目单；同时每天累积，形成 7 天回看所需的历史。

包内文件（7 个）
----------------
  epg_db.php      数据库连接 + 访问口令（唯一需要改的文件）
  epg.sql         建表 DDL（被 epg_init.php / epg_boot.php 调用）
  epg_init.php    建表脚本（有 shell 时用）
  epg.php         查询接口，输出 XMLTV：?ch=东方卫视&day=-1&days=3
  epg_import.php  导入脚本（有 shell 时用）
  epg_boot.php    无 shell 时的部署/导入入口（浏览器访问，带 key）
  install.sh      Linux 一键部署（语法检查→建表→首次导入→自测）

部署步骤（推荐：无 shell 也能完成）
---------------------------------
1. 把 7 个文件传到 MacCMS 站点根目录（index.php / application 同级）。
2. 编辑 epg_db.php，填对 5 个字段：host / name / user / pass / prefix
   （prefix 是表前缀，不是站点名，可在站点 application/database.php 里抄）
3. 浏览器打开：
     http://43.226.44.105:6066/epg_boot.php?key=x50k4cgqflvtiwm916j7&do=all
   看到 programm=… 入库=… 表内=… OK 频道数=… 即部署成功。
4. 自测：
     http://43.226.44.105:6066/epg.php?ch=东方卫视
   返回 <tv> + 若干 <programme> 即可。
5. 定时刷新（二选一）：
   a) 宝塔面板 → 计划任务 → 类型选「访问URL」，每 6 小时：
        http://43.226.44.105:6066/epg_boot.php?key=x50k4cgqflvtiwm916j7&do=import
   b) 有 shell 就加 crontab：
        17 */6 * * * cd /www/wwwroot/站点目录 && /www/server/php/74/bin/php epg_import.php --src=http://43.226.44.105:6061/zytx/playback.xml --days=3 >> epg_import.log 2>&1

手机端
------
设置页 EPG 地址填：http://43.226.44.105:6066/epg.php?ch={name}
（App 已内置这一条为第一候选，部署完无需再改设置）

安全
----
epg_boot.php 由 epg_db.php 里的 key 保护，key 不符直接 404。
把 key 改掉就等于换了口令；不想留这个入口，部署完删掉 epg_boot.php 即可，
定时改走 crontab。

三条已实测的事实（避免误判）
-----------------------------
1. 6061 的 playback.xml 不支持 ?ch= 过滤、不支持 Range、不压缩，只能整份下载，
   所以它本身做不到"单频道快速返回"，这层自建接口就是为此。
2. 6061 只下发当天节目，天然没有历史。7 天回看要靠这里每天累积；
   刚部署的头一两天，昨天/前天确实是空的，属正常。
3. 本接口按频道名精确匹配（channel = 频道名），要和 m3u 里的写法完全一致；
   某频道拿不到节目，先比对名字（多空格、全半角都会匹配不到）。