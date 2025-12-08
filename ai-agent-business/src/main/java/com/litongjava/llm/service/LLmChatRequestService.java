package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.jfinal.kit.Kv;
import com.litongjava.agent.consts.AgentLLMTableNames;
import com.litongjava.chat.ChatMessageArgs;
import com.litongjava.chat.UniChatClient;
import com.litongjava.chat.UniChatMessage;
import com.litongjava.consts.ModelNames;
import com.litongjava.consts.ModelPlatformName;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.gemini.GeminiChatRequest;
import com.litongjava.gemini.GeminiClient;
import com.litongjava.gemini.GeminiContentVo;
import com.litongjava.gemini.GeminiFileDataVo;
import com.litongjava.gemini.GeminiPartVo;
import com.litongjava.gemini.GeminiSystemInstructionVo;
import com.litongjava.gemini.GoogleModels;
import com.litongjava.gitee.GiteeConst;
import com.litongjava.gitee.GiteeModels;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.kit.PgObjectUtils;
import com.litongjava.llm.callback.ChatGeminiEventSourceListener;
import com.litongjava.llm.callback.ChatOpenAiEventSourceListener;
import com.litongjava.llm.can.ChatStreamCallCan;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.consts.ApiChatAskType;
import com.litongjava.llm.vo.AiChatResponseVo;
import com.litongjava.llm.vo.ChatAskVo;
import com.litongjava.llm.vo.ChatParamVo;
import com.litongjava.openai.ChatProvider;
import com.litongjava.openai.chat.OpenAiChatRequest;
import com.litongjava.openai.chat.OpenAiChatResponse;
import com.litongjava.openai.client.OpenAiClient;
import com.litongjava.openai.consts.OpenAiModels;
import com.litongjava.openrouter.OpenRouterConst;
import com.litongjava.openrouter.OpenRouterModels;
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
import okhttp3.sse.EventSource;

@Slf4j
public class LLmChatRequestService {

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
  public AiChatResponseVo predict(ChatAskVo chatAskVo, ChatParamVo chatParamVo, AiChatResponseVo aiChatResponseVo) {
    String provider = chatAskVo.getProvider();
    Boolean stream = chatAskVo.isStream();
    String type = chatAskVo.getType();
    ChannelContext channelContext = chatParamVo.getChannelContext();
    List<UniChatMessage> history = chatParamVo.getHistory();
    Long sessionId = chatAskVo.getSession_id();
    String rewrite_quesiton = chatParamVo.getRewriteQuestion();
    String textQuestion = chatParamVo.getTextQuestion();
    List<UploadResultVo> uploadFiles = chatParamVo.getUploadFiles();
    String systemPrompt = chatParamVo.getSystemPrompt();
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

    chatAskVo.setMessages(history);
    long answerId = SnowflakeIdUtils.id();
    if (stream) {
      // SsePacket packet = new SsePacket(AiChatEventName.input,
      // JsonUtils.toJson(history));
      // Tio.bSend(channelContext, packet);
      if (ApiChatAskType.compare.equals(type)) {
        return multiModel(channelContext, chatAskVo, answerId, textQuestion);

      } else {
        return singleModel(channelContext, chatAskVo, answerId, textQuestion);
      }

    } else {
      if (ModelPlatformName.SILICONFLOW.equals(provider)) {

      } else {
        OpenAiChatRequest chatRequestVo = new OpenAiChatRequest().setModel(OpenAiModels.GPT_4O_MINI)
            //
            .setChatMessages(history);
        OpenAiChatResponse chatCompletions = OpenAiClient.chatCompletions(chatRequestVo);
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
  private AiChatResponseVo multiModel(ChannelContext channelContext, ChatAskVo chatAskVo, long answerId, String textQuesiton) {
    CountDownLatch latch = new CountDownLatch(3);
    List<UniChatMessage> messages = chatAskVo.getMessages();

    long start = System.currentTimeMillis();
    List<EventSource> calls = new ArrayList<>();
    // deepseek v3
    Threads.getTioExecutor().execute(() -> {
      try {
        OpenAiChatRequest chatRequestVo = genOpenAiRequestVo(VolcEngineModels.DEEPSEEK_V3_250324, messages, answerId);
//        ChatOpenAiStreamCommonCallback callback = new ChatOpenAiStreamCommonCallback(channelContext, apiSendVo, answerId, start,
//            textQuesiton, latch);
        ChatOpenAiEventSourceListener callback = new ChatOpenAiEventSourceListener(channelContext, chatAskVo, answerId, start, textQuesiton,
            latch);
        String apiKey = EnvUtils.getStr("VOLCENGINE_API_KEY");
        EventSource call = OpenAiClient.chatCompletions(VolcEngineConst.API_PREFIX_URL, apiKey, chatRequestVo, callback);
        calls.add(call);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }

    });

    // gpt 4o
    Threads.getTioExecutor().execute(() -> {
      try {
        long id = SnowflakeIdUtils.id();
        OpenAiChatRequest genOpenAiRequestVo = genOpenAiRequestVo(OpenAiModels.GPT_4O, messages, id);
        ChatOpenAiEventSourceListener callback = new ChatOpenAiEventSourceListener(channelContext, chatAskVo, answerId, start, textQuesiton,
            latch);
        EventSource openAicall = OpenAiClient.chatCompletions(genOpenAiRequestVo, callback);
        calls.add(openAicall);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }

    });

    Threads.getTioExecutor().execute(() -> {
      try {
        long id = SnowflakeIdUtils.id();
        GeminiChatRequest geminiChatRequestVo = genGeminiRequestVo(messages, id);
        ChatGeminiEventSourceListener geminiCallback = new ChatGeminiEventSourceListener(channelContext, chatAskVo, id, start, textQuesiton,
            latch);
        EventSource call = GeminiClient.stream(GoogleModels.GEMINI_2_0_FLASH_EXP, geminiChatRequestVo, geminiCallback);
        calls.add(call);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }

    });

    ChatStreamCallCan.put(chatAskVo.getSession_id(), calls);
    return null;

  }

  private AiChatResponseVo singleModel(ChannelContext channelContext, ChatAskVo chatAskVo, long answerId, String textQuestion) {
    Long sessionId = chatAskVo.getSession_id();
    String provider = chatAskVo.getProvider();
    String model = chatAskVo.getModel();

    List<UniChatMessage> messages = chatAskVo.getMessages();
    if (provider.equals(ModelPlatformName.SILICONFLOW)) {
      if (ModelNames.DEEPSEEK_R1.equals(model)) {
        model = SiliconFlowModels.DEEPSEEK_R1;
      } else if (ModelNames.DEEPSEEK_V3.equals(model)) {
        model = SiliconFlowModels.DEEPSEEK_V3;
      }

      OpenAiChatRequest chatRequestVo = genOpenAiRequestVo(model, chatAskVo.getMessages(), answerId);
      Threads.getTioExecutor().execute(() -> {
        try {
          long start = System.currentTimeMillis();

          ChatOpenAiEventSourceListener callback = new ChatOpenAiEventSourceListener(channelContext, chatAskVo, answerId, start,
              textQuestion);
          String apiKey = EnvUtils.getStr("SILICONFLOW_API_KEY");
          EventSource call = OpenAiClient.chatCompletions(SiliconFlowConsts.SELICONFLOW_API_BASE, apiKey, chatRequestVo, callback);
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

      OpenAiChatRequest chatRequestVo = genOpenAiRequestVo(model, messages, answerId);
      Threads.getTioExecutor().execute(() -> {
        try {
          long start = System.currentTimeMillis();
          ChatOpenAiEventSourceListener callback = new ChatOpenAiEventSourceListener(channelContext, chatAskVo, answerId, start,
              textQuestion);
          String apiKey = EnvUtils.getStr("VOLCENGINE_API_KEY");
          EventSource call = OpenAiClient.chatCompletions(VolcEngineConst.API_PREFIX_URL, apiKey, chatRequestVo, callback);
          ChatStreamCallCan.put(sessionId, call);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }
      });
      return null;

    } else if (provider.equals(ModelPlatformName.OPENROUTER)) {
      OpenAiChatRequest chatRequestVo = genOpenAiRequestVo(model, messages, answerId);

      if (OpenRouterModels.QWEN_QWEN3_CODER.equals(model)) {
        chatRequestVo.setProvider(ChatProvider.cerebras());
      } else if (OpenRouterModels.Z_AI_GLM_4_6.equals(model)) {
        chatRequestVo.setProvider(ChatProvider.cerebras());
        chatRequestVo.setEnable_thinking(false);
      }

      Threads.getTioExecutor().execute(() -> {
        try {
          long start = System.currentTimeMillis();
          ChatOpenAiEventSourceListener callback = new ChatOpenAiEventSourceListener(channelContext, chatAskVo, answerId, start,
              textQuestion);
          EventSource call = OpenAiClient.chatCompletions(OpenRouterConst.API_PREFIX_URL, UniChatClient.OPENROUTER_API_KEY, chatRequestVo,
              callback);
          ChatStreamCallCan.put(sessionId, call);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }
      });
      return null;

    } else if (provider.equals(ModelPlatformName.GITEE)) {
      if (OpenRouterModels.QWEN_QWEN3_CODER.equals(model)) {
        model = GiteeModels.QWEN3_CODER_480B_A35B_INSTRUCT;

      } else if (OpenRouterModels.AUTO.equals(model)) {
        model = GiteeModels.QWEN3_CODER_480B_A35B_INSTRUCT;
      }

      OpenAiChatRequest chatRequestVo = genOpenAiRequestVo(model, messages, answerId);

      Threads.getTioExecutor().execute(() -> {
        try {
          long start = System.currentTimeMillis();
          ChatOpenAiEventSourceListener callback = new ChatOpenAiEventSourceListener(channelContext, chatAskVo, answerId, start,
              textQuestion);

          EventSource call = OpenAiClient.chatCompletions(GiteeConst.API_PREFIX_URL, UniChatClient.GITEE_API_KEY, chatRequestVo, callback);
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
          GeminiChatRequest geminiChatRequestVo = genGeminiRequestVo(messages, answerId);
          ChatGeminiEventSourceListener geminiCallback = new ChatGeminiEventSourceListener(channelContext, chatAskVo, answerId, start,
              textQuestion);
          EventSource geminiCall = GeminiClient.stream(chatAskVo.getModel(), geminiChatRequestVo, geminiCallback);
          ChatStreamCallCan.put(sessionId, geminiCall);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }
      });
      return null;

    }
    //
    else {
      OpenAiChatRequest chatRequestVo = genOpenAiRequestVo(model, messages, answerId);

      Threads.getTioExecutor().execute(() -> {
        try {
          long start = System.currentTimeMillis();
          ChatOpenAiEventSourceListener callback = new ChatOpenAiEventSourceListener(channelContext, chatAskVo, answerId, start,
              textQuestion);
          EventSource call = OpenAiClient.chatCompletions(chatRequestVo, callback);
          ChatStreamCallCan.put(sessionId, call);
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }
      });

      return null;

    }
  }

  private OpenAiChatRequest genOpenAiRequestVo(String model, List<UniChatMessage> messages, Long answerId) {
    OpenAiChatRequest chatRequestVo = new OpenAiChatRequest().setModel(model)
        //
        .setChatMessages(messages);

    chatRequestVo.setStream(true);
    String requestJson = JsonUtils.toSkipNullJson(chatRequestVo);
    agentNotificationService.sendPredict(requestJson);
    // log.info("chatRequestVo:{}", requestJson);
    // save to database
    TioThreadUtils.execute(() -> {
      String sanitizedJson = requestJson.replaceAll("\u0000", "");
      Db.save(AgentLLMTableNames.llm_chat_completion, Row.by("id", answerId).set("request", PgObjectUtils.json(sanitizedJson)));
    });
    return chatRequestVo;
  }

  private GeminiChatRequest genGeminiRequestVo(List<UniChatMessage> messages, long answerId) {
    GeminiChatRequest geminiChatRequestVo = new GeminiChatRequest();

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
      Db.save(AgentLLMTableNames.llm_chat_completion, Row.by("id", answerId).set("request", PgObjectUtils.json(requestJson)));
    });
    return geminiChatRequestVo;

  }

}