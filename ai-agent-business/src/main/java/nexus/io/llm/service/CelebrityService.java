package nexus.io.llm.service;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import nexus.io.chat.ChatMessageArgs;
import nexus.io.http.common.sse.SsePacket;
import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.consts.AiChatEventName;
import nexus.io.model.web.WebPageContent;
import nexus.io.searxng.SearxngResult;
import nexus.io.searxng.SearxngSearchClient;
import nexus.io.searxng.SearxngSearchParam;
import nexus.io.searxng.SearxngSearchResponse;
import nexus.io.template.PromptEngine;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.Tio;
import nexus.io.tio.utils.hutool.StrUtil;
import nexus.io.tio.utils.json.FastJson2Utils;
import nexus.io.tio.utils.json.JsonUtils;

@Slf4j
public class CelebrityService {

  private LinkedInService linkedInService = Aop.get(LinkedInService.class);
  private SocialMediaService socialMediaService = Aop.get(SocialMediaService.class);
  
  public String celebrity(ChannelContext channelContext, ChatMessageArgs chatSendArgs) {
    String name = chatSendArgs.getName();
    String institution = chatSendArgs.getInstitution();
    //必须要添加两个institution,添加后搜索更准,但是不知道原理是什么?猜测是搜索引擎提高了权重
    String textQuestion = name + " (" + institution + ")" + " at " + institution;

    if (channelContext != null) {
      Kv by = Kv.by("content", "First let me search google with " + textQuestion + ". ");
      SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
      Tio.send(channelContext, ssePacket);
    }

    SearxngSearchResponse searchResponse = Aop.get(SearchService.class).searchapi(textQuestion);
    List<SearxngResult> results = searchResponse.getResults();

    //SearxngSearchResponse searchResponse = SearxngSearchClient.search(textQuestion);
    //SearxngSearchResponse searchResponse2 = Aop.get(SearchService.class).google("site:linkedin.com/in/ " + name + " at " + institution);
    //    List<SearxngResult> results2 = searchResponse2.getResults();
    //    for (SearxngResult searxngResult : results2) {
    //      results.add(searxngResult);
    //    }
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
      if(url.startsWith("https://www.linkedin.com/in/")) {
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
    String systemPrompt = PromptEngine.renderToStringFromDb("celebrity_prompt.txt", kv);
    return systemPrompt;
  }
}
