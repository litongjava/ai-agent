package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.List;

import org.postgresql.util.PGobject;

import com.jfinal.kit.Kv;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.ehcache.EhCacheKit;
import com.litongjava.kit.PgObjectUtils;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.openai.chat.ChatMessage;
import com.litongjava.openai.chat.ChatResponseFormatType;
import com.litongjava.openai.chat.OpenAiChatRequestVo;
import com.litongjava.openai.chat.OpenAiChatResponseVo;
import com.litongjava.openai.client.OpenAiClient;
import com.litongjava.openai.constants.OpenAiModels;
import com.litongjava.template.PromptEngine;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.json.FastJson2Utils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;
import com.litongjava.volcengine.VolcEngineConst;
import com.litongjava.volcengine.VolcEngineModels;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SocialMediaService {
  public String extraSoicalMedia(String name, String institution, String searchInfo) {
    String cacheName = "soical_media_accounts_data";
    String question = name + " at " + institution;
    String content = EhCacheKit.getString(cacheName, question);
    if (content != null) {
      return content;
    }
    PGobject pgobject = Db.queryColumnByField(AgentTableNames.social_media_accounts_cache, "data", "name", question);
    if (pgobject != null && pgobject.getValue() != null) {
      content = pgobject.getValue();
      EhCacheKit.put(cacheName, question, content);
      return content;
    }

    Kv set = Kv.by("data", searchInfo).set("name", name).set("institution", institution);
    String renderToString = PromptEngine.renderToString("extra_soical_media_prompt.txt", set);
    log.info("prompt:{}", renderToString);
    ChatMessage chatMessage = new ChatMessage("user", renderToString);
    List<ChatMessage> messages = new ArrayList<>();
    messages.add(chatMessage);
    OpenAiChatRequestVo chatRequestVo = new OpenAiChatRequestVo();
    chatRequestVo.setStream(false);
    chatRequestVo.setResponse_format(ChatResponseFormatType.json_object);
    chatRequestVo.setChatMessages(messages);

    OpenAiChatResponseVo chat = useDeepseek(chatRequestVo);
    content = chat.getChoices().get(0).getMessage().getContent();
    if (content.startsWith("```json")) {
      content = content.substring(7, content.length() - 3);
    }
    content = FastJson2Utils.parseObject(content).toJSONString();
    Row row = Row.by("id", SnowflakeIdUtils.id()).set("name", question).set("data", PgObjectUtils.json(content));
    Db.save(AgentTableNames.social_media_accounts_cache, row);
    EhCacheKit.put(cacheName, question, content);
    return content;
  }

  private OpenAiChatResponseVo useDeepseek(OpenAiChatRequestVo chatRequestVo) {
    chatRequestVo.setModel(VolcEngineModels.DEEPSEEK_V3_241226);
    String apiKey = EnvUtils.get("VOLCENGINE_API_KEY");
    return OpenAiClient.chatCompletions(VolcEngineConst.BASE_URL, apiKey, chatRequestVo);
  }

  @SuppressWarnings("unused")
  private OpenAiChatResponseVo useOpenAi(OpenAiChatRequestVo chatRequestVo) {
    chatRequestVo.setModel(OpenAiModels.GPT_4O_MINI);
    OpenAiChatResponseVo chat = OpenAiClient.chatCompletions(chatRequestVo);
    return chat;
  }
}
