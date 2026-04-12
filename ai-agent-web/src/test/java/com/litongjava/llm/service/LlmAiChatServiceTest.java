package com.litongjava.llm.service;

import org.junit.Test;

import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.service.WebPageService;
import nexus.io.model.http.response.ResponseVo;
import nexus.io.searxng.SearxngSearchClient;
import nexus.io.searxng.SearxngSearchResponse;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.json.JsonUtils;

public class LlmAiChatServiceTest {

  @Test
  public void test() {
    EnvUtils.load();
    EnvUtils.set("SEARXNG_API_BASE", "https://searx-ng.fly.dev");
    String textQuestion = "site:sjsu.edu I am a 4th year business administration entrepreneurship student pursuing a BS, what classes should I take my last semester";
    SearxngSearchResponse searchResponse = SearxngSearchClient.search(textQuestion);
    System.out.println(JsonUtils.toSkipNullJson(searchResponse));
    String url = searchResponse.getResults().get(0).getUrl();
  }
  
  @Test
  public void testGetMarkdown() {
    ResponseVo responseVo = Aop.get(WebPageService.class).get("https://catalog.sjsu.edu/preview_program.php?catoid=13&poid=7613");
    System.out.println(responseVo.getBodyString());
  }
}
