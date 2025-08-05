package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.jfinal.kit.Kv;
import com.litongjava.chat.ChatMessageArgs;
import com.litongjava.chat.UniChatMessage;
import com.litongjava.consts.ModelNames;
import com.litongjava.consts.ModelPlatformName;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.gemini.GeminiChatRequestVo;
import com.litongjava.gemini.GeminiClient;
import com.litongjava.gemini.GeminiContentVo;
import com.litongjava.gemini.GeminiFileDataVo;
import com.litongjava.gemini.GeminiPartVo;
import com.litongjava.gemini.GeminiSystemInstructionVo;
import com.litongjava.gemini.GoogleModels;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.kit.PgObjectUtils;
import com.litongjava.llm.callback.ChatGeminiStreamCommonCallback;
import com.litongjava.llm.callback.ChatOpenAiStreamCommonCallback;
import com.litongjava.llm.can.ChatStreamCallCan;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.consts.ApiChatAskType;
import com.litongjava.llm.vo.AiChatResponseVo;
import com.litongjava.llm.vo.ChatAskVo;
import com.litongjava.llm.vo.ChatParamVo;
import com.litongjava.openai.chat.OpenAiChatRequestVo;
import com.litongjava.openai.chat.OpenAiChatResponseVo;
import com.litongjava.openai.client.OpenAiClient;
import com.litongjava.openai.consts.OpenAiModels;
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

  private AgentNotificationService agentNotificationService = Aop.get(AgentNotificationService.class);

  /**
   * 使用模型处理消息
   *
   * @param schoolId         学校ID
   * @param sessionId        会话ID
   * @param chatMessages     消息对象
   * @param isFirstQuestion  是否为首次提问
   * @param history          关联消息
   * @param stream           是否使用流式响应
   * @param channelContext   通道上下文
   * @param aiChatResponseVo
   * @return 响应对象
   */
  public AiChatResponseVo predict(ChatAskVo apiSendVo, ChatParamVo paramVo, AiChatResponseVo aiChatResponseVo) {
    String provider = apiSendVo.getProvider();
    Boolean stream = apiSendVo.isStream();
    String type = apiSendVo.getType();
    ChannelContext channelContext = paramVo.getChannelContext();
    List<UniChatMessage> history = paramVo.getHistory();
    Long sessionId = apiSendVo.getSession_id();
    String rewrite_quesiton = paramVo.getRewriteQuestion();
    String textQuestion = paramVo.getTextQuestion();
    List<UploadResultVo> uploadFiles = paramVo.getUploadFiles();
    String systemPrompt = paramVo.getSystemPrompt();
    if (rewrite_quesiton != null) {
      textQuestion = rewrite_quesiton;
    }

    // 发送进度
    if (stream && channelContext != null) {
      Kv kv = Kv.by("content", "- " + provider + "\r\n");
      SsePacket packet = new SsePacket(AiChatEventName.progress, JsonUtils.toJson(kv));
      Tio.bSend(channelContext, packet);
    }
    // 添加系统消息
    if (StrUtil.isNotBlank(systemPrompt)) {
      history.add(0, new UniChatMessage("system", systemPrompt));
    }

    // 添加用户问题
    if (uploadFiles != null) {
      for (UploadResultVo uploadResultVo : uploadFiles) {
        history.add(new UniChatMessage("user", uploadResultVo.getName() + " " + uploadResultVo.getContent()));
      }
    }

    if (StrUtil.isNotBlank(textQuestion)) {
      history.add(new UniChatMessage("user", textQuestion));
    }

    // if (ApiChatSendType.youtube.equals(type)) {
    // if (args != null && args.getUrl() != null) {
    // history.add(new UniChatMessage("user", textQuestion, args));
    // } else {
    // history.add(new UniChatMessage("user", textQuestion));
    // }
    // } else {
    // if (StrUtil.isNotBlank(textQuestion)) {
    // history.add(new UniChatMessage("user", textQuestion));
    // }
    // }

    apiSendVo.setMessages(history);
    long answerId = SnowflakeIdUtils.id();
    if (stream) {
      // SsePacket packet = new SsePacket(AiChatEventName.input,
      // JsonUtils.toJson(history));
      // Tio.bSend(channelContext, packet);
      if (ApiChatAskType.compare.equals(type)) {
        return multiModel(channelContext, apiSendVo, answerId, textQuestion);
      } else {
        return singleModel(channelContext, apiSendVo, answerId, textQuestion);
      }
    } else {
      if (ModelPlatformName.SILICONFLOW.equals(provider)) {

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
  private AiChatResponseVo multiModel(ChannelContext channelContext, ChatAskVo apiSendVo, long answerId,
      String textQuesiton) {
    CountDownLatch latch = new CountDownLatch(3);
    List<UniChatMessage> messages = apiSendVo.getMessages();

    long start = System.currentTimeMillis();
    List<Call> calls = new ArrayList<Call>();
    // deepseek v3
    Threads.getTioExecutor().execute(() -> {
      try {
        OpenAiChatRequestVo chatRequestVo = genOpenAiRequestVo(VolcEngineModels.DEEPSEEK_V3_250324, messages, answerId);
        ChatOpenAiStreamCommonCallback callback = new ChatOpenAiStreamCommonCallback(channelContext, apiSendVo,
            answerId, start, textQuesiton, latch);
        String apiKey = EnvUtils.getStr("VOLCENGINE_API_KEY");
        Call call = OpenAiClient.chatCompletions(VolcEngineConst.API_PERFIX_URL, apiKey, chatRequestVo, callback);
        calls.add(call);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }

    });

    // gpt 4o
    Threads.getTioExecutor().execute(() -> {
      try {
        long id = SnowflakeIdUtils.id();
        OpenAiChatRequestVo genOpenAiRequestVo = genOpenAiRequestVo(OpenAiModels.GPT_4O, messages, id);
        ChatOpenAiStreamCommonCallback openAiCallback = new ChatOpenAiStreamCommonCallback(channelContext, apiSendVo,
            id, start, textQuesiton, latch);
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
        ChatGeminiStreamCommonCallback geminiCallback = new ChatGeminiStreamCommonCallback(channelContext, apiSendVo,
            id, start, textQuesiton, latch);
        Call geminiCall = GeminiClient.stream(GoogleModels.GEMINI_2_0_FLASH_EXP, geminiChatRequestVo, geminiCallback);
        calls.add(geminiCall);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }

    });

    ChatStreamCallCan.put(apiSendVo.getSession_id(), calls);
    return null;

  }

  private AiChatResponseVo singleModel(ChannelContext channelContext, ChatAskVo apiChatSendVo, long answerId,
      String textQuestion) {
    Long sessionId = apiChatSendVo.getSession_id();
    String provider = apiChatSendVo.getProvider();
    String model = apiChatSendVo.getModel();

    List<UniChatMessage> messages = apiChatSendVo.getMessages();
    if (provider.equals(ModelPlatformName.SILICONFLOW)) {
      if (ModelNames.DEEPSEEK_R1.equals(model)) {
        model = SiliconFlowModels.DEEPSEEK_R1;
      } else if (ModelNames.DEEPSEEK_V3.equals(model)) {
        model = SiliconFlowModels.DEEPSEEK_V3;
      }

      OpenAiChatRequestVo chatRequestVo = genOpenAiRequestVo(model, apiChatSendVo.getMessages(), answerId);
      Threads.getTioExecutor().execute(() -> {
        try {
          long start = System.currentTimeMillis();

          ChatOpenAiStreamCommonCallback callback = new ChatOpenAiStreamCommonCallback(channelContext, apiChatSendVo,
              answerId, start, textQuestion);
          String apiKey = EnvUtils.getStr("SILICONFLOW_API_KEY");
          Call call = OpenAiClient.chatCompletions(SiliconFlowConsts.SELICONFLOW_API_BASE, apiKey, chatRequestVo,
              callback);
          ChatStreamCallCan.put(sessionId, call);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }

      });
      return null;

    } else if (provider.equals(ModelPlatformName.VOLC_ENGINE)) {
      if (ModelNames.DEEPSEEK_R1.equals(model)) {
        model = VolcEngineModels.DEEPSEEK_R1_250528;
      } else if (ModelNames.DEEPSEEK_V3.equals(model)) {
        model = VolcEngineModels.DEEPSEEK_V3_250324;
      }
      OpenAiChatRequestVo chatRequestVo = genOpenAiRequestVo(model, messages, answerId);
      Threads.getTioExecutor().execute(() -> {
        try {
          long start = System.currentTimeMillis();
          ChatOpenAiStreamCommonCallback callback = new ChatOpenAiStreamCommonCallback(channelContext, apiChatSendVo,
              answerId, start, textQuestion);
          String apiKey = EnvUtils.getStr("VOLCENGINE_API_KEY");
          Call call = OpenAiClient.chatCompletions(VolcEngineConst.API_PERFIX_URL, apiKey, chatRequestVo, callback);
          ChatStreamCallCan.put(sessionId, call);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }
      });
      return null;
    } else if (ModelPlatformName.GOOGLE.equals(provider)) {

      Threads.getTioExecutor().execute(() -> {
        try {
          long start = System.currentTimeMillis();
          GeminiChatRequestVo geminiChatRequestVo = genGeminiRequestVo(messages, answerId);
          ChatGeminiStreamCommonCallback geminiCallback = new ChatGeminiStreamCommonCallback(channelContext,
              apiChatSendVo, answerId, start, textQuestion);
          Call geminiCall = GeminiClient.stream(apiChatSendVo.getModel(), geminiChatRequestVo, geminiCallback);
          ChatStreamCallCan.put(sessionId, geminiCall);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }
      });
      return null;

    }
    //
    else {
      OpenAiChatRequestVo chatRequestVo = genOpenAiRequestVo(model, messages, answerId);

      Threads.getTioExecutor().execute(() -> {
        try {
          long start = System.currentTimeMillis();
          ChatOpenAiStreamCommonCallback callback = new ChatOpenAiStreamCommonCallback(channelContext, apiChatSendVo,
              answerId, start, textQuestion);
          Call call = OpenAiClient.chatCompletions(chatRequestVo, callback);
          ChatStreamCallCan.put(sessionId, call);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }
      });

      return null;

    }
  }

  private OpenAiChatRequestVo genOpenAiRequestVo(String model, List<UniChatMessage> messages, Long answerId) {
    OpenAiChatRequestVo chatRequestVo = new OpenAiChatRequestVo().setModel(model)
        //
        .setChatMessages(messages);

    chatRequestVo.setStream(true);
    String requestJson = JsonUtils.toSkipNullJson(chatRequestVo);
    agentNotificationService.sendPredict(requestJson);
    // log.info("chatRequestVo:{}", requestJson);
    // save to database
    TioThreadUtils.execute(() -> {
      String sanitizedJson = requestJson.replaceAll("\u0000", "");
      Db.save(AgentTableNames.llm_chat_completion,
          Row.by("id", answerId).set("request", PgObjectUtils.json(sanitizedJson)));
    });
    return chatRequestVo;
  }

  private GeminiChatRequestVo genGeminiRequestVo(List<UniChatMessage> messages, long answerId) {
    GeminiChatRequestVo geminiChatRequestVo = new GeminiChatRequestVo();

    List<GeminiContentVo> contents = new ArrayList<>(messages.size());
    for (UniChatMessage chatMessage : messages) {
      String role = chatMessage.getRole();
      String content = chatMessage.getContent();
      if (StrUtil.isBlank(content)) {
        continue;
      }
      ChatMessageArgs args = chatMessage.getArgs();

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
    Aop.get(AgentNotificationService.class).sendPredict(requestJson);

    // log.info("chatRequestVo:{}", requestJson);
    // save to database
    TioThreadUtils.execute(() -> {
      Db.save(AgentTableNames.llm_chat_completion,
          Row.by("id", answerId).set("request", PgObjectUtils.json(requestJson)));
    });
    return geminiChatRequestVo;

  }

}