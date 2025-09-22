package com.litongjava.llm.service;

import java.util.concurrent.CountDownLatch;

import com.litongjava.llm.consts.ApiChatAskType;
import com.litongjava.tio.utils.environment.EnvUtils;

public class EnvConfigService {

  public boolean isGenerateGraph(CountDownLatch latch, String type) {
    boolean gen = EnvUtils.getBoolean("chat.tutor.gen.function.graph", true);
    if (gen) {
      if (latch == null) {
        return ApiChatAskType.tutor.equals(type) || ApiChatAskType.math.equals(type);
      } else {
        return (latch.getCount() == 1) && (ApiChatAskType.tutor.equals(type) || ApiChatAskType.math.equals(type));
      }

    }
    return false;
  }
}
