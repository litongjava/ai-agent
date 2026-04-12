package com.litongjava.llm.service;

import java.io.File;

import org.junit.Test;

import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.service.ChatUploadService;
import nexus.io.model.upload.UploadFile;
import nexus.io.tio.boot.admin.config.TioAdminDbConfiguration;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.hutool.FileUtil;

public class ChatUploadServiceTest {

  @Test
  public void test() {
    EnvUtils.load();
    new TioAdminDbConfiguration().config();
    String path = "C:\\Users\\Administrator\\Pictures\\300-dpi.txt";
    UploadFile uploadFile = new UploadFile();

    File file = new File(path);
    byte[] bytes = FileUtil.readBytes(file);
    uploadFile.setName(file.getName());
    uploadFile.setSize(file.length());
    uploadFile.setData(bytes);

    Aop.get(ChatUploadService.class).upload("ai", uploadFile);
  }

}
