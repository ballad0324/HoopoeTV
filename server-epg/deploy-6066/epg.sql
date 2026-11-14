-- MacCMS（本机 D:\wwwroot\MacCMS）直播节目单表，前缀 zytx_ 与站内配置一致
-- 执行：D:\BtSoft\mysql\MySQL5.7\bin\mysql.exe -uroot -p maccms10 < epg.sql

CREATE TABLE IF NOT EXISTS `zytx_epg_programme` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `channel`    varchar(64)  NOT NULL DEFAULT '' COMMENT 'XMLTV 的 channel 属性，与 m3u 频道名一致',
  `start_at`   datetime     NOT NULL COMMENT '开播时间（东八区）',
  `stop_at`    datetime     NOT NULL COMMENT '结束时间',
  `title`      varchar(255) NOT NULL DEFAULT '' COMMENT '节目名',
  `updated_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_ch_start_title` (`channel`,`start_at`,`title`),
  KEY `idx_ch_start` (`channel`,`start_at`),
  KEY `idx_start` (`start_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播节目单';
