package com.litongjava.llm.service;

import com.litongjava.jfinal.aop.Aop;

public class AgentPromptService {
  private PromptService promptService = Aop.get(PromptService.class);
  public String renderGeoGebraPrompt() {
    String fileName = "geogebra_prompt.txt";
    // Kv by = Kv.by("data", inputQestion);
    String systemPrompt = promptService.render(fileName);
    return systemPrompt;
  }
}
