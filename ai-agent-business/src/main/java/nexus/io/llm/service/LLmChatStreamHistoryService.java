package nexus.io.llm.service;

import com.jfinal.kit.Kv;

import nexus.io.http.common.sse.SsePacket;
import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.consts.AiChatEventName;
import nexus.io.llm.service.LlmChatHistoryService;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.Tio;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

public class LLmChatStreamHistoryService {

  public void saveAnswerAndSend(Long chatId, ChannelContext channelContext, String answer) {
    long answer_id = SnowflakeIdUtils.id();
    Aop.get(LlmChatHistoryService.class).saveAnswer(chatId, answer, answer_id);
    Kv kv = Kv.by("answer_id", answer_id);
    SsePacket packet = new SsePacket(AiChatEventName.message_id, JsonUtils.toJson(kv));
    Tio.bSend(channelContext, packet);
  }

}
