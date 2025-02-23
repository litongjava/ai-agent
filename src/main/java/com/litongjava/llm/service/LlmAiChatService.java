package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;
import com.litongjava.db.TableResult;
import com.litongjava.db.activerecord.Row;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.can.ChatStreamCallCan;
import com.litongjava.llm.config.AiAgentContext;
import com.litongjava.llm.consts.AgentMessageType;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.dao.SchoolDictDao;
import com.litongjava.llm.utils.AgentBotQuestionUtils;
import com.litongjava.llm.vo.AiChatResponseVo;
import com.litongjava.llm.vo.ApiChatSendType;
import com.litongjava.llm.vo.ApiChatSendVo;
import com.litongjava.llm.vo.ChatParamVo;
import com.litongjava.llm.vo.SchoolDict;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.openai.chat.ChatMessage;
import com.litongjava.openai.chat.OpenAiChatRequestVo;
import com.litongjava.openai.chat.OpenAiChatResponseVo;
import com.litongjava.openai.client.OpenAiClient;
import com.litongjava.openai.constants.OpenAiModels;
import com.litongjava.template.PromptEngine;
import com.litongjava.tio.boot.admin.vo.UploadResultVo;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.sse.SsePacket;
import com.litongjava.tio.http.server.util.SseEmitter;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.json.JsonUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;
import com.litongjava.tio.utils.thread.TioThreadUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;

@Slf4j
public class LlmAiChatService {
  ChatOpenAiStreamCommonService chatStreamCommonService = Aop.get(ChatOpenAiStreamCommonService.class);
  LLmChatDispatcherService dispatcherService = Aop.get(LLmChatDispatcherService.class);

  public RespBodyVo index(ChannelContext channelContext, ApiChatSendVo apiSendVo) {

    /**
     * inputQestion 用户输入的问题
     * textQuestion 用户输入的问题和提示器
     */
    String inputQestion = apiSendVo.getMessages().get(0).getContent();
    apiSendVo.getMessages().remove(0);
    apiSendVo.setInput_quesiton(inputQestion);
    String textQuestion = null;
    if (ApiChatSendType.translator.equals(apiSendVo.getType())) {
      if (StrUtil.isNotBlank(inputQestion)) {
        textQuestion = PromptEngine.renderToString("translator_prompt.txt", Kv.by("data", inputQestion));
      } else {
        return RespBodyVo.fail("input question can not be empty");
      }
    } else {
      textQuestion = inputQestion;
    }
    boolean stream = apiSendVo.isStream();
    Long schoolId = apiSendVo.getSchool_id();
    String userId = apiSendVo.getUser_id();
    Long sessionId = apiSendVo.getSession_id();
    Long appId = apiSendVo.getApp_id();
    String type = apiSendVo.getType();
    List<Long> file_ids = apiSendVo.getFile_ids();

    if (textQuestion != null) {
      if (stream) {
        Kv kv = Kv.by("content", "- Think about your question: " + textQuestion + "\r\n");
        SsePacket packet = new SsePacket(AiChatEventName.progress, JsonUtils.toJson(kv));
        Tio.bSend(channelContext, packet);
      }
    }
    if (textQuestion.startsWith("__echo:")) {
      String[] split = textQuestion.split(":");
      if (stream) {
        SsePacket packet = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(Kv.by("content", "\r\n\r\n")));
        Tio.bSend(channelContext, packet);

        packet = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(Kv.by("content", split[1])));
        Tio.bSend(channelContext, packet);

        packet = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(Kv.by("content", "end")));
        Tio.bSend(channelContext, packet);

        SseEmitter.closeSeeConnection(channelContext);
      }
      return RespBodyVo.ok(new AiChatResponseVo(split[1]));
    }

    SchoolDict schoolDict = null;

    if (schoolId != null) {
      try {
        schoolDict = Aop.get(SchoolDictDao.class).getNameById(schoolId.longValue());
      } catch (Exception e) {
        e.printStackTrace();

        String error = e.getMessage();
        if (stream) {
          SsePacket ssePacket = new SsePacket(AiChatEventName.error, error.getBytes());
          Tio.bSend(channelContext, ssePacket);
          SseEmitter.closeSeeConnection(channelContext);
        }
        return RespBodyVo.fail(error);
      }
    }
    List<Row> histories = null;
    if (ApiChatSendType.general.equals(apiSendVo.getType())) {
      try {
        histories = Aop.get(LlmChatHistoryService.class).getHistory(sessionId);
      } catch (Exception e) {
        e.printStackTrace();
        String error = e.getMessage();
        if (stream) {
          SsePacket ssePacket = new SsePacket(AiChatEventName.error, error);
          Tio.bSend(channelContext, ssePacket);
          SseEmitter.closeSeeConnection(channelContext);
        }
        return RespBodyVo.fail(error);
      }
    }
    int size = 0;
    if (histories != null) {
      size = histories.size();
    }

    if (stream) {
      SsePacket ssePacket = new SsePacket(AiChatEventName.progress, ("The number of history records to be queried:" + size).getBytes());
      Tio.bSend(channelContext, ssePacket);
    }

    boolean isFirstQuestion = false;
    List<ChatMessage> historyMessage = new ArrayList<>();
    if (histories == null || size < 1) {
      isFirstQuestion = true;
    } else {
      for (Row record : histories) {
        String messageType = record.getStr("type");
        if (AgentMessageType.TEXT.equals(messageType)) {
          String role = record.getStr("role");
          String content = record.getStr("content");
          historyMessage.add(new ChatMessage(role, content));
        } else if (AgentMessageType.FILE.equals(messageType)) {
          String role = record.getStr("role");
          String content = record.getStr("content");

          String str = record.getStr("metadata");
          List<UploadResultVo> uploadVos = JsonUtils.parseArray(str, UploadResultVo.class);
          for (UploadResultVo uploadResult : uploadVos) {
            historyMessage.add(new ChatMessage(role, String.format("user upload %s conent is :%s", uploadResult.getName(), uploadResult.getContent())));
          }

          if (StrUtil.notBlank(content)) {
            historyMessage.add(new ChatMessage(role, content));
          }

        }
      }
    }

    AiChatResponseVo aiChatResponseVo = new AiChatResponseVo();
    // save file content to history
    ChatParamVo chatParamVo = new ChatParamVo();
    // save to the user question to db
    long questionId = SnowflakeIdUtils.id();
    if (StrUtil.isNotEmpty(inputQestion)) {
      TableResult<Kv> ts = null;
      List<UploadResultVo> fileInfo = null;
      if (file_ids != null) {
        fileInfo = Aop.get(ChatUploadService.class).getFileBasicInfoByIds(file_ids);
        chatParamVo.setUploadFiles(fileInfo);
        ts = Aop.get(LlmChatHistoryService.class).saveUser(questionId, sessionId, inputQestion, fileInfo);
      } else {
        ts = Aop.get(LlmChatHistoryService.class).saveUser(questionId, sessionId, inputQestion);
      }

      if (ts.getCode() != 1) {
        log.error("Failed to save message:{}", ts.toString());
      } else {
        if (stream) {
          Kv kv = Kv.by("question_id", questionId);
          SsePacket packet = new SsePacket(AiChatEventName.message_id, JsonUtils.toJson(kv));
          Tio.bSend(channelContext, packet);
        }
        aiChatResponseVo.setQuesitonId(questionId);
        if (fileInfo != null) {
          aiChatResponseVo.setUploadFiles(fileInfo);
        }
      }

    }

    if (StrUtil.isNotEmpty(textQuestion)) {
      StringBuffer stringBuffer = new StringBuffer();

      stringBuffer.append("app env:").append(EnvUtils.getStr("app.env")).append("\n")
          //
          .append("userId:").append(userId).append("\n")//
          .append("schooL id:").append(schoolId).append("\n");
      if (schoolDict != null) {
        stringBuffer.append("schooL name:").append(schoolDict.getFullName()).append("\n");
      }
      //
      stringBuffer.append("user question:").append(textQuestion).append("\n")
          //
          .append("type:").append(type);
      if (appId != null) {
        stringBuffer.append("app id:").append(appId);
      }

      log.info("question:{}", stringBuffer.toString());
      RunningNotificationService notification = AiAgentContext.me().getNotification();
      if (notification != null) {
        notification.sendQuestion(stringBuffer.toString());
      }

      if (!EnvUtils.isDev()) {
        String thatTextQuestion = textQuestion;
        TioThreadUtils.submit(() -> {
          AgentBotQuestionUtils.send(stringBuffer.toString());
          if (stream) {
            SsePacket packet = new SsePacket(AiChatEventName.progress, "send message to lark");
            Tio.send(channelContext, packet);
          }
          // save to db
          Aop.get(UserAskQuesitonService.class).save(thatTextQuestion);
        });
      }
    }
    if (size > 20) {
      String message = "Dear user, your conversation count has exceeded the maximum length for multiple rounds of conversation. "
          //
          + "Please start a new session. Your new question might be:" + textQuestion;

      long answerId = SnowflakeIdUtils.id();
      aiChatResponseVo.setAnswerId(answerId);

      Aop.get(LlmChatHistoryService.class).saveAssistant(answerId, sessionId, message);
      Kv kv = Kv.by("answer_id", answerId);
      if (stream) {
        SsePacket ssePacket = new SsePacket(AiChatEventName.progress, JsonUtils.toJson(Kv.by("content", message)));
        Tio.bSend(channelContext, ssePacket);
        SsePacket packet = new SsePacket(AiChatEventName.message_id, JsonUtils.toJson(kv));
        Tio.send(channelContext, packet);
        SseEmitter.closeSeeConnection(channelContext);
      }
      aiChatResponseVo.setContent(message);
      return RespBodyVo.ok(message);
    }
    if (apiSendVo.getType().equals(ApiChatSendType.general)) {
      if (isFirstQuestion && textQuestion != null) {
        if (schoolDict != null) {
          textQuestion += " at " + schoolDict.getFullName();
        }
      }
    }

    chatParamVo.setFirstQuestion(isFirstQuestion).setTextQuestion(textQuestion)
        //
        .setHistory(historyMessage).setChannelContext(channelContext);

    if (textQuestion != null && textQuestion.startsWith("4o:")) {
      if (stream) {
        SsePacket packet = new SsePacket(AiChatEventName.progress, "The user specifies that the gpt4o model is used for message processing");
        Tio.bSend(channelContext, packet);
      }
      String answer = processMessageByChatModel(apiSendVo, channelContext);
      aiChatResponseVo.setContent(answer);
      return RespBodyVo.ok(aiChatResponseVo);

    } else {
      if (textQuestion != null) {
        if (apiSendVo.isRewrite()) {
          textQuestion = Aop.get(LlmRewriteQuestionService.class).rewrite(textQuestion, historyMessage);
          log.info("rewrite question:{}", textQuestion);

          if (stream && channelContext != null) {
            SsePacket packet = new SsePacket(AiChatEventName.question, textQuestion);
            Tio.bSend(channelContext, packet);
            Kv kv = Kv.by("content", "- Understand your intention: " + textQuestion + "\r\n");
            packet = new SsePacket(AiChatEventName.progress, JsonUtils.toJson(kv));
            Tio.bSend(channelContext, packet);
          }
          aiChatResponseVo.setRewrite(textQuestion);
          chatParamVo.setRewriteQuestion(textQuestion);
        }
      }

      dispatcherService.predict(apiSendVo, chatParamVo, aiChatResponseVo);
      return RespBodyVo.ok(aiChatResponseVo);
    }

  }

  public String processMessageByChatModel(ApiChatSendVo vo, ChannelContext channelContext) {
    boolean stream = vo.isStream();
    Long sessionId = vo.getSession_id();
    long start = System.currentTimeMillis();
    // 添加文本
    List<ChatMessage> messages = vo.getMessages();
    messages.add(new ChatMessage("user", vo.getInput_quesiton()));

    OpenAiChatRequestVo chatRequestVo = new OpenAiChatRequestVo().setModel(OpenAiModels.GPT_4O_MINI).setChatMessages(messages);

    if (stream) {
      Kv kv = Kv.by("content", "- Reply to your question.\r\n\r\n");
      SsePacket packet = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(kv));
      Tio.bSend(channelContext, packet);

      chatRequestVo.setStream(true);
      Callback callback = chatStreamCommonService.getCallback(channelContext, sessionId, start);
      Call call = OpenAiClient.chatCompletions(chatRequestVo, callback);
      log.info("add call:{}", sessionId);
      ChatStreamCallCan.put(sessionId, call);
      return null;

    } else {
      OpenAiChatResponseVo chatCompletions = OpenAiClient.chatCompletions(chatRequestVo);
      String content = chatCompletions.getChoices().get(0).getMessage().getContent();
      return content;
    }

  }
}