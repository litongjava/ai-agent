package com.litongjava.llm.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.litongjava.llm.vo.ToolVo;

public class ResponseXmlTagUtils {

  public static ToolVo extracted(String xmlContent) throws ParserConfigurationException, SAXException, IOException {
    if (xmlContent.contains("execute_python")) {
      int index = xmlContent.lastIndexOf("<execute_python>");
      int lastIndex = xmlContent.lastIndexOf("</execute_python>");
      if (index > 0) {
        xmlContent = xmlContent.substring(index, lastIndex + 17).trim();
      }
    }
    // 将字符串转换为 InputStream
    ByteArrayInputStream input = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));

    // 创建 DocumentBuilderFactory 实例
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    // 创建 DocumentBuilder 对象
    DocumentBuilder builder = factory.newDocumentBuilder();
    // 解析 XML 内容
    Document document = null;
    try {
      document = builder.parse(input);
    } catch (Exception e) {
      throw new RuntimeException(xmlContent, e);
    }

    document.getDocumentElement().normalize();

    // 获取 execute_python 标签
    ToolVo toolVo = new ToolVo();
    NodeList executePythonList = document.getElementsByTagName("execute_python");
    for (int i = 0; i < executePythonList.getLength(); i++) {
      Element executePythonElement = (Element) executePythonList.item(i);
      toolVo.setName("execute_python");
      // 获取 execute_python 标签内的 content 标签
      NodeList contentList = executePythonElement.getElementsByTagName("content");
      for (int j = 0; j < contentList.getLength(); j++) {
        Element contentElement = (Element) contentList.item(j);
        String trim = contentElement.getTextContent().trim();
        toolVo.setContent(trim);
      }
    }
    return toolVo;
  }
}
