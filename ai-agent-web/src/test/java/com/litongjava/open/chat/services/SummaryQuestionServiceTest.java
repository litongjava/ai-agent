package com.litongjava.open.chat.services;

import org.junit.Test;

import com.litongjava.agent.service.SummaryQuestionService;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.tio.boot.admin.config.TioAdminEnjoyEngineConfig;
import com.litongjava.tio.utils.environment.EnvUtils;

public class SummaryQuestionServiceTest {

  @Test
  public void test() {
    EnvUtils.load();
    new TioAdminEnjoyEngineConfig();
    SummaryQuestionService summaryQuestionService = Aop.get(SummaryQuestionService.class);
    String summary = summaryQuestionService.summary("What is the first day of sjsu in Fall 2024");
    System.out.println(summary);
  }
//
//  @Test
//  public void summaryQuestionSaveTest() {
//    EnvUtils.load();
//    new DbConfig().config();
//    String summaryQuestion = "First day of SJSU in Fall 2024";
//    String chatId = "6ad28222-3d8d-4fa3-ac30-80469b7ee056";
//    Row record = new Row().set("name", summaryQuestion);
//    boolean update = ApiTable.update(TableNames.llm_chat_session, "id", chatId, record);
//    System.out.println(update);
//  }

}
