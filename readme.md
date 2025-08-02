# ai-agent

## 初始化数据库
```
create database defaultdb;
\c defaultdb;
CREATE EXTENSION IF NOT EXISTS vector;
```
初始化tio-boot-admin需要的表
https://www.tio-boot.com/zh/70_tio-boot-admin/02.html
tio_boot_admin_system_users
tio_boot_admin_system_constants_config
tio_boot_admin_sa_token
tio_boot_admin_system_upload_file

agent需要的表
https://www.tio-boot.com/zh/61_ai_agent/01.html
具体表略

用户相关表
https://www.tio-boot.com/zh/13_%E8%AE%A4%E8%AF%81/03.html

接口统计
https://www.tio-boot.com/zh/06_web/28.html

sys_http_request_statistics
https://www.tio-boot.com/zh/06_web/29.html
sys_http_request_response_statistics

drop table if exists chat_upload_file;
CREATE TABLE chat_upload_file (
  id BIGINT primary key,
  md5 VARCHAR(32) NOT NULL,
  name VARCHAR(1024) NOT NULL,
  content text,
  creator VARCHAR(64) DEFAULT '',
  create_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0,
  tenant_id BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX "idex_chat_upload_file_md5" ON "chat_upload_file" ("md5");



