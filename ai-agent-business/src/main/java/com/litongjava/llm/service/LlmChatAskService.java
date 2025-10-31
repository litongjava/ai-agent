package com.litongjava.llm.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;
import com.litongjava.agent.service.PromptService;
import com.litongjava.agent.service.TranslateService;
import com.litongjava.chat.ChatMessageArgs;
import com.litongjava.chat.UniChatMessage;
import com.litongjava.db.activerecord.Row;
import com.litongjava.google.search.GoogleCustomSearchClient;
import com.litongjava.google.search.GoogleCustomSearchResponse;
import com.litongjava.google.search.SearchResultItem;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.callback.ChatOpenAiEventSourceListener;
import com.litongjava.llm.can.ChatStreamCallCan;
import com.litongjava.llm.config.AiAgentContext;
import com.litongjava.llm.consts.AgentMessageType;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.consts.ApiChatAskType;
import com.litongjava.llm.consts.ApiChatSendCmd;
import com.litongjava.llm.dao.SchoolDictDao;
import com.litongjava.llm.utils.AgentBotQuestionUtils;
import com.litongjava.llm.vo.AiChatResponseVo;
import com.litongjava.llm.vo.ChatAskVo;
import com.litongjava.llm.vo.ChatParamVo;
import com.litongjava.llm.vo.SchoolDict;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.model.web.WebPageContent;
import com.litongjava.openai.chat.OpenAiChatRequestVo;
import com.litongjava.openai.chat.OpenAiChatResponseVo;
import com.litongjava.openai.client.OpenAiClient;
import com.litongjava.openai.consts.OpenAiModels;
import com.litongjava.searxng.SearxngResult;
import com.litongjava.searxng.SearxngSearchClient;
import com.litongjava.searxng.SearxngSearchResponse;
import com.litongjava.tavily.TavilyClient;
import com.litongjava.tavily.TavilySearchResponse;
import com.litongjava.tavily.TavilySearchResult;
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
import com.litongjava.tio.utils.youtube.YouTubeIdUtil;

import lombok.extern.slf4j.Slf4j;
import okhttp3.sse.EventSource;

@Slf4j
public class LlmChatAskService {

  private LLmChatInferenceService dispatcherService = Aop.get(LLmChatInferenceService.class);
  private WebPageService webPageService = Aop.get(WebPageService.class);
  private LlmRewriteQuestionService llmRewriteQuestionService = Aop.get(LlmRewriteQuestionService.class);
  private LlmChatHistoryService llmChatHistoryService = Aop.get(LlmChatHistoryService.class);
  private YoutubeVideoSubtitleService youtubeVideoSubtitleService = Aop.get(YoutubeVideoSubtitleService.class);
  private PromptService promptService = Aop.get(PromptService.class);
  private AgentPromptService agentPromptService = Aop.get(AgentPromptService.class);

  private SchoolDictDao schoolDictDao = Aop.get(SchoolDictDao.class);
  private YoutubeService youtubeService = Aop.get(YoutubeService.class);
  private TranslateService translateService = Aop.get(TranslateService.class);
  private ModelSelectService modelSelectService = Aop.get(ModelSelectService.class);
  private RunningNotificationService notification = AiAgentContext.me().getNotification();
  private boolean enableRewrite = false;

  public RespBodyVo index(ChannelContext channelContext, ChatAskVo apiChatAskVo) {
    /**
     * inputQestion 用户输入的问题 textQuestion 用户输入的问题和提示词
     */
    List<UniChatMessage> messages = apiChatAskVo.getMessages();
    String inputQestion = null;
    String augmentedQuestion = null;
    if (messages != null && messages.size() > 0) {
      inputQestion = messages.get(0).getContent();
      messages.remove(0);
      augmentedQuestion = inputQestion;
    }

    apiChatAskVo.setUser_input_quesiton(inputQestion);

    // save file content to history
    ChatParamVo chatParamVo = new ChatParamVo();
    String type = apiChatAskVo.getType();
    boolean stream = apiChatAskVo.isStream();

    Long schoolId = apiChatAskVo.getSchool_id();
    String userId = apiChatAskVo.getUser_id();
    Long sessionId = apiChatAskVo.getSession_id();
    Long appId = apiChatAskVo.getApp_id();
    List<Long> file_ids = apiChatAskVo.getFile_ids();
    String cmd = apiChatAskVo.getCmd();
    ChatMessageArgs chatSendArgs = apiChatAskVo.getArgs();
    boolean history_enabled = apiChatAskVo.isHistory_enabled();

    SchoolDict schoolDict = null;

    // 1.查询学校
    if (schoolId != null) {
      try {
        schoolDict = schoolDictDao.getSchoolById(schoolId.longValue());
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

    // 2.问题预处理
    if (ApiChatAskType.translator.equals(type)) {
      modelSelectService.select(type, apiChatAskVo);

      if (StrUtil.isNotBlank(inputQestion)) {
        augmentedQuestion = translateService.augmenteQuesiton(inputQestion);
        history_enabled = false;
      } else {
        return RespBodyVo.fail("input question can not be empty");
      }
    }
    if (ApiChatAskType.english_vocabulary.equals(type)) {
      modelSelectService.select(type, apiChatAskVo);
      if (StrUtil.isNotBlank(inputQestion)) {
        String fileName = "english_vocabulary_prompt.txt";
        Kv by = Kv.by("word", inputQestion);
        augmentedQuestion = promptService.render(fileName, by);
        history_enabled = false;
      } else {
        return RespBodyVo.fail("input question can not be empty");
      }
    }
    if (ApiChatAskType.english_sentence.equals(type)) {
      modelSelectService.select(type, apiChatAskVo);
      if (StrUtil.isNotBlank(inputQestion)) {
        String fileName = "english_sentence_prompt.txt";
        Kv by = Kv.by("sentence", inputQestion);
        augmentedQuestion = promptService.render(fileName, by);
        history_enabled = false;
      } else {
        return RespBodyVo.fail("input question can not be empty");
      }
    }

    else if (ApiChatAskType.celebrity.equals(type)) {
      String name = chatSendArgs.getName();
      String institution = chatSendArgs.getInstitution();
      inputQestion = name + " at " + institution;
      augmentedQuestion = inputQestion;

    } else if (ApiChatAskType.perplexity.equals(type)) {
      if (schoolDict != null) {
        augmentedQuestion += (" at " + schoolDict.getFull_name());
      }
    } else if (ApiChatAskType.youtube.equals(type)) {
      if (ApiChatSendCmd.summary.equals(cmd)) {
        augmentedQuestion = "summary the video content";
      }

    } else if (ApiChatAskType.general.equals(type)) {
      if (ApiChatSendCmd.summary.equals(cmd)) {
        augmentedQuestion = "summary the web pages";
      }
    }

    if (inputQestion != null) {
      if (stream) {
        SsePacket packet = new SsePacket(AiChatEventName.question, inputQestion);
        Tio.bSend(channelContext, packet);
      }

      if (inputQestion.startsWith("__echo:")) {
        String[] split = inputQestion.split(":");
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
    }

    // 移除历史
    if (apiChatAskVo.isRe_generate()) {
      Long previous_question_id = apiChatAskVo.getPrevious_question_id();
      Long previous_answer_id = apiChatAskVo.getPrevious_answer_id();
      llmChatHistoryService.remove(previous_question_id, previous_answer_id);
    }

    // 3.查询历史
    AiChatResponseVo aiChatResponseVo = new AiChatResponseVo();
    List<Row> histories = null;
    if (history_enabled) {
      if (!ApiChatAskType.translator.equals(type)) {
        try {
          histories = llmChatHistoryService.getHistory(sessionId);
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
    }

//    if (histories != null && histories.size() > 30) {
//      String message = ResponsePrompt.defaultFallBackMessage + " Your new question might be:" + augmentedQuestion;
//
//      long answerId = SnowflakeIdUtils.id();
//      aiChatResponseVo.setAnswerId(answerId);
//
//      llmChatHistoryService.saveAssistant(answerId, sessionId, message);
//      Kv kv = Kv.by("answer_id", answerId);
//      if (stream) {
//        SsePacket ssePacket = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(Kv.by("content", message)));
//        Tio.bSend(channelContext, ssePacket);
//        SsePacket packet = new SsePacket(AiChatEventName.message_id, JsonUtils.toJson(kv));
//        Tio.send(channelContext, packet);
//        SseEmitter.closeSeeConnection(channelContext);
//      }
//      aiChatResponseVo.setContent(message);
//      return RespBodyVo.ok(message);
//    }

    if (stream && histories != null) {
      SsePacket ssePacket = new SsePacket(AiChatEventName.progress,
          ("The number of history records to be queried:" + histories.size()).getBytes());
      Tio.bSend(channelContext, ssePacket);
    }

    // 4.处理历史记录,拼接历史消息
    boolean isFirstQuestion = false;
    List<UniChatMessage> historyMessage = new ArrayList<>();
    if (histories == null || histories.size() < 1) {
      isFirstQuestion = true;
    } else {
      for (Row record : histories) {
        String messageType = record.getStr("type");
        if (AgentMessageType.TEXT.equals(messageType)) {
          String role = record.getStr("role");
          String content = record.getStr("content");
          String args = record.getString("args");
          if (args != null) {
            ChatMessageArgs historyArgs = JsonUtils.parse(args, ChatMessageArgs.class);

            String url = historyArgs.getUrl();
            if (StrUtil.isNotBlank(url)) {
              if (ApiChatAskType.youtube.equals(historyArgs.getType())) {
                String extractVideoId = YouTubeIdUtil.extractVideoId(url);
                if (extractVideoId != null) {
                  String subTitle = youtubeVideoSubtitleService.get(extractVideoId);
                  if (subTitle != null) {
                    historyMessage.add(new UniChatMessage(role, subTitle));

                  }
                }
              }
            }
            String[] urls = historyArgs.getUrls();
            if (urls != null && urls.length > 0) {
              if (ApiChatAskType.general.equals(historyArgs.getType())) {
                String htmlContent = webPageService.readHtmlPage(urls);
                historyMessage.add(new UniChatMessage(role, htmlContent));
              }
            }

            if (StrUtil.isNotBlank(content)) {
              historyMessage.add(new UniChatMessage(role, content));
            }
          } else {
            historyMessage.add(new UniChatMessage(role, content));
          }
        } else if (AgentMessageType.FILE.equals(messageType)) {
          String role = record.getStr("role");
          String content = record.getStr("content");
          String str = record.getStr("metadata");
          List<UploadResultVo> uploadVos = JsonUtils.parseArray(str, UploadResultVo.class);
          for (UploadResultVo uploadResult : uploadVos) {
            historyMessage.add(new UniChatMessage(role,
                String.format("user upload %s conent is :%s", uploadResult.getName(), uploadResult.getContent())));
          }

          if (StrUtil.notBlank(content)) {
            historyMessage.add(new UniChatMessage(role, content));
          }
        }
      }
    }

    String model = apiChatAskVo.getModel();
    String provider = apiChatAskVo.getProvider();

    // 5.记录问题
    // save to the user question to db
    long questionId = SnowflakeIdUtils.id();
    List<UploadResultVo> fileInfo = null;
    try {
      if (file_ids != null) {
        fileInfo = Aop.get(ChatUploadService.class).getFileBasicInfoByIds(file_ids);
        chatParamVo.setUploadFiles(fileInfo);
      }
      llmChatHistoryService.saveUser(questionId, sessionId, inputQestion, fileInfo, chatSendArgs);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }

    if (stream) {
      Kv kv = Kv.by("question_id", questionId);
      SsePacket packet = new SsePacket(AiChatEventName.message_id, JsonUtils.toJson(kv));
      Tio.bSend(channelContext, packet);
    }
    aiChatResponseVo.setQuesitonId(questionId);
    if (fileInfo != null) {
      aiChatResponseVo.setUploadFiles(fileInfo);
    }

    String rewriteQuestion = null;
    if (enableRewrite) {
      // 7.重写问题后发送数据
      if (!ApiChatAskType.advise.equals(type)) {
        // 关闭问题重写
        rewriteQuestion = llmRewriteQuestionService.rewrite(augmentedQuestion, historyMessage);
        aiChatResponseVo.setRewrite(augmentedQuestion);
        chatParamVo.setRewriteQuestion(augmentedQuestion);
      }
    }

    StringBuffer stringBuffer = new StringBuffer();
    stringBuffer.append("user_id:").append(userId).append("\n")
        //
        .append("chat_id").append(sessionId).append("\n");

    if (StrUtil.isNotBlank(augmentedQuestion)) {
      stringBuffer.append("question:").append(augmentedQuestion).append("\n");
    }
    if (StrUtil.isNotBlank(rewriteQuestion)) {
      stringBuffer.append("writed:").append(rewriteQuestion).append("\n");
    }

    stringBuffer.append("args:").append(JsonUtils.toSkipNullJson(chatSendArgs)).append("\n");
    //
    stringBuffer.append("history:" + JsonUtils.toSkipNullJson(historyMessage)).append("\n");

    if (notification != null) {
      Long appTenant = EnvUtils.getLong("app.tenant");
      notification.sendRewrite(appTenant, stringBuffer.toString());
    }

    // 8.判断类型
    if (ApiChatAskType.search.equals(type)) {
      if (StrUtil.isNotBlank(augmentedQuestion)) {
        log.info("search:{}", augmentedQuestion);
        String systemPrompt = search(channelContext, augmentedQuestion);
        chatParamVo.setSystemPrompt(systemPrompt);
      }
    } else if (ApiChatAskType.advise.equals(type)) {
      if (StrUtil.isNotBlank(augmentedQuestion)) {
        String systemPrompt = advise(channelContext, augmentedQuestion, historyMessage, schoolDict, model);
        chatParamVo.setSystemPrompt(systemPrompt);
      }
    } else if (ApiChatAskType.celebrity.equals(type)) {
      log.info("celebrity:{}", augmentedQuestion);
      String systemPrompt = Aop.get(CelebrityService.class).celebrity(channelContext, chatSendArgs);
      chatParamVo.setSystemPrompt(systemPrompt);
      if (systemPrompt == null) {
        if (channelContext != null) {
          SseEmitter.closeChunkConnection(channelContext);
        }
        return RespBodyVo.fail("Failed to search celebrity");
      }
    } else if (ApiChatAskType.tutor.equals(type)) {
      String systemPrompt = tutor(channelContext, augmentedQuestion, historyMessage, schoolDict, model);
      chatParamVo.setSystemPrompt(systemPrompt);

    } else if (ApiChatAskType.math.equals(type)) {
      modelSelectService.select(type, apiChatAskVo);
      String fileName = "math_prompt.txt";
      // Kv by = Kv.by("data", inputQestion);
      String systemPrompt = promptService.render(fileName);
      chatParamVo.setSystemPrompt(systemPrompt);

    } else if (ApiChatAskType.geogebra.equals(type)) {
      modelSelectService.select(type, apiChatAskVo);
      String systemPrompt = agentPromptService.renderGeoGebraPrompt();
      chatParamVo.setSystemPrompt(systemPrompt);

    } else if (ApiChatAskType.youtube.equals(type)) {
      if (ApiChatSendCmd.summary.equals(cmd)) {
        String systemPrompt = PromptEngine.renderToStringFromDb("youtube_summary_prompt.txt");
        chatParamVo.setSystemPrompt(systemPrompt);
      } else {
        String systemPrompt = generalPrompt();
        chatParamVo.setSystemPrompt(systemPrompt);
      }
      youtubeService.youtube(channelContext, chatSendArgs, historyMessage);
    } else {
      if (ApiChatSendCmd.summary.equals(cmd)) {
        String systemPrompt = PromptEngine.renderToString("general_summary_prompt.txt");
        chatParamVo.setSystemPrompt(systemPrompt);
      } else {
        String systemPrompt = generalPrompt();
        chatParamVo.setSystemPrompt(systemPrompt);
      }

      if (chatSendArgs != null && chatSendArgs.getUrls() != null) {
        String message = null;

        String[] urls = chatSendArgs.getUrls();
        if (channelContext != null) {
          message = "First, user submit %d links. let me read them.  ";
          message = String.format(message, urls.length);
          Kv by = Kv.by("content", message);
          SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
          Tio.bSend(channelContext, ssePacket);
        }

        for (String url : urls) {
          ResponseVo responseVo = webPageService.get(url);
          if (responseVo != null && responseVo.isOk()) {
            StringBuffer htmlContent = new StringBuffer();
            htmlContent.append("source:").append(url).append(" content:").append(responseVo.getBodyString())
                .append("  ");
            String string = htmlContent.toString();

            message = "Read result:" + string;
            Tio.bSend(channelContext,
                new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(Kv.by("content", message))));
            historyMessage.add(new UniChatMessage("user", string));

          } else {
            message = "Sorry, No web page content is available of %s, please try again later.";
            message = String.format(message, url);
            historyMessage.add(new UniChatMessage("user", message));
            message = String.format(message, url);
            Tio.bSend(channelContext,
                new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(Kv.by("content", message))));
          }
        }
      }
    }

    // 09.发送问题通知

    if (StrUtil.isNotEmpty(augmentedQuestion)) {
      StringBuffer notificationStringBuffer = new StringBuffer();

      notificationStringBuffer.append("app env:").append(EnvUtils.getStr("app.env")).append("\n")
          //
          .append("userId:").append(userId).append("\n")
          //
          .append("school id:").append(schoolId).append("\n");

      notificationStringBuffer.append("user question:").append(augmentedQuestion).append("\n")
          //
          .append("type:").append(type).append("\n")
          //
          .append("provider:").append(provider).append("\n")
          //
          .append("model:").append(model).append("\n");

      if (schoolDict != null) {
        notificationStringBuffer.append("school name:").append(schoolDict.getFull_name()).append("\n");
      }

      if (appId != null) {
        notificationStringBuffer.append("app id:").append(appId).append("\n");
      }

      if (chatSendArgs != null) {
        notificationStringBuffer.append("args:").append(JsonUtils.toSkipNullJson(chatSendArgs)).append("\n");
      }

      log.info("question:{}", notificationStringBuffer.toString());
      if (notification != null) {
        Long appTenant = EnvUtils.getLong("app.tenant");
        notification.sendQuestion(appTenant, notificationStringBuffer.toString());
      }

      if (!EnvUtils.isDev()) {
        String thatTextQuestion = augmentedQuestion;
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

    // 10.处理问题
    chatParamVo.setFirstQuestion(isFirstQuestion).setTextQuestion(augmentedQuestion)
        //
        .setHistory(historyMessage).setChannelContext(channelContext);

    if (augmentedQuestion != null && augmentedQuestion.startsWith("4o:")) {
      if (stream) {
        SsePacket packet = new SsePacket(AiChatEventName.progress,
            "The user specifies that the gpt4o model is used for message processing");
        Tio.bSend(channelContext, packet);
      }
      String answer = processMessageByChatModel(apiChatAskVo, channelContext);
      aiChatResponseVo.setContent(answer);
      return RespBodyVo.ok(aiChatResponseVo);

    } else {
      dispatcherService.predict(apiChatAskVo, chatParamVo, aiChatResponseVo);
      return RespBodyVo.ok(aiChatResponseVo);
    }
  }

  private String tutor(ChannelContext channelContext, String textQuestion, List<UniChatMessage> historyMessage,
      SchoolDict schoolDict, String model) {
    String isoTimeStr = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    Kv kv = Kv.by("date", isoTimeStr);
    return promptService.render("general_prompt.txt", kv);
  }

  private String generalPrompt() {
    String isoTimeStr = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    Kv kv = Kv.by("date", isoTimeStr);
    return Aop.get(PromptService.class).render("general_prompt.txt", kv);
  }

  private String search(ChannelContext channelContext, String textQuestion) {
    TavilySearchResponse search = TavilyClient.search(textQuestion);
    List<TavilySearchResult> results = search.getResults();
    List<WebPageContent> pages = new ArrayList<>();
    StringBuffer markdown = new StringBuffer();
    for (int i = 0; i < results.size(); i++) {
      TavilySearchResult searchResult = results.get(i);
      String title = searchResult.getTitle();
      String url = searchResult.getUrl();
      pages.add(new WebPageContent(title, url));
      markdown.append("source " + (i + 1) + " " + searchResult.getContent());
    }
    if (channelContext != null) {
      SsePacket ssePacket = new SsePacket(AiChatEventName.citation, JsonUtils.toSkipNullJson(pages));
      Tio.send(channelContext, ssePacket);
    }

    String isoTimeStr = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    // 3. 使用 PromptEngine 模版引擎填充提示词
    Kv kv = Kv.by("date", isoTimeStr).set("context", markdown);
    String systemPrompt = PromptEngine.renderToStringFromDb("ai_search_prompt.txt", kv);
    return systemPrompt;
  }

  private String advise(ChannelContext channelContext, String textQuestion, List<UniChatMessage> historyMessage,
      SchoolDict schoolDict, String model) {
    String full_name = schoolDict.getFull_name();
    String domain_name = schoolDict.getDomain_name();
    if (channelContext != null) {
      String message = "First your are from %s let me search %s. ";
      message = String.format(message, full_name, domain_name);
      Kv by = Kv.by("content", message);
      SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
    }

    textQuestion = "site:" + domain_name + " " + textQuestion;
    log.info("quesiotn:{}", textQuestion);
    textQuestion = llmRewriteQuestionService.rewriteSearchQuesiton(textQuestion, historyMessage);
    log.info("rewtie quesiton:{}", textQuestion);

    if (channelContext != null) {
      String message = "Second rewrite your question to %s. ";
      message = String.format(message, textQuestion);
      Kv by = Kv.by("content", message);
      SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
    }
    StringBuffer markdown = useGoogle(channelContext, textQuestion, model);

    String isoTimeStr = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    // 3. 使用 PromptEngine 模版引擎填充提示词
    Kv kv = Kv.by("date", isoTimeStr).set("context", markdown);
    String systemPrompt = PromptEngine.renderToStringFromDb("ai_search_prompt.txt", kv);
    if (EnvUtils.isDev()) {
      log.info(systemPrompt);
    }
    return systemPrompt;
  }

  private StringBuffer useGoogle(ChannelContext channelContext, String textQuestion, String model) {
    GoogleCustomSearchResponse searchResponse = GoogleCustomSearchClient.search(textQuestion);
    List<SearchResultItem> results = searchResponse.getItems();
    log.info("found page size:{}", results.size());

    int max = 4;
    if (model.startsWith("gemini")) {
      max = 5;
    }

    List<WebPageContent> pages = new ArrayList<>();
    StringBuffer markdown = new StringBuffer();
    if (results.size() > 0) {
      if (channelContext != null) {
        String message = "Third I found %d web pages. let me read top %d pages. ";
        message = String.format(message, results.size(), max);
        Kv by = Kv.by("content", message);
        SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
        Tio.send(channelContext, ssePacket);
      }
    } else {
      String message = "Failed to search please try again later. ";
      Kv by = Kv.by("content", message);
      SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
    }

    if (results.size() < max) {
      max = results.size();
    }

    for (int i = 0; i < max; i++) {
      SearchResultItem result = results.get(i);
      String title = result.getTitle();
      String url = result.getLink();
      if (channelContext != null) {
        String message = "Read %s. ";
        message = String.format(message, url);
        Kv by = Kv.by("content", message);
        SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
        Tio.send(channelContext, ssePacket);
      }

      pages.add(new WebPageContent(title, url));

      ResponseVo responseVo = webPageService.get(url);
      if (responseVo == null) {
        if (channelContext != null) {
          String message = "Failed to read %s. ";
          message = String.format(message, url);
          Kv by = Kv.by("content", message);
          SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
          Tio.send(channelContext, ssePacket);
        }
        markdown.append("source " + (i + 1) + " " + result.getSnippet());
      } else {
        if (responseVo.isOk()) {
          markdown.append("source " + (i + 1) + " " + responseVo.getBodyString());
        } else {
          markdown.append("source " + (i + 1) + " " + result.getSnippet());
        }
      }
    }

    if (channelContext != null) {
      SsePacket ssePacket = new SsePacket(AiChatEventName.citation, JsonUtils.toSkipNullJson(pages));
      Tio.send(channelContext, ssePacket);
    }
    return markdown;
  }

  private StringBuffer useSearchNg(ChannelContext channelContext, String textQuestion) {
    SearxngSearchResponse searchResponse = SearxngSearchClient.search(textQuestion);
    List<SearxngResult> results = searchResponse.getResults();
    log.info("found page size:{}", results.size());

    List<WebPageContent> pages = new ArrayList<>();
    StringBuffer markdown = new StringBuffer();
    if (results.size() > 0) {
      if (channelContext != null) {
        String message = "Third I found %d web pages. let me read top 3 pages. ";
        message = String.format(message, results.size());
        Kv by = Kv.by("content", message);
        SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
        Tio.send(channelContext, ssePacket);
      }
    } else {
      String message = "Failed to search please try again later. ";
      Kv by = Kv.by("content", message);
      SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
    }
    int max = 3;
    if (results.size() < max) {
      max = results.size();
    }

    for (int i = 0; i < max; i++) {
      SearxngResult searxngResult = results.get(i);
      String title = searxngResult.getTitle();
      String url = searxngResult.getUrl();
      if (channelContext != null) {
        String message = "Read %s. ";
        message = String.format(message, url);
        Kv by = Kv.by("content", message);
        SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
        Tio.send(channelContext, ssePacket);
      }

      pages.add(new WebPageContent(title, url));

      ResponseVo responseVo = webPageService.get(url);
      if (responseVo == null) {
        if (channelContext != null) {
          String message = "Failed to read %s. ";
          message = String.format(message, url);
          Kv by = Kv.by("content", message);
          SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
          Tio.send(channelContext, ssePacket);
        }
        markdown.append("source " + (i + 1) + " " + searxngResult.getContent());
      } else {
        if (responseVo.isOk()) {
          markdown.append("source " + (i + 1) + " " + responseVo.getBodyString());
        } else {
          markdown.append("source " + (i + 1) + " " + searxngResult.getContent());
        }
      }
    }

    if (channelContext != null) {
      SsePacket ssePacket = new SsePacket(AiChatEventName.citation, JsonUtils.toSkipNullJson(pages));
      Tio.send(channelContext, ssePacket);
    }
    return markdown;
  }

  public String processMessageByChatModel(ChatAskVo vo, ChannelContext channelContext) {
    boolean stream = vo.isStream();
    Long sessionId = vo.getSession_id();
    long start = System.currentTimeMillis();
    // 添加文本
    List<UniChatMessage> messages = vo.getMessages();
    String textQuestion = vo.getUser_input_quesiton();
    messages.add(new UniChatMessage("user", textQuestion));

    OpenAiChatRequestVo chatRequestVo = new OpenAiChatRequestVo().setModel(OpenAiModels.GPT_4O_MINI)
        .setChatMessages(messages);

    long answerId = SnowflakeIdUtils.id();
    if (stream) {
      Kv kv = Kv.by("content", "- Reply to your question.\r\n\r\n");
      SsePacket packet = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(kv));
      Tio.bSend(channelContext, packet);

      chatRequestVo.setStream(true);
      // ChatOpenAiStreamCommonCallback callback = new
      // ChatOpenAiStreamCommonCallback(channelContext, vo, answerId, start,
      // textQuestion);
      ChatOpenAiEventSourceListener listener = new ChatOpenAiEventSourceListener(channelContext, vo, answerId, start,
          textQuestion);
      // Call call = OpenAiClient.chatCompletions(chatRequestVo, callback);
      EventSource eventSource = OpenAiClient.chatCompletions(chatRequestVo, listener);
      log.info("add call:{}", sessionId);
      ChatStreamCallCan.put(sessionId, eventSource);
      return null;

    } else {
      OpenAiChatResponseVo chatCompletions = OpenAiClient.chatCompletions(chatRequestVo);
      String content = chatCompletions.getChoices().get(0).getMessage().getContent();
      return content;
    }
  }
}