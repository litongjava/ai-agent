package com.litongjava.llm.handler;

import java.io.File;

import org.junit.Test;

import com.litongjava.ai.agent.config.AdminAppConfig;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.service.AiDocumentParseService;
import com.litongjava.model.TaskResponse;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.model.upload.UploadFile;
import com.litongjava.tio.boot.testing.TioBootTest;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.json.JsonUtils;

public class DocuementParseServiceTest {

  @Test
  public void testParse() {
    TioBootTest.runWith(AdminAppConfig.class);
    File file = new File("F:\\my_document\\subject-docs\\06_ICS\\ICS241\\ICS241_Topic1_Intro.pdf");
    String name = file.getName();
    byte[] bytes = FileUtil.readBytes(file);
    UploadFile uploadFile = new UploadFile(name, bytes);
    RespBodyVo body = Aop.get(AiDocumentParseService.class).parse(uploadFile);
    System.out.println(JsonUtils.toJson(body));
  }

  @Test
  public void getTask() {
    TioBootTest.runWith(AdminAppConfig.class);
    long taskId = 580332660355862528L;
    TaskResponse task = Aop.get(AiDocumentParseService.class).getTask(taskId);
    System.out.println(task.getResult());
  }
}
