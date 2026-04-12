package nexus.io.ai.agent.handler;

import lombok.extern.slf4j.Slf4j;
import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.api.ChatAskService;
import nexus.io.llm.service.LlmChatSessionService;
import nexus.io.llm.vo.ChatAskRequest;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.Tio;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.http.server.handler.HttpRequestHandler;
import nexus.io.tio.http.server.util.CORSUtils;
import nexus.io.tio.utils.hutool.StrUtil;
import nexus.io.tio.utils.json.JsonUtils;

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
    if(stream) {
      response.addServerSentEventsHeader();
      Tio.send(channelContext, response);
      response.setSend(false);
    }

    RespBodyVo RespBodyVo = chatAskService.index(channelContext, chatAskRequest);
    if (!stream) {
      response.setJson(RespBodyVo);
    }
    return response;
  }
}
