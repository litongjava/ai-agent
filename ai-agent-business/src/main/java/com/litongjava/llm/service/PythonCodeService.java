package com.litongjava.llm.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import com.litongjava.chat.UniResponseSchema;
import com.litongjava.gemini.GeminiCandidateVo;
import com.litongjava.gemini.GeminiChatRequestVo;
import com.litongjava.gemini.GeminiChatResponseVo;
import com.litongjava.gemini.GeminiClient;
import com.litongjava.gemini.GeminiGenerationConfig;
import com.litongjava.gemini.GeminiPartVo;
import com.litongjava.gemini.GoogleModels;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.vo.ToolVo;
import com.litongjava.template.PromptEngine;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PythonCodeService {
  
  public String generateMatplotlibCode(String quesiton, String answer) {
    String systemPrompt = PromptEngine.renderToString("python_code_prompt.txt");
    String userPrompt = "请根据下面的用户的问题和答案使用python代码绘制函数图像帮助用户更好的理解问题.如果无需绘制函数图像,请返回`not_needed`";

    // 1. Construct request body
    GeminiChatRequestVo reqVo = new GeminiChatRequestVo();
    reqVo.setUserPrompts(userPrompt, quesiton, answer);
    reqVo.setSystemPrompt(systemPrompt);

    UniResponseSchema pythonCode = UniResponseSchema.pythonCode();
    GeminiGenerationConfig geminiGenerationConfigVo = new GeminiGenerationConfig();
    geminiGenerationConfigVo.buildJsonValue().setResponseSchema(pythonCode);

    reqVo.setGenerationConfig(geminiGenerationConfigVo);

    // 2. Send sync request: generateContent
    GeminiChatResponseVo respVo = GeminiClient.generate(GoogleModels.GEMINI_2_0_FLASH, reqVo);
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
  
  public String fixCodeError(String userPrompt, String code) {
    
    String text = generateMatplotlibCode(userPrompt, code);
    if (StrUtil.isBlank(text)) {
      return null;
    }
    ToolVo toolVo = null;
    try {
      toolVo = JsonUtils.parse(text, ToolVo.class);
      return toolVo.getCode();
    } catch (Exception e) {
      log.error("text:{}", text, e.getMessage(), e);
      StringWriter stringWriter = new StringWriter();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      e.printStackTrace(printWriter);
      String stackTrace = stringWriter.toString();
      String msg = "code:" + text + ",stackTrace" + stackTrace;
      Aop.get(AgentNotificationService.class).sendError(msg);
      return null;
    }
  }


}
