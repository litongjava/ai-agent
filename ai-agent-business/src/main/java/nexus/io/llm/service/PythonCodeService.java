package nexus.io.llm.service;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import nexus.io.chat.UniChatClient;
import nexus.io.chat.UniChatRequest;
import nexus.io.chat.UniChatResponse;
import nexus.io.chat.UniResponseSchema;
import nexus.io.consts.ModelPlatformName;
import nexus.io.gemini.GeminiCandidate;
import nexus.io.gemini.GeminiChatRequest;
import nexus.io.gemini.GeminiChatResponse;
import nexus.io.gemini.GeminiClient;
import nexus.io.gemini.GeminiGenerationConfig;
import nexus.io.gemini.GeminiPart;
import nexus.io.gemini.GoogleModels;
import nexus.io.openai.ChatProvider;
import nexus.io.openrouter.OpenRouterModels;
import nexus.io.template.PromptEngine;

@Slf4j
public class PythonCodeService {

  public String generateMatplotlibCode(String quesiton, String answer) {
    String systemPrompt = PromptEngine.renderToString("python_code_prompt.txt");
    String userPrompt = "请根据下面的用户的问题和答案使用python代码绘制函数图像帮助用户更好的理解问题.如果无需绘制函数图像,请返回`not_needed`";

    UniChatRequest uniChatRequest = new UniChatRequest(systemPrompt);
    uniChatRequest.setUserPrompts(userPrompt, quesiton, answer);

    uniChatRequest.setPlatform(ModelPlatformName.OPENROUTER).setModel(OpenRouterModels.QWEN_QWEN3_CODER)
        //
        .setProvider(ChatProvider.cerebras()).setTemperature(0f);

    // useGemini(quesiton, answer, systemPrompt, userPrompt);
    UniChatResponse response = UniChatClient.generate(uniChatRequest);
    return response.getMessage().getContent();
  }

  private String useGemini(String quesiton, String answer, String systemPrompt, String userPrompt) {
    // 1. Construct request body
    GeminiChatRequest reqVo = new GeminiChatRequest();
    reqVo.setUserPrompts(userPrompt, quesiton, answer);
    reqVo.setSystemPrompt(systemPrompt);

    UniResponseSchema pythonCode = UniResponseSchema.pythonCode();
    GeminiGenerationConfig geminiGenerationConfigVo = new GeminiGenerationConfig();
    geminiGenerationConfigVo.buildJsonValue().setResponseSchema(pythonCode);

    reqVo.setGenerationConfig(geminiGenerationConfigVo);

    // 2. Send sync request: generateContent
    GeminiChatResponse respVo = GeminiClient.generate(GoogleModels.GEMINI_2_0_FLASH, reqVo);
    if (respVo != null) {
      List<GeminiCandidate> candidates = respVo.getCandidates();
      GeminiCandidate candidate = candidates.get(0);
      List<GeminiPart> parts = candidate.getContent().getParts();
      if (candidate != null && candidate.getContent() != null && parts != null) {
        String text = parts.get(0).getText();
        return text;
      }
    }
    return null;
  }

  public String fixCodeError(String stdErr, String code) {

    String prompt = "python代码执行过程中出现了错误,请修正错误并仅输出修改后的代码,错误信息:%s";
    prompt = String.format(prompt, stdErr);

    String systemPrompt = PromptEngine.renderToString("python_code_prompt.txt");

    UniChatRequest uniChatRequest = new UniChatRequest(systemPrompt);
    uniChatRequest.setUserPrompts(prompt);

    uniChatRequest.setPlatform(ModelPlatformName.OPENROUTER).setModel(OpenRouterModels.QWEN_QWEN3_CODER)
        //
        .setProvider(ChatProvider.cerebras()).setTemperature(0f);

    // useGemini(quesiton, answer, systemPrompt, userPrompt);
    UniChatResponse response = UniChatClient.generate(uniChatRequest);
    return response.getMessage().getContent();
  }

}
