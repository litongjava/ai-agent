package nexus.io.llm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import nexus.io.agent.consts.AiAgentBaseTableNames;
import nexus.io.chat.ChatMessageArgs;
import nexus.io.chat.PlatformInput;
import nexus.io.chat.UniChatClient;
import nexus.io.chat.UniChatMessage;
import nexus.io.chat.UniChatRequest;
import nexus.io.consts.ModelNames;
import nexus.io.consts.ModelPlatformName;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.gemini.GeminiChatRequest;
import nexus.io.gemini.GeminiClient;
import nexus.io.gemini.GeminiContent;
import nexus.io.gemini.GeminiFileData;
import nexus.io.gemini.GeminiPart;
import nexus.io.gemini.GeminiSystemInstruction;
import nexus.io.gemini.GoogleModels;
import nexus.io.gitee.GiteeModels;
import nexus.io.http.common.sse.SsePacket;
import nexus.io.jfinal.aop.Aop;
import nexus.io.kit.PgObjectUtils;
import nexus.io.llm.callback.ChatGeminiEventSourceListener;
import nexus.io.llm.callback.ChatOpenAiEventSourceListener;
import nexus.io.llm.can.ChatStreamCallCan;
import nexus.io.llm.consts.AiChatEventName;
import nexus.io.llm.consts.ApiChatAskType;
import nexus.io.llm.service.LlmChatHistoryService;
import nexus.io.llm.vo.AiChatResponse;
import nexus.io.llm.vo.ChatAskRequest;
import nexus.io.llm.vo.ChatParamVo;
import nexus.io.model.upload.UploadResult;
import nexus.io.openai.ChatProvider;
import nexus.io.openai.chat.OpenAiChatRequest;
import nexus.io.openai.chat.OpenAiChatResponse;
import nexus.io.openai.client.OpenAiClient;
import nexus.io.openai.consts.OpenAiModels;
import nexus.io.openrouter.OpenRouterModels;
import nexus.io.siliconflow.SiliconFlowModels;
import nexus.io.tio.boot.admin.utils.TioVirtualThreadUtils;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.Tio;
import nexus.io.tio.utils.Threads;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.hutool.StrUtil;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;
import nexus.io.tio.utils.thread.TioThreadUtils;
import nexus.io.volcengine.VolcEngineConst;
import nexus.io.volcengine.VolcEngineModels;
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
  public AiChatResponse predict(ChatAskRequest chatAskVo, ChatParamVo chatParamVo, AiChatResponse aiChatResponseVo) {
    String provider = chatAskVo.getProvider();
    Boolean stream = chatAskVo.isStream();
    String type = chatAskVo.getType();
    ChannelContext channelContext = chatParamVo.getChannelContext();
    List<UniChatMessage> history = chatParamVo.getHistory();
    Long sessionId = chatAskVo.getSession_id();
    String rewrite_quesiton = chatParamVo.getRewriteQuestion();
    String textQuestion = chatParamVo.getTextQuestion();
    List<UploadResult> uploadFiles = chatParamVo.getUploadFiles();
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
      for (UploadResult uploadResultVo : uploadFiles) {
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
        aiChatResponseVo.setAnswer_id(answerId);
        aiChatResponseVo.setCitions(citations);
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
  private AiChatResponse multiModel(ChannelContext channelContext, ChatAskRequest chatAskVo, long answerId,
      String textQuesiton) {
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
        ChatOpenAiEventSourceListener callback = new ChatOpenAiEventSourceListener(channelContext, chatAskVo, answerId,
            start, textQuesiton, latch);
        String apiKey = EnvUtils.getStr("VOLCENGINE_API_KEY");
        EventSource call = OpenAiClient.chatCompletions(VolcEngineConst.API_PREFIX_URL, apiKey, chatRequestVo,
            callback);
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
        ChatOpenAiEventSourceListener callback = new ChatOpenAiEventSourceListener(channelContext, chatAskVo, answerId,
            start, textQuesiton, latch);
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
        ChatGeminiEventSourceListener geminiCallback = new ChatGeminiEventSourceListener(channelContext, chatAskVo, id,
            start, textQuesiton, latch);
        EventSource call = GeminiClient.stream(GoogleModels.GEMINI_2_0_FLASH_EXP, geminiChatRequestVo, geminiCallback);
        calls.add(call);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }

    });

    ChatStreamCallCan.put(chatAskVo.getSession_id(), calls);
    return null;

  }

  private AiChatResponse singleModel(ChannelContext channelContext, ChatAskRequest chatAskVo, long answerId,
      String textQuestion) {
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

    } else if (provider.equals(ModelPlatformName.VOLC_ENGINE)) {
      if (ModelNames.DEEPSEEK_R1.equals(model)) {
        model = VolcEngineModels.DEEPSEEK_R1_250528;
      } else if (ModelNames.DEEPSEEK_V3.equals(model)) {
        model = VolcEngineModels.DEEPSEEK_V3_250324;
      }

    } else if (provider.equals(ModelPlatformName.OPENROUTER)) {
      OpenAiChatRequest chatRequestVo = genOpenAiRequestVo(model, messages, answerId);

      if (OpenRouterModels.QWEN_QWEN3_CODER.equals(model)) {
        chatRequestVo.setProvider(ChatProvider.cerebras());
      } else if (OpenRouterModels.Z_AI_GLM_4_6.equals(model)) {
        chatRequestVo.setProvider(ChatProvider.cerebras());
        chatRequestVo.setEnable_thinking(false);
      }
    } else if (provider.equals(ModelPlatformName.GITEE)) {
      if (OpenRouterModels.QWEN_QWEN3_CODER.equals(model)) {
        model = GiteeModels.QWEN3_CODER_480B_A35B_INSTRUCT;

      } else if (OpenRouterModels.AUTO.equals(model)) {
        model = GiteeModels.QWEN3_CODER_480B_A35B_INSTRUCT;
      }
      return null;

    } else if (ModelPlatformName.GOOGLE.equals(provider)) {

    } else {
    }

    PlatformInput platformInput = new PlatformInput(provider, model);
    UniChatRequest uniChatRequest = new UniChatRequest(platformInput);
    uniChatRequest.setMessages(messages);

    TioVirtualThreadUtils.execute(() -> {
      try {
        long start = System.currentTimeMillis();
        ChatOpenAiEventSourceListener callback = new ChatOpenAiEventSourceListener(channelContext, chatAskVo, answerId,
            start, textQuestion);
        EventSource call = UniChatClient.stream(uniChatRequest, callback);
        
        ChatStreamCallCan.put(sessionId, call);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }
    });
    return null;

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
      Db.save(AiAgentBaseTableNames.llm_chat_completion,
          Row.by("id", answerId).set("request", PgObjectUtils.json(sanitizedJson)));
    });
    return chatRequestVo;
  }

  private GeminiChatRequest genGeminiRequestVo(List<UniChatMessage> messages, long answerId) {
    GeminiChatRequest geminiChatRequestVo = new GeminiChatRequest();

    List<GeminiContent> contents = new ArrayList<>(messages.size());
    for (UniChatMessage chatMessage : messages) {
      String role = chatMessage.getRole();
      String content = chatMessage.getContent();
      if (StrUtil.isBlank(content)) {
        continue;
      }
      ChatMessageArgs args = chatMessage.getArgs();

      if (args != null) {
        List<GeminiPart> parts = new ArrayList<>();
        if (args.getUrl() != null) {
          String url = args.getUrl();
          GeminiFileData geminiFileDataVo = new GeminiFileData("video/*", url);
          GeminiPart videoPart = new GeminiPart(geminiFileDataVo);
          parts.add(videoPart);
        }

        GeminiPart questionPart = new GeminiPart(content);
        parts.add(questionPart);
        GeminiContent vo = new GeminiContent("user", parts);
        contents.add(vo);
      } else {

        if (role.equals("assistant")) {
          role = "model";
        } else if (role.equals("system")) {
          GeminiPart part = new GeminiPart(content);
          GeminiSystemInstruction geminiSystemInstructionVo = new GeminiSystemInstruction(part);
          geminiChatRequestVo.setSystem_instruction(geminiSystemInstructionVo);
          continue;
        }
        GeminiPart part = new GeminiPart(content);
        GeminiContent vo = new GeminiContent(role, Collections.singletonList(part));
        contents.add(vo);
      }

    }

    geminiChatRequestVo.setContents(contents);

    String requestJson = JsonUtils.toSkipNullJson(geminiChatRequestVo);
    Aop.get(AgentNotificationService.class).sendPredict(requestJson);

    // log.info("chatRequestVo:{}", requestJson);
    // save to database
    TioThreadUtils.execute(() -> {
      Db.save(AiAgentBaseTableNames.llm_chat_completion,
          Row.by("id", answerId).set("request", PgObjectUtils.json(requestJson)));
    });
    return geminiChatRequestVo;

  }

}