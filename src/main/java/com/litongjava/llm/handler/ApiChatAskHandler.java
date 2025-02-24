package com.litongjava.llm.handler;

import java.util.List;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.service.LlmAiChatService;
import com.litongjava.llm.service.LlmChatSessionService;
import com.litongjava.llm.vo.ApiChatSendVo;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.openai.chat.ChatMessage;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.json.FastJson2Utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiChatAskHandler {
  public HttpResponse send(HttpRequest httpRequest) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    String bodyString = httpRequest.getBodyString();

    JSONObject reqVo = FastJson2Utils.parseObject(bodyString);

    String userId = TioRequestContext.getUserIdString();
    if (userId == null) {
      userId = reqVo.getString("user_id");
    }
    Long session_id = reqVo.getLong("session_id");
    if (session_id == null) {
      session_id = reqVo.getLong("chat_id");
      if (session_id == null) {
        response.setJson(RespBodyVo.fail("chat_id can not be empty"));
        return response;
      }
    }
    Long schoolId = reqVo.getLong("school_id");
    String type = reqVo.getString("type");
    boolean validateChatId = reqVo.getBooleanValue("validate_session_id", true);
    Boolean stream = reqVo.getBoolean("stream");
    Long appId = reqVo.getLong("app_id");
    String provider = reqVo.getString("provider");
    String model = reqVo.getString("model");
    JSONArray jsonArray = reqVo.getJSONArray("file_ids");
    Boolean rewrite = reqVo.getBoolean("rewrite");
    Long previous_question_id = reqVo.getLong("previous_question_id");
    Long previous_answer_id = reqVo.getLong("previous_answer_id");

    if (stream == null) {
      stream = true;
    }

    if (provider == null) {
      response.setJson(RespBodyVo.fail("provider can not be empty"));
      return response;
    } else {
      provider = provider.toLowerCase();
    }

    if (type == null) {
      type = "general";
    }

    Integer chatType = 0;
    try {
      chatType = reqVo.getInteger("chat_type");
    } catch (Exception e) {
      e.printStackTrace();
    }

    if (provider == null) {
      provider = "openai";
    }
    if (model == null) {
      model = "gpt-4o-mini";
    }

    JSONArray messages = reqVo.getJSONArray("messages");
    if (messages == null || messages.size() < 1) {
      response.setJson(RespBodyVo.fail("Messages array can not be empty"));
      return response;
    }

    ApiChatSendVo apiChatSendVo = new ApiChatSendVo();

    if (jsonArray != null) {
      try {
        List<Long> fileIds = jsonArray.toJavaList(Long.class);
        apiChatSendVo.setFile_ids(fileIds);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        response.setJson(RespBodyVo.fail("Fail to parse file_ids:" + e.getMessage()));
        return response;
      }
    }

    List<ChatMessage> messageList = messages.toJavaList(ChatMessage.class);

    apiChatSendVo.setProvider(provider).setModel(model).setType(type).setUser_id(userId)
        //
        .setSession_id(session_id).setSchool_id(schoolId)
        //
        .setApp_id(appId).setChat_type(chatType).setStream(stream).setMessages(messageList);

    if (rewrite != null) {
      apiChatSendVo.setRewrite(rewrite).setPrevious_question_id(previous_question_id).setPrevious_answer_id(previous_answer_id);
    }
    LlmChatSessionService llmChatSessionService = Aop.get(LlmChatSessionService.class);

    if (validateChatId) {
      boolean exists = llmChatSessionService.exists(session_id, userId);
      if (!exists) {
        log.info("seesion_id:{},userId:{}", session_id, userId);
        response.setJson(RespBodyVo.fail("Invalid chat"));
        return response;
      }
    }

    ChannelContext channelContext = null;
    if (stream) {
      channelContext = httpRequest.getChannelContext();
      // 设置sse请求头
      response.addServerSentEventsHeader();
      // 发送http响应包,告诉客户端保持连接
      Tio.bSend(channelContext, response);
      response.setSend(false);
    }

    RespBodyVo RespBodyVo = Aop.get(LlmAiChatService.class).index(channelContext, apiChatSendVo);
    if (!stream) {
      response.setJson(RespBodyVo);
    }
    return response;
  }
}
