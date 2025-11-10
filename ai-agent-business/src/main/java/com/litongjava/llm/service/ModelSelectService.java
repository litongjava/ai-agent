package com.litongjava.llm.service;

import com.litongjava.consts.ModelPlatformName;
import com.litongjava.gemini.GoogleModels;
import com.litongjava.llm.consts.ApiChatAskType;
import com.litongjava.llm.vo.ChatAskVo;
import com.litongjava.openai.consts.OpenAiModels;
import com.litongjava.openrouter.OpenRouterModels;

public class ModelSelectService {

  public void select(String type, ChatAskVo apiChatAskVo) {
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
