-- ============================================================================
-- AI Workspace 数据库初始化脚本
-- 由 docker-compose.infra.yml 挂载到 MySQL 容器，仅在数据卷首次初始化时执行。
--
-- 维护约定：任何表结构变更必须同步更新本文件
-- （可用 docker exec ai-workspace-mysql mysqldump -uroot -p123456 --no-data ai_workspace 重新导出）。
--
-- 软删除约定：deleted BIGINT，0=未删除，非 0=删除时刻毫秒时间戳（GORM soft_delete milli 模式）；
-- sys_user/sys_role 的唯一键为 (username, deleted) / (role_code, deleted) 复合键，软删后可重建同名。
--
-- 默认种子数据：管理员 admin / 123456（bcrypt），角色 ROLE_ADMIN。
-- ============================================================================



/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `agent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT 'Agentåç§°',
  `description` varchar(500) DEFAULT NULL COMMENT 'æè¿°',
  `system_prompt` text COMMENT 'ç³»ç»Ÿæç¤ºè¯',
  `model` varchar(100) DEFAULT NULL COMMENT 'ä½¿ç”¨çš„æ¨¡åž‹ï¼ˆä¸ºç©ºèµ°AIæœåŠ¡é»˜è®¤ï¼‰',
  `tools` varchar(500) DEFAULT NULL COMMENT 'å·¥å…·åJSONæ•°ç»„ï¼Œå¦‚["search"]',
  `mcp_servers` varchar(500) DEFAULT NULL COMMENT 'MCPæœåŠ¡å™¨åJSONæ•°ç»„',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT 'æ˜¯å¦å¯ç”¨',
  `create_by` bigint DEFAULT NULL COMMENT 'åˆ›å»ºäººID',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agentå®šä¹‰è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `chat_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL COMMENT 'ä¼šè¯ID',
  `role` varchar(20) NOT NULL COMMENT 'user/assistant/system',
  `content` longtext NOT NULL COMMENT 'æ¶ˆæ¯å†…å®¹',
  `token_count` int DEFAULT NULL COMMENT 'æ¶ˆè€—Tokenæ•°',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='èŠå¤©æ¶ˆæ¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `chat_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_name` varchar(100) NOT NULL COMMENT 'æ¨¡åž‹åç§°',
  `provider` varchar(50) NOT NULL COMMENT 'æä¾›å•†ï¼ˆOpenAI/Ollamaç­‰ï¼‰',
  `api_url` varchar(500) DEFAULT NULL COMMENT 'APIåœ°å€',
  `api_key` varchar(500) DEFAULT NULL COMMENT 'API Keyï¼ˆåŠ å¯†å­˜å‚¨ï¼‰',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT 'æ˜¯å¦å¯ç”¨',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ¨¡åž‹é…ç½®è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `chat_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ·ID',
  `title` varchar(255) NOT NULL DEFAULT 'æ–°å¯¹è¯' COMMENT 'ä¼šè¯æ ‡é¢˜',
  `model_name` varchar(100) DEFAULT NULL COMMENT 'ä½¿ç”¨çš„æ¨¡åž‹',
  `system_prompt` text COMMENT '会话级系统提示词',
  `summary` text COMMENT '滚动摘要',
  `summary_upto_id` bigint NOT NULL DEFAULT 0 COMMENT '摘要覆盖到的最大消息ID',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='èŠå¤©ä¼šè¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `file_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_name` varchar(255) NOT NULL COMMENT 'åŽŸå§‹æ–‡ä»¶å',
  `file_path` varchar(500) NOT NULL COMMENT 'MinIOå­˜å‚¨è·¯å¾„',
  `file_size` bigint DEFAULT NULL COMMENT 'æ–‡ä»¶å¤§å°ï¼ˆå­—èŠ‚ï¼‰',
  `file_type` varchar(50) DEFAULT NULL COMMENT 'æ–‡ä»¶ç±»åž‹/MIME',
  `upload_by` bigint DEFAULT NULL COMMENT 'ä¸Šä¼ äººID',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_upload_by` (`upload_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ–‡ä»¶ä¿¡æ¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `kb_chunk_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kb_chunk_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_id` bigint NOT NULL COMMENT 'æ–‡æ¡£ID',
  `task_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
  `error_msg` text COMMENT 'å¤±è´¥åŽŸå› ',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ–‡æ¡£åˆ‡ç‰‡ä»»åŠ¡è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `kb_document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kb_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kb_id` bigint NOT NULL COMMENT 'çŸ¥è¯†åº“ID',
  `file_name` varchar(255) NOT NULL COMMENT 'æ–‡ä»¶å',
  `file_path` varchar(500) NOT NULL COMMENT 'æ–‡ä»¶å­˜å‚¨è·¯å¾„ï¼ˆMinIOï¼‰',
  `file_size` bigint DEFAULT NULL COMMENT 'æ–‡ä»¶å¤§å°ï¼ˆå­—èŠ‚ï¼‰',
  `file_type` varchar(50) DEFAULT NULL COMMENT 'æ–‡ä»¶ç±»åž‹',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DONE/FAILED',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='çŸ¥è¯†åº“æ–‡æ¡£è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `kb_knowledge_base`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kb_knowledge_base` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kb_name` varchar(100) NOT NULL COMMENT 'çŸ¥è¯†åº“åç§°',
  `description` text COMMENT 'æè¿°',
  `create_by` bigint DEFAULT NULL COMMENT 'åˆ›å»ºäººID',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='çŸ¥è¯†åº“è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mcp_server`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mcp_server` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT 'åç§°',
  `description` varchar(500) DEFAULT NULL COMMENT 'æè¿°',
  `transport` varchar(20) NOT NULL DEFAULT 'sse' COMMENT 'ä¼ è¾“æ–¹å¼ï¼šsse/stdio',
  `url` varchar(500) DEFAULT NULL COMMENT 'sseä¼ è¾“çš„æœåŠ¡åœ°å€',
  `command` varchar(500) DEFAULT NULL COMMENT 'stdioä¼ è¾“çš„å¯åŠ¨å‘½ä»¤',
  `config` longtext COMMENT 'JSONé…ç½®ï¼ˆheaders/env/argsï¼‰',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT 'æ˜¯å¦å¯ç”¨',
  `create_by` bigint DEFAULT NULL COMMENT 'åˆ›å»ºäººID',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCPæœåŠ¡å™¨è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `prompt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `prompt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(150) NOT NULL COMMENT 'æ ‡é¢˜',
  `content` longtext NOT NULL COMMENT 'æç¤ºè¯å†…å®¹',
  `category` varchar(50) DEFAULT NULL COMMENT 'åˆ†ç±»',
  `description` varchar(500) DEFAULT NULL COMMENT 'æè¿°',
  `create_by` bigint DEFAULT NULL COMMENT 'åˆ›å»ºäººID',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æç¤ºè¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_name` varchar(100) NOT NULL COMMENT 'ä»»åŠ¡åç§°',
  `job_group` varchar(50) DEFAULT 'DEFAULT' COMMENT 'ä»»åŠ¡åˆ†ç»„',
  `invoke_target` varchar(100) NOT NULL COMMENT 'è°ƒç”¨çš„JobHandleråç§°',
  `cron_expression` varchar(100) NOT NULL COMMENT 'Spring 6æ®µcronè¡¨è¾¾å¼',
  `job_params` varchar(500) DEFAULT NULL COMMENT 'ä¼ ç»™å¤„ç†å™¨çš„å‚æ•°',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0=è¿è¡Œä¸­ 1=æš‚åœ',
  `remark` varchar(500) DEFAULT NULL COMMENT 'å¤‡æ³¨',
  `create_by` bigint DEFAULT NULL COMMENT 'åˆ›å»ºäººID',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å®šæ—¶ä»»åŠ¡è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_job_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint DEFAULT NULL COMMENT 'ä»»åŠ¡ID',
  `job_name` varchar(100) DEFAULT NULL COMMENT 'ä»»åŠ¡åç§°',
  `invoke_target` varchar(100) DEFAULT NULL COMMENT 'è°ƒç”¨ç›®æ ‡',
  `job_params` varchar(500) DEFAULT NULL COMMENT 'å‚æ•°',
  `status` tinyint DEFAULT NULL COMMENT '0=æˆåŠŸ 1=å¤±è´¥',
  `job_message` varchar(500) DEFAULT NULL COMMENT 'æ‰§è¡Œä¿¡æ¯',
  `exception_info` text COMMENT 'å¼‚å¸¸å †æ ˆ',
  `cost_ms` bigint DEFAULT NULL COMMENT 'è€—æ—¶(æ¯«ç§’)',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å®šæ—¶ä»»åŠ¡æ‰§è¡Œæ—¥å¿—è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT 'çˆ¶èœå•ID',
  `menu_name` varchar(50) NOT NULL COMMENT 'èœå•åç§°',
  `path` varchar(200) DEFAULT NULL COMMENT 'è·¯ç”±è·¯å¾„',
  `component` varchar(200) DEFAULT NULL COMMENT 'ç»„ä»¶è·¯å¾„',
  `permission` varchar(100) DEFAULT NULL COMMENT 'æƒé™æ ‡è¯†',
  `menu_type` char(1) NOT NULL COMMENT 'Mç›®å½• Cèœå• FæŒ‰é’®',
  `sort` int NOT NULL DEFAULT '0' COMMENT 'æŽ’åº',
  `icon` varchar(100) DEFAULT NULL COMMENT 'å›¾æ ‡',
  `visible` tinyint NOT NULL DEFAULT '1' COMMENT 'æ˜¯å¦æ˜¾ç¤º',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='èœå•è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(50) NOT NULL COMMENT 'è§’è‰²åç§°',
  `role_code` varchar(50) NOT NULL COMMENT 'è§’è‰²æ ‡è¯†',
  `remark` varchar(255) DEFAULT NULL COMMENT 'å¤‡æ³¨',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è§’è‰²è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL COMMENT 'è§’è‰²ID',
  `menu_id` bigint NOT NULL COMMENT 'èœå•ID',
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è§’è‰²èœå•å…³è”è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT 'ç”¨æˆ·å',
  `password` varchar(255) NOT NULL COMMENT 'å¯†ç ï¼ˆBCryptï¼‰',
  `nickname` varchar(50) DEFAULT NULL COMMENT 'æ˜µç§°',
  `avatar` varchar(500) DEFAULT NULL COMMENT 'å¤´åƒURL',
  `email` varchar(100) DEFAULT NULL COMMENT 'é‚®ç®±',
  `phone` varchar(20) DEFAULT NULL COMMENT 'æ‰‹æœºå·',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'çŠ¶æ€ 1æ­£å¸¸ 0ç¦ç”¨',
  `remark` varchar(500) DEFAULT NULL COMMENT 'å¤‡æ³¨',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç³»ç»Ÿç”¨æˆ·è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ·ID',
  `role_id` bigint NOT NULL COMMENT 'è§’è‰²ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·è§’è‰²å…³è”è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tool`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tool` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT 'å·¥å…·åç§°',
  `description` varchar(500) DEFAULT NULL COMMENT 'æè¿°',
  `tool_type` varchar(30) NOT NULL DEFAULT 'http' COMMENT 'ç±»åž‹ï¼šhttp/builtin',
  `endpoint` varchar(500) DEFAULT NULL COMMENT 'httpå·¥å…·è°ƒç”¨åœ°å€',
  `config` longtext COMMENT 'JSONå‚æ•°schema/é…ç½®',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT 'æ˜¯å¦å¯ç”¨',
  `create_by` bigint DEFAULT NULL COMMENT 'åˆ›å»ºäººID',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å·¥å…·æ³¨å†Œè¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `workflow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT 'å·¥ä½œæµåç§°',
  `description` varchar(500) DEFAULT NULL COMMENT 'æè¿°',
  `definition` longtext COMMENT 'å·¥ä½œæµå®šä¹‰JSONï¼ˆèŠ‚ç‚¹/è¿žçº¿ï¼‰',
  `model` varchar(100) DEFAULT NULL COMMENT 'ä½¿ç”¨çš„æ¨¡åž‹ï¼ˆä¸ºç©ºèµ°AIæœåŠ¡é»˜è®¤ï¼‰',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT 'æ˜¯å¦å¯ç”¨',
  `create_by` bigint DEFAULT NULL COMMENT 'åˆ›å»ºäººID',
  `deleted` bigint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime DEFAULT NULL COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å·¥ä½œæµå®šä¹‰è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


CREATE TABLE `usage_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `stat_date` date NOT NULL COMMENT '统计日',
  `model_name` varchar(100) NOT NULL DEFAULT '(default)' COMMENT '模型名',
  `tokens` bigint NOT NULL DEFAULT '0' COMMENT 'token 消耗',
  `msg_count` bigint NOT NULL DEFAULT '0' COMMENT '回复条数',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date_model` (`user_id`,`stat_date`,`model_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日 token 用量聚合（派生数据，幂等重算）';

-- ───────────────────────── 种子数据 ─────────────────────────

-- 管理员账号：admin / 123456
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `status`, `deleted`)
VALUES (1, 'admin', '$2a$10$HcxBz3vUrhQKF3ZeU6mAduQPKbvvm5wLCcPdvuWWFLAwagBt0JBjm', '管理员', 1, 0);

INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `remark`, `deleted`)
VALUES (1, '超级管理员', 'ROLE_ADMIN', '系统管理员', 0);

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- 默认 LLM 模型（api_url/api_key 由管理员在系统-模型管理页配置）
INSERT INTO `chat_model` (`id`, `model_name`, `provider`, `api_url`, `api_key`, `enabled`, `deleted`)
VALUES (1, 'deepseek-chat', 'DeepSeek', '', '', 1, 0);

-- 每日用量聚合排程（00:05 重算最近 2 天）
INSERT INTO `sys_job` (`job_name`, `invoke_target`, `cron_expression`, `job_params`, `status`, `remark`, `deleted`)
VALUES ('每日用量聚合', 'usageDailyJob', '0 5 0 * * ?', '', 0, '聚合 chat_message.token_count 到 usage_daily', 0);
