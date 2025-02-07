package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.template.Engine;
import com.jfinal.template.Template;
import com.litongjava.db.activerecord.Row;
import com.litongjava.db.utils.MarkdownTableUtils;
import com.litongjava.openai.chat.ChatMessage;
import com.litongjava.openai.chat.OpenAiChatResponseVo;
import com.litongjava.openai.client.OpenAiClient;
import com.litongjava.openai.constants.OpenAiModels;

public class LlmRewriteQuestionService {

  public String rewrite(String question, List<ChatMessage> messages) {

    // 1.渲染模版
    Engine engine = Engine.use();
    Template template = engine.getTemplate("rewrite_question_prompt.txt");

    Map<String, Object> values = new HashMap<>();
    values.put("table", toMarkdown(messages));
    values.put("query", question);
    String prompt = template.renderToString(values);

    // 2.大模型推理
    OpenAiChatResponseVo chatCompletions = OpenAiClient.chatWithModel(OpenAiModels.GPT_4O_MINI, "system", prompt);
    String content = chatCompletions.getChoices().get(0).getMessage().getContent();
    if ("not_needed".equalsIgnoreCase(content) || "Not needed".equalsIgnoreCase(content)) {
      return question;
    }
    // 3.返回推理结果
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
