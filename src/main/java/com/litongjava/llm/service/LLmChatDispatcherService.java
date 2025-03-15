package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.jfinal.kit.Kv;
import com.litongjava.consts.AiModelNames;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.gemini.GeminiChatRequestVo;
import com.litongjava.gemini.GeminiClient;
import com.litongjava.gemini.GeminiContentVo;
import com.litongjava.gemini.GeminiFileDataVo;
import com.litongjava.gemini.GeminiPartVo;
import com.litongjava.gemini.GeminiSystemInstructionVo;
import com.litongjava.gemini.GoogleGeminiModels;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.kit.PgObjectUtils;
import com.litongjava.llm.callback.ChatGeminiStreamCommonCallback;
import com.litongjava.llm.callback.ChatOpenAiStreamCommonCallback;
import com.litongjava.llm.can.ChatStreamCallCan;
import com.litongjava.llm.config.AiAgentContext;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.consts.ApiChatSendProvider;
import com.litongjava.llm.consts.ApiChatSendType;
import com.litongjava.llm.vo.AiChatResponseVo;
import com.litongjava.llm.vo.ApiChatSendVo;
import com.litongjava.llm.vo.ChatParamVo;
import com.litongjava.openai.chat.ChatMessage;
import com.litongjava.openai.chat.ChatSendArgs;
import com.litongjava.openai.chat.OpenAiChatRequestVo;
import com.litongjava.openai.chat.OpenAiChatResponseVo;
import com.litongjava.openai.client.OpenAiClient;
import com.litongjava.openai.constants.OpenAiModels;
import com.litongjava.siliconflow.SiliconFlowConsts;
import com.litongjava.siliconflow.SiliconFlowModels;
import com.litongjava.tio.boot.admin.vo.UploadResultVo;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.sse.SsePacket;
import com.litongjava.tio.utils.Threads;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.json.JsonUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;
import com.litongjava.tio.utils.thread.TioThreadUtils;
import com.litongjava.volcengine.VolcEngineConst;
import com.litongjava.volcengine.VolcEngineModels;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;

@Slf4j
public class LLmChatDispatcherService {

  /**
   * 使用模型处理消息
   *
   * @param schoolId        学校ID
   * @param sessionId       会话ID
   * @param chatMessages    消息对象
   * @param isFirstQuestion 是否为首次提问
   * @param history  关联消息
   * @param stream          是否使用流式响应
   * @param channelContext  通道上下文
   * @param aiChatResponseVo 
   * @return 响应对象
   */
  public AiChatResponseVo predict(ApiChatSendVo apiSendVo, ChatParamVo paramVo, AiChatResponseVo aiChatResponseVo) {
    String provider = apiSendVo.getProvider();
    Boolean stream = apiSendVo.isStream();
    String type = apiSendVo.getType();
    ChannelContext channelContext = paramVo.getChannelContext();
    List<ChatMessage> history = paramVo.getHistory();
    Long sessionId = apiSendVo.getSession_id();
    String rewrite_quesiton = paramVo.getRewriteQuestion();
    String textQuestion = paramVo.getTextQuestion();
    List<UploadResultVo> uploadFiles = paramVo.getUploadFiles();
    String systemPrompt = paramVo.getSystemPrompt();
    ChatSendArgs args = apiSendVo.getArgs();
    if (rewrite_quesiton != null) {
      textQuestion = rewrite_quesiton;
    }

    // 发送进度
    if (stream && channelContext != null) {
      Kv kv = Kv.by("content", "- " + provider + "\r\n");
      SsePacket packet = new SsePacket(AiChatEventName.progress, JsonUtils.toJson(kv));
      Tio.bSend(channelContext, packet);
    }
    //添加系统消息
    if (systemPrompt != null) {
      history.add(new ChatMessage("system", systemPrompt));
    }

    // 添加用户问题
    if (uploadFiles != null) {
      for (UploadResultVo uploadResultVo : uploadFiles) {
        history.add(new ChatMessage("user", "upload a " + uploadResultVo.getName() + " content is:" + uploadResultVo.getContent()));
      }
    }

    if (ApiChatSendType.youtube.equals(type)) {
      if (args != null && args.getUrl() != null) {
        history.add(new ChatMessage("user", textQuestion, args));
      } else {
        history.add(new ChatMessage("user", textQuestion));
      }
    } else {
      if (StrUtil.isNotBlank(textQuestion)) {
        history.add(new ChatMessage("user", textQuestion));
      }
    }

    apiSendVo.setMessages(history);
    long answerId = SnowflakeIdUtils.id();
    if (stream) {
      //SsePacket packet = new SsePacket(AiChatEventName.input, JsonUtils.toJson(history));
      //Tio.bSend(channelContext, packet);
      if (ApiChatSendType.compare.equals(type)) {
        return multiModel(channelContext, apiSendVo, answerId);
      } else {
        return singleModel(channelContext, apiSendVo, answerId, textQuestion);
      }
    } else {
      if (provider.equals(ApiChatSendProvider.SILICONFLOW)) {

      } else {
        OpenAiChatRequestVo chatRequestVo = new OpenAiChatRequestVo().setModel(OpenAiModels.GPT_4O_MINI)
            //
            .setChatMessages(history);
        OpenAiChatResponseVo chatCompletions = OpenAiClient.chatCompletions(chatRequestVo);
        List<String> citations = chatCompletions.getCitations();
        String answerContent = chatCompletions.getChoices().get(0).getMessage().getContent();
        Aop.get(LlmChatHistoryService.class).saveAssistant(answerId, sessionId, answerContent);
        aiChatResponseVo.setContent(answerContent);
        aiChatResponseVo.setAnswerId(answerId);
        aiChatResponseVo.setCition(citations);
        return aiChatResponseVo;
      }

    }
    return aiChatResponseVo;
  }

  /**
   * @param channelContext
   * @param provider
   * @param model
   * @param messages
   * @param sessionId
   * @param answerId
   * @return
   */
  private AiChatResponseVo multiModel(ChannelContext channelContext, ApiChatSendVo apiSendVo, long answerId) {
    CountDownLatch latch = new CountDownLatch(3);
    List<ChatMessage> messages = apiSendVo.getMessages();

    long start = System.currentTimeMillis();
    List<Call> calls = new ArrayList<Call>();
    //deepseek v3
    Threads.getTioExecutor().execute(() -> {
      try {
        OpenAiChatRequestVo chatRequestVo = genOpenAiRequestVo(VolcEngineModels.DEEPSEEK_V3_241226, messages, answerId);
        ChatOpenAiStreamCommonCallback callback = new ChatOpenAiStreamCommonCallback(channelContext, apiSendVo, answerId, start, latch);
        String apiKey = EnvUtils.getStr("VOLCENGINE_API_KEY");
        Call call = OpenAiClient.chatCompletions(VolcEngineConst.BASE_URL, apiKey, chatRequestVo, callback);
        calls.add(call);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }

    });

    //gpt 4o
    Threads.getTioExecutor().execute(() -> {
      try {
        long id = SnowflakeIdUtils.id();
        OpenAiChatRequestVo genOpenAiRequestVo = genOpenAiRequestVo(OpenAiModels.GPT_4O, messages, id);
        ChatOpenAiStreamCommonCallback openAiCallback = new ChatOpenAiStreamCommonCallback(channelContext, apiSendVo, id, start, latch);
        Call openAicall = OpenAiClient.chatCompletions(genOpenAiRequestVo, openAiCallback);
        calls.add(openAicall);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }

    });

    Threads.getTioExecutor().execute(() -> {
      try {
        long id = SnowflakeIdUtils.id();
        GeminiChatRequestVo geminiChatRequestVo = genGeminiRequestVo(messages, id);
        ChatGeminiStreamCommonCallback geminiCallback = new ChatGeminiStreamCommonCallback(channelContext, apiSendVo, id, start, latch);
        Call geminiCall = GeminiClient.stream(GoogleGeminiModels.GEMINI_2_0_FLASH_EXP, geminiChatRequestVo, geminiCallback);
        calls.add(geminiCall);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }

    });

    ChatStreamCallCan.put(apiSendVo.getSession_id(), calls);
    return null;

  }

  private AiChatResponseVo singleModel(ChannelContext channelContext, ApiChatSendVo apiChatSendVo, long answerId, String textQuestion) {
    Long sessionId = apiChatSendVo.getSession_id();
    String provider = apiChatSendVo.getProvider();
    String model = apiChatSendVo.getModel();
    String type = apiChatSendVo.getType();
    ChatSendArgs args = apiChatSendVo.getArgs();
    List<ChatMessage> messages = apiChatSendVo.getMessages();
    if (provider.equals(ApiChatSendProvider.SILICONFLOW)) {
      if (AiModelNames.DEEPSEEK_R1.equals(model)) {
        model = SiliconFlowModels.DEEPSEEK_R1;
      } else if (AiModelNames.DEEPSEEK_V3.equals(model)) {
        model = SiliconFlowModels.DEEPSEEK_V3;
      }

      OpenAiChatRequestVo chatRequestVo = genOpenAiRequestVo(model, apiChatSendVo.getMessages(), answerId);
      Threads.getTioExecutor().execute(() -> {
        try {
          long start = System.currentTimeMillis();

          ChatOpenAiStreamCommonCallback callback = new ChatOpenAiStreamCommonCallback(channelContext, apiChatSendVo, answerId, start);
          String apiKey = EnvUtils.getStr("SILICONFLOW_API_KEY");
          Call call = OpenAiClient.chatCompletions(SiliconFlowConsts.SELICONFLOW_API_BASE, apiKey, chatRequestVo, callback);
          ChatStreamCallCan.put(sessionId, call);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }

      });
      return null;

    } else if (provider.equals(ApiChatSendProvider.VOLCENGINE)) {
      if (AiModelNames.DEEPSEEK_R1.equals(model)) {
        model = VolcEngineModels.DEEPSEEK_R1_250120;
      } else if (AiModelNames.DEEPSEEK_V3.equals(model)) {
        model = VolcEngineModels.DEEPSEEK_V3_241226;
      }
      OpenAiChatRequestVo chatRequestVo = genOpenAiRequestVo(model, messages, answerId);
      Threads.getTioExecutor().execute(() -> {
        try {
          long start = System.currentTimeMillis();
          ChatOpenAiStreamCommonCallback callback = new ChatOpenAiStreamCommonCallback(channelContext, apiChatSendVo, answerId, start);
          String apiKey = EnvUtils.getStr("VOLCENGINE_API_KEY");
          Call call = OpenAiClient.chatCompletions(VolcEngineConst.BASE_URL, apiKey, chatRequestVo, callback);
          ChatStreamCallCan.put(sessionId, call);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }
      });
      return null;
    } else if (provider.equals(ApiChatSendProvider.GOOGLE)) {
      if (ApiChatSendType.youtube.equals(type)) {
        if (channelContext != null) {
          String message = null;
          if (args != null && args.getUrl() != null) {
            String url = args.getUrl();
            message = "First, let me download the YouTube video. It will take a few minutes " + url + ".";
          } else {
            message = "First, let me review the YouTube video. It will take a few minutes .";
          }

          Kv by = Kv.by("content", message);
          SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
          Tio.send(channelContext, ssePacket);
        }
        Threads.getTioExecutor().execute(() -> {
          try {
            long start = System.currentTimeMillis();
            GeminiChatRequestVo geminiChatRequestVo = genGeminiRequestVo(messages, answerId);
            ChatGeminiStreamCommonCallback geminiCallback = new ChatGeminiStreamCommonCallback(channelContext, apiChatSendVo, answerId, start);
            Call geminiCall = GeminiClient.stream(apiChatSendVo.getModel(), geminiChatRequestVo, geminiCallback);
            ChatStreamCallCan.put(sessionId, geminiCall);
          } catch (Exception e) {
            log.error(e.getMessage(), e);
          }
        });

        return null;
      } else {
        Threads.getTioExecutor().execute(() -> {
          try {
            long start = System.currentTimeMillis();
            GeminiChatRequestVo geminiChatRequestVo = genGeminiRequestVo(messages, answerId);
            ChatGeminiStreamCommonCallback geminiCallback = new ChatGeminiStreamCommonCallback(channelContext, apiChatSendVo, answerId, start);
            Call geminiCall = GeminiClient.stream(apiChatSendVo.getModel(), geminiChatRequestVo, geminiCallback);
            ChatStreamCallCan.put(sessionId, geminiCall);
          } catch (Exception e) {
            log.error(e.getMessage(), e);
          }
        });
        return null;
      }

    }
    //
    else {
      OpenAiChatRequestVo chatRequestVo = genOpenAiRequestVo(model, messages, answerId);

      Threads.getTioExecutor().execute(() -> {
        try {
          long start = System.currentTimeMillis();
          ChatOpenAiStreamCommonCallback callback = new ChatOpenAiStreamCommonCallback(channelContext, apiChatSendVo, answerId, start);
          Call call = OpenAiClient.chatCompletions(chatRequestVo, callback);
          ChatStreamCallCan.put(sessionId, call);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }
      });

      return null;

    }
  }

  private OpenAiChatRequestVo genOpenAiRequestVo(String model, List<ChatMessage> messages, Long answerId) {
    OpenAiChatRequestVo chatRequestVo = new OpenAiChatRequestVo().setModel(model)
        //
        .setChatMessages(messages);

    chatRequestVo.setStream(true);
    String requestJson = JsonUtils.toSkipNullJson(chatRequestVo);
    RunningNotificationService notification = AiAgentContext.me().getNotification();
    if (notification != null) {
      notification.sendPredict(requestJson);
    }
    // log.info("chatRequestVo:{}", requestJson);
    // save to database
    TioThreadUtils.execute(() -> {
      String sanitizedJson = requestJson.replaceAll("\u0000", "");
      Db.save(AgentTableNames.llm_chat_completion_input, Row.by("id", answerId).set("request", PgObjectUtils.json(sanitizedJson)));
    });
    return chatRequestVo;
  }

  private GeminiChatRequestVo genGeminiRequestVo(List<ChatMessage> messages, long answerId) {
    GeminiChatRequestVo geminiChatRequestVo = new GeminiChatRequestVo();

    List<GeminiContentVo> contents = new ArrayList<>(messages.size());
    for (ChatMessage chatMessage : messages) {
      String role = chatMessage.getRole();
      String content = chatMessage.getContent();
      ChatSendArgs args = chatMessage.getArgs();

      if (args != null) {
        List<GeminiPartVo> parts = new ArrayList<>();
        if (args.getUrl() != null) {
          String url = args.getUrl();
          GeminiFileDataVo geminiFileDataVo = new GeminiFileDataVo("video/*", url);
          GeminiPartVo videoPart = new GeminiPartVo(geminiFileDataVo);
          parts.add(videoPart);
        }

        GeminiPartVo questionPart = new GeminiPartVo(content);
        parts.add(questionPart);
        GeminiContentVo vo = new GeminiContentVo("user", parts);
        contents.add(vo);
      } else {

        if (role.equals("assistant")) {
          role = "model";
        } else if (role.equals("system")) {
          GeminiPartVo part = new GeminiPartVo(content);
          GeminiSystemInstructionVo geminiSystemInstructionVo = new GeminiSystemInstructionVo(part);
          geminiChatRequestVo.setSystem_instruction(geminiSystemInstructionVo);
          continue;
        }
        GeminiPartVo part = new GeminiPartVo(content);
        GeminiContentVo vo = new GeminiContentVo(role, Collections.singletonList(part));
        contents.add(vo);
      }

    }

    geminiChatRequestVo.setContents(contents);

    String requestJson = JsonUtils.toSkipNullJson(geminiChatRequestVo);
    RunningNotificationService notification = AiAgentContext.me().getNotification();
    if (notification != null) {
      notification.sendPredict(requestJson);
    }
    // log.info("chatRequestVo:{}", requestJson);
    // save to database
    TioThreadUtils.execute(() -> {
      Db.save(AgentTableNames.llm_chat_completion_input, Row.by("id", answerId).set("request", PgObjectUtils.json(requestJson)));
    });
    return geminiChatRequestVo;

  }

}