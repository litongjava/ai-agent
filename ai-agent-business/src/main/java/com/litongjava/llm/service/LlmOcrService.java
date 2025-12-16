package com.litongjava.llm.service;

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
import com.litongjava.tio.utils.http.ContentTypeUtils;
import com.litongjava.tio.utils.hutool.FilenameUtils;

public class LlmOcrService {

  String prompt = "Convert the image to text and just output the text.If the picture does not contain text, please describe the image.";

  public String parse(byte[] data, String filename) {

    String suffix = FilenameUtils.getSuffix(filename);
    String mimeType = ContentTypeUtils.getContentType(suffix);
    String encodeImage = Base64Utils.encodeToString(data);
    String googleApiKey = EnvUtils.getStr("GEMINI_API_KEY");

    // 1. Build request body
    List<GeminiPart> parts = new ArrayList<>();
    parts.add(new GeminiPart(prompt));
    parts.add(new GeminiPart(new GeminiInlineData(mimeType, encodeImage)));
    GeminiContent content = new GeminiContent("user", parts);
    GeminiChatRequest reqVo = new GeminiChatRequest(Collections.singletonList(content));

    // 2. Sync request: generateContent
    GeminiChatResponse respVo = GeminiClient.generate(googleApiKey, GoogleModels.GEMINI_2_0_FLASH_EXP, reqVo);
    if (respVo != null && respVo.getCandidates() != null) {
      GeminiCandidate candidate = respVo.getCandidates().get(0);
      if (candidate.getContent() != null && candidate.getContent().getParts() != null) {
        GeminiPart partVo = candidate.getContent().getParts().get(0);
        return partVo.getText();
      }
    }
    return null;
  }

}
