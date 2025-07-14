package com.litongjava.llm.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

public class PptxUtils {

  /**
   * 从pptx字节数据中提取文本内容
   * @param fileData pptx文件的字节数组
   * @return 提取的文本内容
   * @throws IOException 如果pptx解析失败
   */
  public static String parsePptx(byte[] fileData) throws IOException {
    StringBuilder content = new StringBuilder();
    try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(fileData))) {
      int slideIndex = 1;
      for (XSLFSlide slide : slideShow.getSlides()) {
        content.append("Slide ").append(slideIndex++).append(":\n");
        for (XSLFShape shape : slide.getShapes()) {
          if (shape instanceof XSLFTextShape) {
            XSLFTextShape textShape = (XSLFTextShape) shape;
            content.append(textShape.getText()).append("\n");
          }
        }
        content.append("----- End of Slide -----\n");
      }
    }
    return content.toString().trim();
  }
}
