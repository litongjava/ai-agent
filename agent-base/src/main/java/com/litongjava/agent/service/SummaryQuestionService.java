package com.litongjava.agent.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.template.Template;
import com.litongjava.chat.UniChatClient;
import com.litongjava.chat.UniChatMessage;
import com.litongjava.chat.UniChatRequest;
import com.litongjava.chat.UniChatResponse;
import com.litongjava.consts.ModelPlatformName;
import com.litongjava.openrouter.OpenRouterModels;
import com.litongjava.template.PromptEngine;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SummaryQuestionService {

  public String summary(String question) {
    // 1. 渲染模板
    Template template = PromptEngine.getTemplate("summary_question_prompt.txt");
    Map<String, Object> values = new HashMap<>();
    values.put("query", question);
    String prompt = template.renderToString(values);

    // 2. 调用大模型进行推理
    UniChatMessage user = UniChatMessage.buildUser(prompt);
    List<UniChatMessage> messages = new ArrayList<>();
    messages.add(user);

    UniChatRequest uniChatRequest = new UniChatRequest(ModelPlatformName.OPENROUTER, OpenRouterModels.QWEN_QWEN3_CODER,
        messages);

    for (int i = 0; i < 3; i++) {
      try {
        UniChatResponse chatResponse = UniChatClient.generate(uniChatRequest);
        String content = chatResponse.getMessage().getContent();

        // 3. 判断结果并返回
        if ("not_needed".equals(content)) {
          return question;
        }
        return content;
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        continue;
      }

    }
    return null;

  }
}
