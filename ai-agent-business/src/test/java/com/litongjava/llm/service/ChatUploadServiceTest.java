package com.litongjava.llm.service;

import java.io.File;

import org.junit.Test;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.model.upload.UploadFile;
import com.litongjava.tio.boot.admin.config.TioAdminDbConfiguration;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.FileUtil;

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
