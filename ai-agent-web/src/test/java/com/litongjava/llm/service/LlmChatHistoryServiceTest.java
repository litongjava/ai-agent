package com.litongjava.llm.service;

import org.junit.Test;

import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.service.LlmChatHistoryService;
import nexus.io.tio.boot.admin.config.TioAdminDbConfiguration;
import nexus.io.tio.utils.environment.EnvUtils;

public class LlmChatHistoryServiceTest {

  @Test
  public void test() {
    EnvUtils.load();
    new TioAdminDbConfiguration().config();
    Aop.get(LlmChatHistoryService.class).getHistory(1L);
  }

}
