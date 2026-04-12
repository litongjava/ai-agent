package com.litongjava.llm.service;

import org.junit.Test;

import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.service.AgentPromptService;

public class AgentPromptServiceTest {

  @Test
  public void testRenderGeoGebraPrompt() {
    String prompt = Aop.get(AgentPromptService.class).renderGeoGebraPrompt();
    System.out.println(prompt);
  }

}
