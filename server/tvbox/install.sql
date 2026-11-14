-- HoopoeTV 云端备份：MySQL 建表语句
-- 在 MacCMS 所在库执行即可（不会动 mac_ 开头的任何表）

CREATE TABLE IF NOT EXISTS `hoopoe_app_user` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `pass_hash` varchar(255) NOT NULL,
  `token` char(48) DEFAULT NULL,
  `token_expire` int unsigned NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `hoopoe_app_backup` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int unsigned NOT NULL,
  `name` varchar(64) NOT NULL,
  `size` int unsigned NOT NULL DEFAULT 0,
  `payload` longtext NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_name` (`user_id`,`name`),
  KEY `idx_user_time` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `hoopoe_app_source` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int unsigned NOT NULL,
  `api_url` varchar(255) NOT NULL DEFAULT '',
  `live_url` varchar(255) NOT NULL DEFAULT '',
  `epg_url` varchar(255) NOT NULL DEFAULT '',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- v1.7：注册邀请码
CREATE TABLE IF NOT EXISTS `hoopoe_app_invite` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `max_uses` int unsigned NOT NULL DEFAULT 0 COMMENT '0=不限次数',
  `used` int unsigned NOT NULL DEFAULT 0,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `note` varchar(128) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `hoopoe_app_invite` (`code`,`max_uses`,`used`,`enabled`,`note`)
SELECT '98555576', 0, 0, 1, '默认总邀请码'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `hoopoe_app_invite`);

-- v1.7：播放记录（继续观看）
CREATE TABLE IF NOT EXISTS `hoopoe_app_history` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int unsigned NOT NULL,
  `site_key` varchar(64) NOT NULL DEFAULT '',
  `vod_id` varchar(64) NOT NULL DEFAULT '',
  `name` varchar(128) NOT NULL DEFAULT '',
  `pic` varchar(512) NOT NULL DEFAULT '',
  `flag` varchar(64) NOT NULL DEFAULT '',
  `pos` int unsigned NOT NULL DEFAULT 0,
  `dur` int unsigned NOT NULL DEFAULT 0,
  `ep` int unsigned NOT NULL DEFAULT 1,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_vod` (`user_id`,`site_key`,`vod_id`),
  KEY `idx_user_time` (`user_id`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

