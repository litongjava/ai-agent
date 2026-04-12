package nexus.io.llm.api;

import nexus.io.llm.vo.ChatAskRequest;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.core.ChannelContext;

public interface ChatAskService {
  public RespBodyVo index(ChannelContext channelContext, ChatAskRequest chatAskRequest);
}
