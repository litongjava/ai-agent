package com.litongjava.llm.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jfinal.kit.Kv;
import com.litongjava.db.activerecord.Row;
import com.litongjava.google.search.GoogleCustomSearchClient;
import com.litongjava.google.search.GoogleCustomSearchResponse;
import com.litongjava.google.search.SearchResultItem;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.callback.ChatOpenAiStreamCommonCallback;
import com.litongjava.llm.can.ChatStreamCallCan;
import com.litongjava.llm.config.AiAgentContext;
import com.litongjava.llm.consts.AgentMessageType;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.consts.ApiChatSendType;
import com.litongjava.llm.dao.SchoolDictDao;
import com.litongjava.llm.utils.AgentBotQuestionUtils;
import com.litongjava.llm.vo.AiChatResponseVo;
import com.litongjava.llm.vo.ApiChatSendVo;
import com.litongjava.llm.vo.ChatParamVo;
import com.litongjava.llm.vo.ChatSendArgs;
import com.litongjava.llm.vo.SchoolDict;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.model.web.WebPageContent;
import com.litongjava.openai.chat.ChatMessage;
import com.litongjava.openai.chat.OpenAiChatRequestVo;
import com.litongjava.openai.chat.OpenAiChatResponseVo;
import com.litongjava.openai.client.OpenAiClient;
import com.litongjava.openai.constants.OpenAiModels;
import com.litongjava.searxng.SearxngResult;
import com.litongjava.searxng.SearxngSearchClient;
import com.litongjava.searxng.SearxngSearchParam;
import com.litongjava.searxng.SearxngSearchResponse;
import com.litongjava.template.PromptEngine;
import com.litongjava.tio.boot.admin.vo.UploadResultVo;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.sse.SsePacket;
import com.litongjava.tio.http.server.util.SseEmitter;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.json.FastJson2Utils;
import com.litongjava.tio.utils.json.JsonUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;
import com.litongjava.tio.utils.thread.TioThreadUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;

@Slf4j
public class LlmAiChatService {

  LLmChatDispatcherService dispatcherService = Aop.get(LLmChatDispatcherService.class);
  LinkedInService linkedInService = Aop.get(LinkedInService.class);
  SocialMediaService socialMediaService = Aop.get(SocialMediaService.class);
  WebPageService webPageService = Aop.get(WebPageService.class);
  LlmRewriteQuestionService llmRewriteQuestionService = Aop.get(LlmRewriteQuestionService.class);

  public RespBodyVo index(ChannelContext channelContext, ApiChatSendVo apiSendVo) {
    /**
     * inputQestion 用户输入的问题
     * textQuestion 用户输入的问题和提示词
     */
    List<ChatMessage> messages = apiSendVo.getMessages();
    String inputQestion = null;
    String textQuestion = null;
    if (messages != null && messages.size() > 0) {
      inputQestion = messages.get(0).getContent();
      messages.remove(0);
      textQuestion = inputQestion;
    }

    apiSendVo.setInput_quesiton(inputQestion);

    // save file content to history
    ChatParamVo chatParamVo = new ChatParamVo();
    String type = apiSendVo.getType();
    boolean stream = apiSendVo.isStream();
    Long schoolId = apiSendVo.getSchool_id();
    String userId = apiSendVo.getUser_id();
    Long sessionId = apiSendVo.getSession_id();
    Long appId = apiSendVo.getApp_id();
    List<Long> file_ids = apiSendVo.getFile_ids();
    ChatSendArgs chatSendArgs = apiSendVo.getArgs();

    SchoolDict schoolDict = null;

    // 1.查询学校
    if (schoolId != null) {
      try {
        schoolDict = Aop.get(SchoolDictDao.class).getSchoolById(schoolId.longValue());
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
    if (ApiChatSendType.translator.equals(type)) {
      if (StrUtil.isNotBlank(inputQestion)) {
        textQuestion = PromptEngine.renderToString("translator_prompt.txt", Kv.by("data", inputQestion));
      } else {
        return RespBodyVo.fail("input question can not be empty");
      }
    } else if (ApiChatSendType.celebrity.equals(type)) {
      String name = chatSendArgs.getName();
      String institution = chatSendArgs.getInstitution();
      inputQestion = name + " at " + institution;
      textQuestion = inputQestion;
    } else if (ApiChatSendType.perplexity.equals(type)) {
      if (schoolDict != null) {
        textQuestion += (" at " + schoolDict.getFull_name());
      }
    }

    if (textQuestion != null) {
      if (stream) {
        SsePacket packet = new SsePacket(AiChatEventName.question, textQuestion);
        Tio.bSend(channelContext, packet);
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
    }

    // 3.查询历史
    LlmChatHistoryService llmChatHistoryService = Aop.get(LlmChatHistoryService.class);
    if (apiSendVo.isRewrite()) {
      Long previous_question_id = apiSendVo.getPrevious_question_id();
      Long previous_answer_id = apiSendVo.getPrevious_answer_id();
      llmChatHistoryService.remove(previous_question_id, previous_answer_id);
    }

    AiChatResponseVo aiChatResponseVo = new AiChatResponseVo();
    List<Row> histories = null;
    if (!ApiChatSendType.translator.equals(type)) {
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

    if (histories.size() > 20) {
      String message = "Dear user, your conversation count has exceeded the maximum length for multiple rounds of conversation. "
          //
          + "Please start a new session. Your new question might be:" + textQuestion;

      long answerId = SnowflakeIdUtils.id();
      aiChatResponseVo.setAnswerId(answerId);

      llmChatHistoryService.saveAssistant(answerId, sessionId, message);
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

    if (stream) {
      SsePacket ssePacket = new SsePacket(AiChatEventName.progress, ("The number of history records to be queried:" + histories.size()).getBytes());
      Tio.bSend(channelContext, ssePacket);
    }

    // 4.处理历史记录,拼接历史消息
    boolean isFirstQuestion = false;
    List<ChatMessage> historyMessage = new ArrayList<>();
    if (histories == null || histories.size() < 1) {
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

    // 5.记录问题
    // save to the user question to db
    long questionId = SnowflakeIdUtils.id();
    List<UploadResultVo> fileInfo = null;
    try {
      if (file_ids != null) {
        fileInfo = Aop.get(ChatUploadService.class).getFileBasicInfoByIds(file_ids);
        chatParamVo.setUploadFiles(fileInfo);

        llmChatHistoryService.saveUser(questionId, sessionId, inputQestion, fileInfo);
      } else {
        llmChatHistoryService.saveUser(questionId, sessionId, inputQestion);
      }
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

    // 6.发送问题通知
    if (StrUtil.isNotEmpty(textQuestion)) {
      StringBuffer stringBuffer = new StringBuffer();

      stringBuffer.append("app env:").append(EnvUtils.getStr("app.env")).append("\n")
          //
          .append("userId:").append(userId).append("\n")//
          .append("schooL id:").append(schoolId).append("\n");
      if (schoolDict != null) {
        stringBuffer.append("schooL name:").append(schoolDict.getFull_name()).append("\n");
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
    // 7.重写问题
    boolean isRewriteQuesiton = false;
    if (textQuestion != null) {
      if (textQuestion.length() > 76) {
        isRewriteQuesiton = true;
      } else if (historyMessage.size() > 1) {
        isRewriteQuesiton = true;
      }
    }
    if (isRewriteQuesiton) {
      if (!ApiChatSendType.advise.equals(type)) {
        textQuestion = llmRewriteQuestionService.rewrite(textQuestion, historyMessage);
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("user_id:").append(userId).append("\n")
            //
            .append("chat_id").append(sessionId).append("\n");
        stringBuffer.append("question:").append(textQuestion).append("\n");
        //
        stringBuffer.append("history:" + JsonUtils.toSkipNullJson(historyMessage)).append("\n");
        //
        stringBuffer.append("rewrite:" + textQuestion);

        AiAgentContext.me().getNotification().sendRewrite(stringBuffer.toString());
        log.info("rewrite question:{}", textQuestion);

        if (stream && channelContext != null) {
          Kv kv = Kv.by("question", textQuestion).set("history", historyMessage).set("rewrited", textQuestion);
          SsePacket packet = new SsePacket(AiChatEventName.rewrite, JsonUtils.toJson(kv));
          Tio.bSend(channelContext, packet);
        }
        aiChatResponseVo.setRewrite(textQuestion);
        chatParamVo.setRewriteQuestion(textQuestion);
      }
    }

    // 8.判断类型
    if (ApiChatSendType.search.equals(type)) {
      if (StrUtil.isNotBlank(textQuestion)) {
        log.info("search:{}", textQuestion);
        String systemPrompt = search(channelContext, textQuestion);
        chatParamVo.setSystemPrompt(systemPrompt);
      }
    } else if (ApiChatSendType.advise.equals(type)) {
      if (StrUtil.isNotBlank(textQuestion)) {
        String systemPrompt = advise(channelContext, textQuestion, historyMessage, schoolDict);
        chatParamVo.setSystemPrompt(systemPrompt);
      }
    } else if (ApiChatSendType.celebrity.equals(type)) {
      log.info("celebrity:{}", textQuestion);
      String systemPrompt = celebrity(channelContext, chatSendArgs);
      chatParamVo.setSystemPrompt(systemPrompt);
      if (systemPrompt == null) {
        if (channelContext != null) {
          SseEmitter.closeChunkConnection(channelContext);
        }
        return RespBodyVo.fail("Failed to search celebrity");
      }
    }

    //9.处理问题
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
      dispatcherService.predict(apiSendVo, chatParamVo, aiChatResponseVo);
      return RespBodyVo.ok(aiChatResponseVo);
    }
  }

  private String celebrity(ChannelContext channelContext, ChatSendArgs chatSendArgs) {
    String name = chatSendArgs.getName();
    String institution = chatSendArgs.getInstitution();
    //必须要添加两个institution,添加后搜索更准,但是不知道原理是什么?猜测是搜索引擎提高了权重
    String textQuestion = name + " (" + institution + ")" + " at " + institution;

    if (channelContext != null) {
      Kv by = Kv.by("content", "First let me search google with " + textQuestion + ". ");
      SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
    }

    SearxngSearchResponse searchResponse = SearxngSearchClient.search(textQuestion);
    List<SearxngResult> results = searchResponse.getResults();
    if (results != null && results.size() < 1) {
      Kv by = Kv.by("content", "unfortunate Failed to search.I will try again a 3 seconds. ");
      SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      searchResponse = SearxngSearchClient.search(textQuestion);
      results = searchResponse.getResults();

      if (results != null && results.size() < 1) {
        by = Kv.by("content", "unfortunate Failed to search.I will try again a 3 seconds. ");
        ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
        Tio.send(channelContext, ssePacket);
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        searchResponse = SearxngSearchClient.search(textQuestion);
        results = searchResponse.getResults();
      }
    }
    if (results != null && results.size() < 1) {
      Kv by = Kv.by("content", "unfortunate Failed to search.Please try again later. ");
      SsePacket ssePacket = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
      return null;
    }
    List<WebPageContent> pages = new ArrayList<>();
    StringBuffer markdown = new StringBuffer();
    StringBuffer sources = new StringBuffer();
    for (int i = 0; i < results.size(); i++) {
      SearxngResult searxngResult = results.get(i);
      String title = searxngResult.getTitle();
      String url = searxngResult.getUrl();
      pages.add(new WebPageContent(title, url));

      markdown.append("source " + (i + 1) + " " + searxngResult.getContent());
      String content = searxngResult.getContent();
      sources.append("source " + (i + 1) + ":").append(title).append(" ").append("url:").append(url).append(" ")
          //
          .append("content:").append(content).append("\r\n");
    }

    if (channelContext != null) {
      SsePacket ssePacket = new SsePacket(AiChatEventName.citation, JsonUtils.toSkipNullJson(pages));
      Tio.send(channelContext, ssePacket);
    }

    if (channelContext != null) {
      Kv by = Kv.by("content", "Second let me extra social media account with " + textQuestion + ".");
      SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
    }

    String soicalMediaAccounts = socialMediaService.extraSoicalMedia(name, institution, sources.toString());
    if (channelContext != null) {
      SsePacket ssePacket = new SsePacket(AiChatEventName.social_media, soicalMediaAccounts);
      Tio.send(channelContext, ssePacket);
    }

    if (channelContext != null) {
      Kv by = Kv.by("content", "Third let me search video with " + textQuestion + ". ");
      SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
    }
    SearxngSearchParam param = new SearxngSearchParam();
    param.setFormat("json").setQ(textQuestion).setCategories("videos");
    searchResponse = SearxngSearchClient.search(param);
    results = searchResponse.getResults();
    pages = new ArrayList<>();
    for (int i = 0; i < results.size(); i++) {
      SearxngResult searxngResult = results.get(i);
      String title = searxngResult.getTitle();
      String url = searxngResult.getUrl();
      pages.add(new WebPageContent(title, url));
    }

    if (channelContext != null) {
      SsePacket ssePacket = new SsePacket(AiChatEventName.video, JsonUtils.toSkipNullJson(pages));
      Tio.send(channelContext, ssePacket);
    }

    if (channelContext != null) {
      Kv by = Kv.by("content", "Forth let me search linkedin with " + name + " " + institution + ". ");
      SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
    }
    //SearxngSearchResponse person = LinkedinSearch.person(name, institution);
    //List<SearxngResult> personResults = person.getResults();
    //if (personResults != null && personResults.size() > 0) {
    //String url = personResults.get(0).getUrl();

    String profile = null;
    String url = null;
    try {
      JSONArray social_media = FastJson2Utils.parseObject(soicalMediaAccounts).getJSONArray("social_media");
      for (int i = 0; i < social_media.size(); i++) {
        JSONObject jsonObject = social_media.getJSONObject(i);
        if ("LinkedIn".equals(jsonObject.getString("platform"))) {
          url = jsonObject.getString("url");
          break;
        }
      }
    } catch (Exception e) {
      Kv by = Kv.by("content", "unfortunate Failed to find linkedin url. ");
      SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
      log.error(e.getMessage(), e);
    }

    if (StrUtil.isNotBlank(url)) {
      if (channelContext != null) {
        Kv by = Kv.by("content", "Fith let me read linkedin profile " + url + ". ");
        SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
        Tio.send(channelContext, ssePacket);
      }
      try {
        profile = linkedInService.profileScraper(url);
        if (profile != null) {
          SsePacket ssePacket = new SsePacket(AiChatEventName.linkedin, profile);
          Tio.send(channelContext, ssePacket);
        }
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        Kv by = Kv.by("content", "unfortunate Failed to read linkedin profile " + url + ". ");
        SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
        Tio.send(channelContext, ssePacket);
      }

      if (channelContext != null) {
        Kv by = Kv.by("content", "Sixth let me read linkedin posts " + url + ". ");
        SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
        Tio.send(channelContext, ssePacket);
      }

      try {
        String profilePosts = linkedInService.profilePostsScraper(url);
        if (profilePosts != null) {
          SsePacket ssePacket = new SsePacket(AiChatEventName.linkedin_posts, profilePosts);
          Tio.send(channelContext, ssePacket);
        }
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        Kv by = Kv.by("content", "unfortunate Failed to read linkedin profile posts " + url + ". ");
        SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
        Tio.send(channelContext, ssePacket);
      }
    }

    if (channelContext != null) {
      Kv by = Kv.by("content", "Then let me summary all information and generate user information. ");
      SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
    }
    // 3. 使用 PromptEngine 模版引擎填充提示词
    Kv kv = Kv.by("name", name).set("institution", institution).set("info", markdown).set("profile", profile);
    String systemPrompt = PromptEngine.renderToString("celebrity_prompt.txt", kv);
    return systemPrompt;
  }

  private String search(ChannelContext channelContext, String textQuestion) {
    SearxngSearchResponse searchResponse = SearxngSearchClient.search(textQuestion);
    List<SearxngResult> results = searchResponse.getResults();
    List<WebPageContent> pages = new ArrayList<>();
    StringBuffer markdown = new StringBuffer();
    for (int i = 0; i < results.size(); i++) {
      SearxngResult searxngResult = results.get(i);
      String title = searxngResult.getTitle();
      String url = searxngResult.getUrl();
      pages.add(new WebPageContent(title, url));
      markdown.append("source " + (i + 1) + " " + searxngResult.getContent());
    }
    if (channelContext != null) {
      SsePacket ssePacket = new SsePacket(AiChatEventName.citation, JsonUtils.toSkipNullJson(pages));
      Tio.send(channelContext, ssePacket);
    }

    String isoTimeStr = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    // 3. 使用 PromptEngine 模版引擎填充提示词
    Kv kv = Kv.by("date", isoTimeStr).set("context", markdown);
    String systemPrompt = PromptEngine.renderToString("WebSearchResponsePrompt.txt", kv);
    return systemPrompt;
  }

  private String advise(ChannelContext channelContext, String textQuestion, List<ChatMessage> historyMessage, SchoolDict schoolDict) {
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
    StringBuffer markdown = useGoogle(channelContext, textQuestion);

    String isoTimeStr = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    // 3. 使用 PromptEngine 模版引擎填充提示词
    Kv kv = Kv.by("date", isoTimeStr).set("context", markdown);
    String systemPrompt = PromptEngine.renderToString("WebSearchResponsePrompt.txt", kv);
    if (EnvUtils.isDev()) {
      log.info(systemPrompt);
    }
    return systemPrompt;
  }

  private StringBuffer useGoogle(ChannelContext channelContext, String textQuestion) {
    GoogleCustomSearchResponse searchResponse = GoogleCustomSearchClient.search(textQuestion);
    List<SearchResultItem> results = searchResponse.getItems();
    log.info("found page size:{}", results.size());

    List<WebPageContent> pages = new ArrayList<>();
    StringBuffer markdown = new StringBuffer();
    if (results.size() > 0) {
      if (channelContext != null) {
        String message = "Third I found %d web pages. let me read top 4 pages. ";
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
    int max = 4;
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

  public String processMessageByChatModel(ApiChatSendVo vo, ChannelContext channelContext) {
    boolean stream = vo.isStream();
    Long sessionId = vo.getSession_id();
    long start = System.currentTimeMillis();
    // 添加文本
    List<ChatMessage> messages = vo.getMessages();
    messages.add(new ChatMessage("user", vo.getInput_quesiton()));

    OpenAiChatRequestVo chatRequestVo = new OpenAiChatRequestVo().setModel(OpenAiModels.GPT_4O_MINI).setChatMessages(messages);

    long answerId = SnowflakeIdUtils.id();
    if (stream) {
      Kv kv = Kv.by("content", "- Reply to your question.\r\n\r\n");
      SsePacket packet = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(kv));
      Tio.bSend(channelContext, packet);

      chatRequestVo.setStream(true);
      ChatOpenAiStreamCommonCallback callback = new ChatOpenAiStreamCommonCallback(channelContext, vo, answerId, start);
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