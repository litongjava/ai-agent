package com.litongjava.llm.service;

import org.junit.Test;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.tio.boot.admin.config.TioAdminDbConfiguration;
import com.litongjava.tio.utils.environment.EnvUtils;

public class LlmChatHistoryServiceTest {

  @Test
  public void test() {
    EnvUtils.load();
    new TioAdminDbConfiguration().config();
    Aop.get(LlmChatHistoryService.class).getHistory(1L);
  }

}
