package com.litongjava.llm.ocr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.litongjava.gemini.GeminiCandidate;
import com.litongjava.gemini.GeminiChatRequest;
import com.litongjava.gemini.GeminiChatResponse;
import com.litongjava.gemini.GeminiClient;
import com.litongjava.gemini.GeminiContent;
import com.litongjava.gemini.GeminiInlineData;
import com.litongjava.gemini.GeminiPart;
import com.litongjava.gemini.GoogleModels;
import com.litongjava.tio.utils.base64.Base64Utils;
import com.litongjava.tio.utils.environment.EnvUtils;

public class GeminiClientImageTest {

  public void toMarkdown(Path path) {
    // Read image to base64
    byte[] readAllBytes = null;
    try {
      readAllBytes = Files.readAllBytes(path);
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (readAllBytes == null) {
      return;
    }
    String mimeType = "image/png";
    String encodeImage = Base64Utils.encodeToString(readAllBytes);
    String googleApiKey = EnvUtils.getStr("GEMINI_API_KEY");

    // 1. Build request body
    List<GeminiPart> parts = new ArrayList<>();
    parts.add(new GeminiPart("识别图片内容"));
    parts.add(new GeminiPart(new GeminiInlineData(mimeType, encodeImage)));
    GeminiContent content = new GeminiContent("user", parts);
    GeminiChatRequest reqVo = new GeminiChatRequest(Collections.singletonList(content));

    // 2. Sync request: generateContent
    GeminiChatResponse respVo = GeminiClient.generate(googleApiKey, GoogleModels.GEMINI_1_5_FLASH, reqVo);
    if (respVo != null && respVo.getCandidates() != null) {
      GeminiCandidate candidate = respVo.getCandidates().get(0);
      if (candidate.getContent() != null && candidate.getContent().getParts() != null) {
        GeminiPart partVo = candidate.getContent().getParts().get(0);
        System.out.println("Gemini answer text: " + partVo.getText());
      }
    }
    return;
  }
}