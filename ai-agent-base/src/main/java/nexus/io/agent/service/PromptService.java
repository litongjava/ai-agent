package nexus.io.agent.service;

import com.jfinal.kit.Kv;

import nexus.io.agent.consts.AgentConfigKeys;
import nexus.io.template.PromptEngine;
import nexus.io.tio.utils.environment.EnvUtils;

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
  
  public String render(String key) {
    String textQuestion;
    boolean b = EnvUtils.getBoolean(AgentConfigKeys.AI_PROMPT_LOAD_FROM_DB);
    if (b) {
      textQuestion = PromptEngine.renderToStringFromDb(key);
    } else {
      textQuestion = PromptEngine.renderToString(key);
    }
    return textQuestion;
  }
}
