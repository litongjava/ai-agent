package com.litongjava.llm.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.litongjava.gemini.GeminiCandidateVo;
import com.litongjava.gemini.GeminiChatRequestVo;
import com.litongjava.gemini.GeminiChatResponseVo;
import com.litongjava.gemini.GeminiClient;
import com.litongjava.gemini.GeminiPartVo;
import com.litongjava.gemini.GoogleGeminiModels;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.linux.LinuxClient;
import com.litongjava.linux.ProcessResult;
import com.litongjava.llm.utils.ResponseXmlTagUtils;
import com.litongjava.llm.vo.ToolVo;
import com.litongjava.template.PromptEngine;
import com.litongjava.tio.boot.admin.services.AwsS3StorageService;
import com.litongjava.tio.boot.admin.vo.UploadResultVo;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.utils.encoder.Base64Utils;
import com.litongjava.tio.utils.encoder.ImageVo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MatplotlibService {

  public ProcessResult generateMatplot(String answer) {
    String text = generateCode(answer);
    if (!text.contains("execute_python")) {
      return null;
    }
    ToolVo toolVo = null;
    try {
      toolVo = ResponseXmlTagUtils.extracted(text);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      log.error("text:{}", text, e.getMessage(), e);
      return null;
    }
    if (toolVo.getName().equals("execute_python")) {
      String code = toolVo.getContent();
      ProcessResult result = LinuxClient.executePythonCode(code);
      List<String> images = result.getImages();
      List<String> imageUrls = new ArrayList<>(images.size());
      if (images != null) {
        String imageBase64Code = result.getImages().get(0);
        ImageVo decodeImage = Base64Utils.decodeImage(imageBase64Code);
        UploadFile uploadFile = new UploadFile("matplotlib." + decodeImage.getExtension(), decodeImage.getData());
        UploadResultVo resultVo = Aop.get(AwsS3StorageService.class).uploadFile("matplotlib", uploadFile);
        String url = resultVo.getUrl();
        imageUrls.add(url);
      }
      result.setImages(imageUrls);
      return result;
    }
    return null;
  }

  public String generateCode(String answer) {
    String systemPrompt = PromptEngine.renderToString("python_code_prompt.txt");
    String userPrompt = "请根据下面的内容使用python代码绘制函数图像.如果无需绘制函数图像,请返回`not_needed`";

    // 1. Construct request body
    GeminiChatRequestVo reqVo = new GeminiChatRequestVo();
    reqVo.setUserPrompt(userPrompt, answer);
    reqVo.setSystemPrompt(systemPrompt);

    // 2. Send sync request: generateContent
    GeminiChatResponseVo respVo = GeminiClient.generate(GoogleGeminiModels.GEMINI_2_0_FLASH, reqVo);
    if (respVo != null) {
      List<GeminiCandidateVo> candidates = respVo.getCandidates();
      GeminiCandidateVo candidate = candidates.get(0);
      List<GeminiPartVo> parts = candidate.getContent().getParts();
      if (candidate != null && candidate.getContent() != null && parts != null) {
        String text = parts.get(0).getText();
        return text;
      }
    }
    return null;
  }

}
