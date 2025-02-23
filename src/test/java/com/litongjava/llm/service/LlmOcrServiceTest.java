package com.litongjava.llm.service;

import java.io.File;

import org.junit.Test;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.FileUtil;

public class LlmOcrServiceTest {

  @Test
  public void test() {
    EnvUtils.load();
    String path = "C:\\Users\\Administrator\\Pictures\\200-dpi.png";
    File file = new File(path);
    byte[] bytes = FileUtil.readBytes(file);
    String string = Aop.get(LlmOcrService.class).parse(bytes, file.getName());
    System.out.println(string);
  }

}
