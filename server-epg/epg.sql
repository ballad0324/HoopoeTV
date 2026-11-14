-- MacCMS（苹果CMS）直播节目单表
-- 用法：mysql -u用户 -p 库名 < epg.sql
-- 表名前缀按你的 MacCMS 实际前缀改（默认 mac_）

CREATE TABLE IF NOT EXISTS `mac_epg_programme` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `channel`    varchar(64)  NOT NULL DEFAULT '' COMMENT 'XMLTV 的 channel 属性，必须与 m3u 里的频道名完全一致',
  `start_at`   datetime     NOT NULL COMMENT '开播时间（东八区，库里存本地时间）',
  `stop_at`    datetime     NOT NULL COMMENT '结束时间',
  `title`      varchar(255) NOT NULL DEFAULT '' COMMENT '节目名',
  `updated_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_ch_start_title` (`channel`,`start_at`,`title`),
  KEY `idx_ch_start` (`channel`,`start_at`),
  KEY `idx_start` (`start_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播节目单（按频道+半小时/节目粒度）';
