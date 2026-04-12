package com.litongjava.llm.handler;

import java.io.File;

import org.junit.Test;

import nexus.io.ai.agent.config.AgentWebAppConfig;
import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.service.AiDocumentParseService;
import nexus.io.model.TaskResponse;
import nexus.io.model.body.RespBodyVo;
import nexus.io.model.upload.UploadFile;
import nexus.io.tio.boot.testing.TioBootTest;
import nexus.io.tio.utils.hutool.FileUtil;
import nexus.io.tio.utils.json.JsonUtils;

public class DocuementParseServiceTest {

  @Test
  public void testParse() {
    TioBootTest.runWith(AgentWebAppConfig.class);
    File file = new File("F:\\my_document\\subject-docs\\06_ICS\\ICS241\\ICS241_Topic1_Intro.pdf");
    String name = file.getName();
    byte[] bytes = FileUtil.readBytes(file);
    UploadFile uploadFile = new UploadFile(name, bytes);
    RespBodyVo body = Aop.get(AiDocumentParseService.class).parse(uploadFile);
    System.out.println(JsonUtils.toJson(body));
  }

  @Test
  public void getTask() {
    TioBootTest.runWith(AgentWebAppConfig.class);
    long taskId = 580332660355862528L;
    TaskResponse task = Aop.get(AiDocumentParseService.class).getTask(taskId);
    System.out.println(task.getResult());
  }
}
