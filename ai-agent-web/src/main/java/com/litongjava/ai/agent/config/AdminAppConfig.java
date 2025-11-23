package com.litongjava.ai.agent.config;

import com.litongjava.annotation.AConfiguration;
import com.litongjava.annotation.Initialization;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.handler.ApiChatAskHandler;
import com.litongjava.llm.handler.ApiChatHandler;
import com.litongjava.llm.handler.GeogebraChatHandler;
import com.litongjava.tio.boot.admin.config.TioAdminControllerConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminDbConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminEhCacheConfig;
import com.litongjava.tio.boot.admin.config.TioAdminHandlerConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminInterceptorConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminMongoDbConfiguration;
import com.litongjava.tio.boot.admin.config.TioAdminRedisDbConfiguration;
import com.litongjava.tio.boot.admin.handler.system.SystemFileTencentCosHandler;
import com.litongjava.tio.boot.server.TioBootServer;
import com.litongjava.tio.http.server.router.HttpRequestRouter;

@AConfiguration
public class AdminAppConfig {

  @Initialization
  public void config() {
    // 配置数据库相关
    new TioAdminDbConfiguration().config();
    new TioAdminEhCacheConfig().config();
    new TioAdminRedisDbConfiguration().config();
    new TioAdminMongoDbConfiguration().config();
    new TioAdminInterceptorConfiguration().config();
    new TioAdminHandlerConfiguration().config();

    // 获取 HTTP 请求路由器
    TioBootServer server = TioBootServer.me();
    HttpRequestRouter r = server.getRequestRouter();
    if (r != null) {
      // 获取文件处理器，并添加文件上传和获取 URL 的接口
      SystemFileTencentCosHandler systemUploadHandler = Aop.get(SystemFileTencentCosHandler.class);
      r.add("/api/system/file/upload", systemUploadHandler::upload);
      r.add("/api/system/file/url", systemUploadHandler::getUrl);

      ApiChatHandler apiChatHandler = Aop.get(ApiChatHandler.class);

      GeogebraChatHandler geogebraChatHandler = new GeogebraChatHandler();

      r.add("/api/v1/chat/recommend", apiChatHandler::recommend);
      r.add("/api/v1/chat/create", apiChatHandler::createSession);
      r.add("/api/v1/chat/list", apiChatHandler::listSession);
      r.add("/api/v1/chat/delete", apiChatHandler::deleteSession);
      r.add("/api/v1/chat/set/name", apiChatHandler::setSessionName);
      r.add("/api/v1/chat/like", apiChatHandler::like);
      r.add("/api/v1/chat/history", apiChatHandler::getChatHistory);
      r.add("/api/v1/chat/stop", apiChatHandler::stop);

      ApiChatAskHandler apiChatAskHandler = Aop.get(ApiChatAskHandler.class);
      r.add("/api/v1/chat/ask", apiChatAskHandler);

      r.add("/api/v1/geogebra/chat", geogebraChatHandler::chat);
    }

    // 配置控制器
    new TioAdminControllerConfiguration().config();
  }
}
