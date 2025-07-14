package com.litongjava.llm.service;

import com.jfinal.kit.Kv;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.sse.SsePacket;
import com.litongjava.tio.utils.json.JsonUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

public class LLmChatStreamHistoryService {

  public void saveAnswerAndSend(Long chatId, ChannelContext channelContext, String answer) {
    long answer_id = SnowflakeIdUtils.id();
    Row row = Row.by("id", answer_id).set("content", answer).set("role", "assistant").set("session_id", chatId);
    Db.save(AgentTableNames.llm_chat_history, row);
    Kv kv = Kv.by("answer_id", answer_id);
    SsePacket packet = new SsePacket(AiChatEventName.message_id, JsonUtils.toJson(kv));
    Tio.bSend(channelContext, packet);
  }
}
