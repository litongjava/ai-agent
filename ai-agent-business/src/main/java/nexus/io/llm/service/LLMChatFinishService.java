package nexus.io.llm.service;

import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.callback.ChatCompletionVo;
import nexus.io.llm.vo.ChatAskRequest;
import nexus.io.tio.core.ChannelContext;

public class LLMChatFinishService {
  private FollowUpQuestionService followUpQuestionService = Aop.get(FollowUpQuestionService.class);

  public void index(ChannelContext channelContext, ChatAskRequest apiChatSendVo, ChatCompletionVo callbackVo) {
    followUpQuestionService.generate(channelContext, apiChatSendVo, callbackVo);
  }
}
