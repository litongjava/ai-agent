package com.litongjava.llm.handler;

import java.util.List;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.litongjava.chat.ChatMessageArgs;
import com.litongjava.chat.UniChatMessage;
import com.litongjava.gemini.GoogleGeminiModels;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.consts.ApiChatSendType;
import com.litongjava.llm.service.LlmAiChatService;
import com.litongjava.llm.service.LlmChatSessionService;
import com.litongjava.llm.vo.ApiChatSendVo;
import com.litongjava.model.body.RespBodyVo;
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
  private LlmAiChatService llmAiChatService = Aop.get(LlmAiChatService.class);

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
    if (schoolId == null) {
      schoolId = reqVo.getLong("schoolId");
    }
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
    String session_type = reqVo.getString("session_type");
    String session_name = reqVo.getString("session_name");
    String cmd = reqVo.getString("cmd");

    if (stream == null) {
      stream = true;
    }
    if (type == null) {
      type = "general";
    }

    if (ApiChatSendType.youtube.equals(type)) {
      if (provider == null) {
        provider = "google";
      }
      if (model == null) {
        model = GoogleGeminiModels.GEMINI_2_0_FLASH;
      }
    } else {
      if (provider == null) {
        provider = "openai";
      }
      if (model == null) {
        model = "gpt-4o-mini";
      }
    }

    Integer chatType = 0;
    try {
      chatType = reqVo.getInteger("chat_type");
    } catch (Exception e) {
      e.printStackTrace();
    }

    JSONArray messages = reqVo.getJSONArray("messages");
    JSONObject args = reqVo.getJSONObject("args");
    ApiChatSendVo apiChatSendVo = new ApiChatSendVo();
    if (args != null) {
      ChatMessageArgs javaObject = args.toJavaObject(ChatMessageArgs.class);
      javaObject.setType(type);
      apiChatSendVo.setArgs(javaObject);
    }

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

    if (messages != null) {
      List<UniChatMessage> messageList = messages.toJavaList(UniChatMessage.class);
      apiChatSendVo.setMessages(messageList);
    }

    apiChatSendVo.setProvider(provider).setModel(model).setType(type).setUser_id(userId)
        //
        .setSession_id(session_id).setSchool_id(schoolId)
        //
        .setApp_id(appId).setChat_type(chatType).setStream(stream).setCmd(cmd);

    if (rewrite != null) {
      apiChatSendVo.setRewrite(rewrite).setPrevious_question_id(previous_question_id).setPrevious_answer_id(previous_answer_id);
    }
    LlmChatSessionService llmChatSessionService = Aop.get(LlmChatSessionService.class);

    if (validateChatId) {
      boolean exists = llmChatSessionService.createIfNotExists(userId, session_id, session_type, session_name);
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
      // 发送http响应头,告诉客户端保持连接
      Tio.bSend(channelContext, response);
      response.setSend(false);
    }

    RespBodyVo RespBodyVo = llmAiChatService.index(channelContext, apiChatSendVo);
    if (!stream) {
      response.setJson(RespBodyVo);
    }
    return response;
  }
}
