package com.litongjava.agent.service;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.template.Template;
import com.litongjava.chat.PlatformInput;
import com.litongjava.chat.UniChatClient;
import com.litongjava.chat.UniChatMessage;
import com.litongjava.chat.UniChatRequest;
import com.litongjava.chat.UniChatResponse;
import com.litongjava.template.PromptEngine;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SummaryQuestionService {

  public String summary(PlatformInput platformInput, String question) {
    // 1. 渲染模板
    Template template = PromptEngine.getTemplate("summary_question_prompt.txt");
    String systemPrompt = template.renderToString();
    return summary(platformInput, systemPrompt, question);
  }

  public String summary(PlatformInput platformInput, String systemPrompt, String question) {
    // 2. 调用大模型进行推理
    String prompt = "Input: " + question + ". \nSummary:";
    UniChatMessage user = UniChatMessage.buildUser(prompt);
    List<UniChatMessage> messages = new ArrayList<>();
    messages.add(user);

    UniChatRequest uniChatRequest = new UniChatRequest(platformInput);
    uniChatRequest.setSystemPrompt(systemPrompt);
    uniChatRequest.setCacheSystemPrompt(true);
    uniChatRequest.setMessages(messages);
    String content = null;
    UniChatResponse chatResponse = generate(uniChatRequest);

    content = chatResponse.getMessage().getContent();

    // 3. 判断结果并返回
    if ("not_needed".equals(content)) {
      return question;
    }
    return content;
  }

  private UniChatResponse generate(UniChatRequest uniChatRequest) {
    UniChatResponse chatResponse = null;
    for (int i = 0; i < 3; i++) {
      try {
        chatResponse = UniChatClient.generate(uniChatRequest);
        if (chatResponse == null) {
          continue;
        } else {
          break;
        }
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        continue;
      }
    }
    return chatResponse;
  }
}
