package com.litongjava.llm.service;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.callback.ChatCompletionVo;
import com.litongjava.llm.vo.ChatAskRequest;
import com.litongjava.tio.core.ChannelContext;

public class LLMChatFinishService {
  private FollowUpQuestionService followUpQuestionService = Aop.get(FollowUpQuestionService.class);

  public void index(ChannelContext channelContext, ChatAskRequest apiChatSendVo, ChatCompletionVo callbackVo) {
    followUpQuestionService.generate(channelContext, apiChatSendVo, callbackVo);
  }
}
