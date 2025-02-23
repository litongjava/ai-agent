package com.litongjava.llm.service;

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
import com.litongjava.tio.utils.encoder.Base64Utils;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.http.ContentTypeUtils;
import com.litongjava.tio.utils.hutool.FilenameUtils;

public class LlmOcrService {

  String prompt = "Convert the image to text and just output the text.";

  public String parse(byte[] data, String filename) {

    String suffix = FilenameUtils.getSuffix(filename);
    String mimeType = ContentTypeUtils.getContentType(suffix);
    String encodeImage = Base64Utils.encodeToString(data);
    String googleApiKey = EnvUtils.getStr("GEMINI_API_KEY");

    // 1. Build request body
    List<GeminiPartVo> parts = new ArrayList<>();
    parts.add(new GeminiPartVo(prompt));
    parts.add(new GeminiPartVo(new GeminiInlineDataVo(mimeType, encodeImage)));
    GeminiContentVo content = new GeminiContentVo("user", parts);
    GeminiChatRequestVo reqVo = new GeminiChatRequestVo(Collections.singletonList(content));

    // 2. Sync request: generateContent
    GeminiChatResponseVo respVo = GeminiClient.generate(googleApiKey, GoogleGeminiModels.GEMINI_2_0_FLASH_EXP, reqVo);
    if (respVo != null && respVo.getCandidates() != null) {
      GeminiCandidateVo candidate = respVo.getCandidates().get(0);
      if (candidate.getContent() != null && candidate.getContent().getParts() != null) {
        GeminiPartVo partVo = candidate.getContent().getParts().get(0);
        return partVo.getText();
      }
    }
    return null;
  }

}
