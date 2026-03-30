package com.litongjava.llm.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.api.ChatAskService;
import com.litongjava.llm.service.LlmChatSessionService;
import com.litongjava.llm.vo.ChatAskRequest;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiChatAskHandler implements HttpRequestHandler {
  private ChatAskService chatAskService = null;
  
  private LlmChatSessionService llmChatSessionService = Aop.get(LlmChatSessionService.class);

  public ApiChatAskHandler(ChatAskService chatAskService) {
    this.chatAskService = chatAskService;
  }

  @Override
  public HttpResponse handle(HttpRequest httpRequest) throws Exception {
    ChannelContext channelContext = httpRequest.getChannelContext();
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    String bodyString = httpRequest.getBodyString();
    if (StrUtil.isBlank(bodyString)) {
      return response.fail(RespBodyVo.fail("body can not be empty"));
    }

    ChatAskRequest chatAskRequest = JsonUtils.parse(bodyString, ChatAskRequest.class);

    String userId = TioRequestContext.getUserIdString();
    if (userId == null) {
      userId = chatAskRequest.getUser_id();
    }
    Long session_id = chatAskRequest.getSession_id();

    if (session_id == null) {
      response.setJson(RespBodyVo.fail("session_id can not be empty"));
      return response;
    }

    boolean exists = llmChatSessionService.exists(userId, session_id);
    if (!exists) {
      response.setJson(RespBodyVo.fail("invalid session id"));
      return response;
    }

    boolean stream = chatAskRequest.isStream();

    RespBodyVo RespBodyVo = chatAskService.index(channelContext, chatAskRequest);
    if (!stream) {
      response.setJson(RespBodyVo);
    }
    return response;
  }
}
