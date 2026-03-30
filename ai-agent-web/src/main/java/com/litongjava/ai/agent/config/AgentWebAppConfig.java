package com.litongjava.ai.agent.config;

import com.litongjava.ai.agent.handler.ApiChatAskHandler;
import com.litongjava.context.BootConfiguration;
import com.litongjava.llm.service.LlmChatAskService;
import com.litongjava.tio.boot.admin.config.TioAdminControllerConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminDbConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminEhCacheConfig;
import com.litongjava.tio.boot.admin.config.TioAdminHandlerConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminInterceptorConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminMongoDbConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminRedisDbConfiguration;
import com.litongjava.tio.boot.server.TioBootServer;
import com.litongjava.tio.http.server.router.HttpRequestRouter;

public class AgentWebAppConfig implements BootConfiguration {

  public void config() {
    // 配置数据库相关
    new TioAdminDbConfiguration().config();
    new TioAdminEhCacheConfig().config();
    new TioAdminRedisDbConfiguration().config();
    new TioAdminMongoDbConfiguration().config();
    new TioAdminInterceptorConfiguration().config();
    new TioAdminHandlerConfiguration().config();
    TioBootServer server = TioBootServer.me();
    HttpRequestRouter r = server.getRequestRouter();
    if (r != null) {
      r.add("/api/v1/chat/ask", new ApiChatAskHandler(new LlmChatAskService()));
    }

    new AgentWebHandlerConfig().config();
    new AgentControllerConfiguration().config();
    // 配置控制器
    new TioAdminControllerConfiguration().config();
  }
}
