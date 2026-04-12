package nexus.io.llm.service;

import nexus.io.consts.ModelPlatformName;
import nexus.io.gemini.GoogleModels;
import nexus.io.llm.consts.ApiChatAskType;
import nexus.io.llm.vo.ChatAskRequest;
import nexus.io.openai.consts.OpenAiModels;
import nexus.io.openrouter.OpenRouterModels;

public class ModelSelectService {

  public void select(String type, ChatAskRequest apiChatAskVo) {
    if (ApiChatAskType.translator.equals(type)) {
      if (ModelPlatformName.AUTO.equals(apiChatAskVo.getProvider())) {
        apiChatAskVo.setProvider(ModelPlatformName.GOOGLE);
      }

      if (OpenRouterModels.AUTO.equals(apiChatAskVo.getModel())) {
        // apiChatAskVo.setModel(OpenAiModels.GPT_5);
        apiChatAskVo.setModel(GoogleModels.GEMINI_2_5_FLASH);
      }
    } else {
      if (ModelPlatformName.AUTO.equals(apiChatAskVo.getProvider())) {
        apiChatAskVo.setProvider(ModelPlatformName.OPENAI);
      }

      if (OpenRouterModels.AUTO.equals(apiChatAskVo.getModel())) {
        apiChatAskVo.setModel(OpenAiModels.GPT_5);
      }
    }

  }
}
