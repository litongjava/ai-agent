package nexus.io.llm.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.template.Template;

import nexus.io.chat.UniChatMessage;
import nexus.io.db.activerecord.Row;
import nexus.io.db.utils.MarkdownTableUtils;
import nexus.io.gemini.GeminiClient;
import nexus.io.gemini.GoogleModels;
import nexus.io.openai.chat.OpenAiChatResponse;
import nexus.io.openai.client.OpenAiClient;
import nexus.io.openai.consts.OpenAiModels;
import nexus.io.template.PromptEngine;

public class LlmRewriteQuestionService {

  public String rewriteSearchQuesiton(String question, List<UniChatMessage> messages) {
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

  public String rewrite(String question, List<UniChatMessage> messages) {
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
    String content = GeminiClient.chatWithModel(GoogleModels.GEMINI_2_0_FLASH_EXP, "user", prompt);
    return content;
  }

  public String openAiGpt(String question, String prompt) {
    OpenAiChatResponse chatCompletions = OpenAiClient.chatWithModel(OpenAiModels.GPT_4O_MINI, "system", prompt);
    String content = chatCompletions.getChoices().get(0).getMessage().getContent();
    return content;
  }

  private String toMarkdown(List<UniChatMessage> messages) {

    List<Row> histories = new ArrayList<>();
    for (UniChatMessage m : messages) {
      histories.add(Row.by("role", m.getRole()).set("message", m.getContent()));
    }
    return MarkdownTableUtils.to(histories);
  }
}
