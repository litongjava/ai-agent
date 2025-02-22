package com.litongjava.llm.utils;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.litongjava.tio.utils.hutool.FileUtil;

public class PdfUtilsTest {

  @Test
  public void test() {

    //String path = "C:\\Users\\Administrator\\Pictures\\aws\\Amazon.com - Order 111-7211017-9981812.pdf";
    String path="F:\\document\\项目资料总结\\13_项目-imaginix\\college-bot\\06_文档向量\\Algorithms-JeffE-2up-free textbook-standard 1 page format.pdf";
    byte[] bytes = FileUtil.readBytes(new File(path));
    String extraText = null;
    try {
      extraText = PdfUtils.extraText(bytes);
      System.out.println(extraText);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
