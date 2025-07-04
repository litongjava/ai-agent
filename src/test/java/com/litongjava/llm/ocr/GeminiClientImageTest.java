package com.litongjava.llm.ocr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.litongjava.gemini.GeminiCandidateVo;
import com.litongjava.gemini.GeminiChatRequestVo;
import com.litongjava.gemini.GeminiChatResponseVo;
import com.litongjava.gemini.GeminiClient;
import com.litongjava.gemini.GeminiContentVo;
import com.litongjava.gemini.GeminiInlineDataVo;
import com.litongjava.gemini.GeminiPartVo;
import com.litongjava.gemini.GoogleGeminiModels;
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
    List<GeminiPartVo> parts = new ArrayList<>();
    parts.add(new GeminiPartVo("识别图片内容"));
    parts.add(new GeminiPartVo(new GeminiInlineDataVo(mimeType, encodeImage)));
    GeminiContentVo content = new GeminiContentVo("user", parts);
    GeminiChatRequestVo reqVo = new GeminiChatRequestVo(Collections.singletonList(content));

    // 2. Sync request: generateContent
    GeminiChatResponseVo respVo = GeminiClient.generate(googleApiKey, GoogleGeminiModels.GEMINI_1_5_FLASH, reqVo);
    if (respVo != null && respVo.getCandidates() != null) {
      GeminiCandidateVo candidate = respVo.getCandidates().get(0);
      if (candidate.getContent() != null && candidate.getContent().getParts() != null) {
        GeminiPartVo partVo = candidate.getContent().getParts().get(0);
        System.out.println("Gemini answer text: " + partVo.getText());
      }
    }
    return;
  }
}