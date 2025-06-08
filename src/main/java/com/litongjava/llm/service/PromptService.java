package com.litongjava.llm.service;

import com.jfinal.kit.Kv;
import com.litongjava.llm.consts.AgentConfigKeys;
import com.litongjava.template.PromptEngine;
import com.litongjava.tio.utils.environment.EnvUtils;

public class PromptService {

  public String render(String key, Kv by) {
    String textQuestion;
    boolean b = EnvUtils.getBoolean(AgentConfigKeys.AI_PROMPT_LOAD_FROM_DB);
    if (b) {
      textQuestion = PromptEngine.renderToStringFromDb(key, by);
    } else {
      textQuestion = PromptEngine.renderToString(key, by);
    }
    return textQuestion;
  }
}
