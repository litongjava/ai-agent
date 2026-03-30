package com.litongjava.ai.agent.config;

import com.litongjava.annotation.AConfiguration;
import com.litongjava.annotation.Initialization;
import com.litongjava.tio.boot.admin.config.TioAdminControllerConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminDbConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminEhCacheConfig;
import com.litongjava.tio.boot.admin.config.TioAdminHandlerConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminInterceptorConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminMongoDbConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminRedisDbConfiguration;

@AConfiguration
public class AgentWebAppConfig {

  @Initialization
  public void config() {
    // 配置数据库相关
    new TioAdminDbConfiguration().config();
    new TioAdminEhCacheConfig().config();
    new TioAdminRedisDbConfiguration().config();
    new TioAdminMongoDbConfiguration().config();
    new TioAdminInterceptorConfiguration().config();
    new TioAdminHandlerConfiguration().config();

    new AgentWebHandlerConfig().config();
    // 配置控制器
    new TioAdminControllerConfiguration().config();
  }
}
