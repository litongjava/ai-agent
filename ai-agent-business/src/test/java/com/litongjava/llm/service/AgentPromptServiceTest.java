package com.litongjava.llm.service;

import org.junit.Test;

import com.litongjava.jfinal.aop.Aop;

public class AgentPromptServiceTest {

  @Test
  public void testRenderGeoGebraPrompt() {
    String prompt = Aop.get(AgentPromptService.class).renderGeoGebraPrompt();
    System.out.println(prompt);
  }

}
