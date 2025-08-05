package com.litongjava.llm.handler;

import java.util.List;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.litongjava.chat.ChatMessageArgs;
import com.litongjava.chat.UniChatMessage;
import com.litongjava.gemini.GoogleModels;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.consts.ApiChatAskType;
import com.litongjava.llm.service.LlmChatAskService;
import com.litongjava.llm.service.LlmChatSessionService;
import com.litongjava.llm.vo.ChatAskVo;
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
  private LlmChatAskService llmAiChatService = Aop.get(LlmChatAskService.class);

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
    
    boolean validateChatId = reqVo.getBooleanValue("validate_session_id", true);
    boolean history_enabled = reqVo.getBooleanValue("history_enabled", true);

    if (stream == null) {
      stream = true;
    }
    if (type == null) {
      type = "general";
    }

    if (ApiChatAskType.youtube.equals(type)) {
      if (provider == null) {
        provider = "google";
      }
      if (model == null) {
        model = GoogleModels.GEMINI_2_0_FLASH;
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
    ChatAskVo chatAskVo = new ChatAskVo();
    if (args != null) {
      ChatMessageArgs javaObject = args.toJavaObject(ChatMessageArgs.class);
      javaObject.setType(type);
      chatAskVo.setArgs(javaObject);
    }

    if (jsonArray != null) {
      try {
        List<Long> fileIds = jsonArray.toJavaList(Long.class);
        chatAskVo.setFile_ids(fileIds);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        response.setJson(RespBodyVo.fail("Fail to parse file_ids:" + e.getMessage()));
        return response;
      }
    }

    if (messages != null) {
      List<UniChatMessage> messageList = messages.toJavaList(UniChatMessage.class);
      chatAskVo.setMessages(messageList);
    }

    chatAskVo.setProvider(provider).setModel(model).setType(type).setUser_id(userId)
        //
        .setSession_id(session_id).setSchool_id(schoolId).setHistory_enabled(history_enabled)
        //
        .setApp_id(appId).setChat_type(chatType).setStream(stream).setCmd(cmd);

    if (rewrite != null) {
      chatAskVo.setRe_generate(rewrite).setPrevious_question_id(previous_question_id)
          .setPrevious_answer_id(previous_answer_id);
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

    RespBodyVo RespBodyVo = llmAiChatService.index(channelContext, chatAskVo);
    if (!stream) {
      response.setJson(RespBodyVo);
    }
    return response;
  }
}
