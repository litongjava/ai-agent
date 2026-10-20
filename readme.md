# ai-agent

## 初始化数据库
```
create database defaultdb;
\c defaultdb;
CREATE EXTENSION IF NOT EXISTS vector;
```
初始化tio-boot-admin需要的表
https://tio-boot.cn/zh/tio-boot-admin/02.html
tio_boot_admin_system_users
tio_boot_admin_system_constants_config
tio_boot_admin_sa_token
tio_boot_admin_system_upload_file

agent需要的表
https://tio-boot.cn/zh/ai-agent/01.html
具体表略

用户相关表
https://tio-boot.cn/zh/authentication/03.html

接口统计
https://tio-boot.cn/zh/web/40.html

sys_http_request_statistics
https://tio-boot.cn/zh/web/41.html
sys_http_request_response_statistics

https://tio-boot.cn/zh/ai-agent/08.html