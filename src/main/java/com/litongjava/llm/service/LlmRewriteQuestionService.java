package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.template.Template;
import com.litongjava.db.activerecord.Row;
import com.litongjava.db.utils.MarkdownTableUtils;
import com.litongjava.gemini.GeminiClient;
import com.litongjava.gemini.GoogleGeminiModels;
import com.litongjava.openai.chat.ChatMessage;
import com.litongjava.openai.chat.OpenAiChatResponseVo;
import com.litongjava.openai.client.OpenAiClient;
import com.litongjava.openai.consts.OpenAiModels;
import com.litongjava.template.PromptEngine;

public class LlmRewriteQuestionService {

  public String rewriteSearchQuesiton(String question, List<ChatMessage> messages) {
    // 1.渲染模版
    Template template = PromptEngine.getTemplate("search_rewrite_question_prompt.txt");

    Map<String, Object> values = new HashMap<>();
    values.put("query", question);
    if (messages != null && messages.size() > 0) {
      values.put("table", toMarkdown(messages));
    }
    String prompt = template.renderToString(values);
    return predict(question, prompt);
  }

  public String rewrite(String question, List<ChatMessage> messages) {
    // 1.渲染模版
    Template template = PromptEngine.getTemplate("rewrite_question_prompt.txt");

    Map<String, Object> values = new HashMap<>();
    values.put("query", question);
    if (messages != null && messages.size() > 0) {
      values.put("table", toMarkdown(messages));
    }
    String prompt = template.renderToString(values);

    return predict(question, prompt);
  }

  //2.大模型推理
  private String predict(String question, String prompt) {

    //String content = openAiGpt(question, prompt);
    String content = googleGemini(prompt);
    if (content == null) {
      return question;
    } else if ("not_needed".equalsIgnoreCase(content) || "not_needed\n".equalsIgnoreCase(content) || "Not needed".equalsIgnoreCase(content)) {
      return question;
    } else if (content.startsWith("not_needed")) {
      return question;
    }
    // 3.返回推理结果
    return content;
  }

  private String googleGemini(String prompt) {
    String content = GeminiClient.chatWithModel(GoogleGeminiModels.GEMINI_2_0_FLASH_EXP, "user", prompt);
    return content;
  }

  public String openAiGpt(String question, String prompt) {
    OpenAiChatResponseVo chatCompletions = OpenAiClient.chatWithModel(OpenAiModels.GPT_4O_MINI, "system", prompt);
    String content = chatCompletions.getChoices().get(0).getMessage().getContent();
    return content;
  }

  private String toMarkdown(List<ChatMessage> messages) {

    List<Row> histories = new ArrayList<>();
    for (ChatMessage m : messages) {
      histories.add(Row.by("role", m.getRole()).set("message", m.getContent()));
    }
    return MarkdownTableUtils.to(histories);
  }
}
