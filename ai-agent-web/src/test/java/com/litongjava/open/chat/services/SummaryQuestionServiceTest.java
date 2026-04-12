package com.litongjava.open.chat.services;

import org.junit.Test;

import nexus.io.agent.service.SummaryQuestionService;
import nexus.io.chat.PlatformInput;
import nexus.io.consts.ModelPlatformName;
import nexus.io.jfinal.aop.Aop;
import nexus.io.openai.consts.OpenAiModels;
import nexus.io.tio.boot.admin.config.TioAdminEnjoyEngineConfig;
import nexus.io.tio.utils.environment.EnvUtils;

public class SummaryQuestionServiceTest {

  @Test
  public void test() {
    EnvUtils.load();
    new TioAdminEnjoyEngineConfig();
    SummaryQuestionService summaryQuestionService = Aop.get(SummaryQuestionService.class);
    PlatformInput platformInput = new PlatformInput(ModelPlatformName.OPENAI, OpenAiModels.GPT_5);
    String summary = summaryQuestionService.summary(platformInput, "What is the first day of sjsu in Fall 2024");
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
