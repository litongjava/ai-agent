package com.litongjava.llm.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import com.litongjava.gemini.GeminiCandidateVo;
import com.litongjava.gemini.GeminiChatRequestVo;
import com.litongjava.gemini.GeminiChatResponseVo;
import com.litongjava.gemini.GeminiClient;
import com.litongjava.gemini.GeminiGenerationConfigVo;
import com.litongjava.gemini.GeminiPartVo;
import com.litongjava.gemini.GeminiResponseSchema;
import com.litongjava.gemini.GoogleGeminiModels;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.linux.LinuxClient;
import com.litongjava.linux.ProcessResult;
import com.litongjava.llm.vo.ToolVo;
import com.litongjava.template.PromptEngine;
import com.litongjava.tio.boot.admin.services.AwsS3StorageService;
import com.litongjava.tio.boot.admin.vo.UploadResultVo;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.utils.encoder.Base64Utils;
import com.litongjava.tio.utils.encoder.ImageVo;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MatplotlibService {

  public ProcessResult generateMatplot(String quesiton, String answer) {
    String text = generateCode(quesiton, answer);
    if (StrUtil.isBlank(text)) {
      return null;
    }
    ToolVo toolVo = null;
    try {
      toolVo = JsonUtils.parse(text, ToolVo.class);
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
    if (toolVo != null && "execute_python".equals(toolVo.getTool())) {
      String code = toolVo.getCode();
      ProcessResult result = LinuxClient.executePythonCode(code);
      if (result != null) {
        String stdErr = result.getStdErr();
        if (StrUtil.isNotBlank(stdErr)) {
          String prompt = "python代码执行过程中出现了错误,请修正错误并仅输出修改后的代码,错误信息:%s";
          prompt = String.format(prompt, stdErr);
          code = fixCodeError(prompt, code);
          result = LinuxClient.executePythonCode(code);
        }

        List<String> images = result.getImages();
        if (images != null) {
          List<String> imageUrls = new ArrayList<>(images.size());
          for (String imageBase64Code : images) {
            ImageVo decodeImage = Base64Utils.decodeImage(imageBase64Code);
            UploadFile uploadFile = new UploadFile("matplotlib." + decodeImage.getExtension(), decodeImage.getData());
            UploadResultVo resultVo = Aop.get(AwsS3StorageService.class).uploadFile("matplotlib", uploadFile);
            String url = resultVo.getUrl();
            imageUrls.add(url);
          }
          result.setImages(imageUrls);
        }
        return result;
      }

    }
    return null;
  }

  private String fixCodeError(String userPrompt, String code) {
    String text = generateCode(userPrompt, code);
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

  public String generateCode(String quesiton, String answer) {
    String systemPrompt = PromptEngine.renderToString("python_code_prompt.txt");
    String userPrompt = "请根据下面的用户的问题和答案使用python代码绘制函数图像帮助用户更好的理解问题.如果无需绘制函数图像,请返回`not_needed`";

    // 1. Construct request body
    GeminiChatRequestVo reqVo = new GeminiChatRequestVo();
    reqVo.setUserPrompts(userPrompt, quesiton, answer);
    reqVo.setSystemPrompt(systemPrompt);

    GeminiResponseSchema pythonCode = GeminiResponseSchema.pythonCode();
    GeminiGenerationConfigVo geminiGenerationConfigVo = new GeminiGenerationConfigVo();
    geminiGenerationConfigVo.buildJsonValue().setResponseSchema(pythonCode);

    reqVo.setGenerationConfig(geminiGenerationConfigVo);

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
