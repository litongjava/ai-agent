package com.litongjava.llm.api;

import com.litongjava.llm.vo.ChatAskRequest;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.tio.core.ChannelContext;

public interface ChatAskService {
  public RespBodyVo index(ChannelContext channelContext, ChatAskRequest chatAskRequest);
}
