package com.litongjava.ai.agent.config;

import com.litongjava.ai.agent.handler.ApiChatHandler;
import com.litongjava.ai.agent.handler.GeogebraChatHandler;
import com.litongjava.tio.boot.server.TioBootServer;
import com.litongjava.tio.http.server.router.HttpRequestRouter;

public class AgentWebHandlerConfig {

  public void config() {

    // 获取 HTTP 请求路由器
    TioBootServer server = TioBootServer.me();
    HttpRequestRouter r = server.getRequestRouter();
    if (r != null) {
      ApiChatHandler apiChatHandler = new ApiChatHandler();

      r.add("/api/v1/chat/recommend", apiChatHandler::recommend);
      r.add("/api/v1/chat/create", apiChatHandler::createSession);
      r.add("/api/v1/chat/list", apiChatHandler::listSession);
      r.add("/api/v1/chat/delete", apiChatHandler::deleteSession);
      r.add("/api/v1/chat/set/name", apiChatHandler::setSessionName);
      r.add("/api/v1/chat/like", apiChatHandler::like);
      r.add("/api/v1/chat/history", apiChatHandler::getChatHistory);
      r.add("/api/v1/chat/stop", apiChatHandler::stop);

      GeogebraChatHandler geogebraChatHandler = new GeogebraChatHandler();
      r.add("/api/v1/geogebra/chat", geogebraChatHandler);
    }
  }
}
