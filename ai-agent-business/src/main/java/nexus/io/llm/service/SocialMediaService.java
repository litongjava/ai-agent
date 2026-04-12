package nexus.io.llm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.postgresql.util.PGobject;

import com.google.common.util.concurrent.Striped;
import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import nexus.io.chat.UniChatMessage;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.kit.PgObjectUtils;
import nexus.io.llm.consts.AgentTableNames;
import nexus.io.openai.chat.ChatResponseFormatType;
import nexus.io.openai.chat.OpenAiChatRequest;
import nexus.io.openai.chat.OpenAiChatResponse;
import nexus.io.openai.client.OpenAiClient;
import nexus.io.openai.consts.OpenAiModels;
import nexus.io.template.PromptEngine;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.json.FastJson2Utils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;
import nexus.io.volcengine.VolcEngineConst;
import nexus.io.volcengine.VolcEngineModels;

@Slf4j
public class SocialMediaService {
  private static final Striped<Lock> stripedLocks = Striped.lock(256);

  public String extraSoicalMedia(String name, String institution, String searchInfo) {
    String lowerCaseName = name.toLowerCase();
    institution = institution.toUpperCase();
    String key = name + " at " + institution;
    Lock lock = stripedLocks.get(key);
    lock.lock();
    try {
      String sql = "select data from %s where name=? and institution=?";
      sql = String.format(sql, AgentTableNames.social_media_accounts);
      PGobject pgobject = Db.queryPGobject(sql, lowerCaseName, institution);
      String content = null;
      if (pgobject != null && pgobject.getValue() != null) {
        content = pgobject.getValue();
        return content;
      }

      Kv set = Kv.by("data", searchInfo).set("name", name).set("institution", institution);
      String renderToString = PromptEngine.renderToStringFromDb("extra_soical_media_prompt.txt", set);
      log.info("prompt:{}", renderToString);
      UniChatMessage chatMessage = new UniChatMessage("user", renderToString);
      List<UniChatMessage> messages = new ArrayList<>();
      messages.add(chatMessage);
      OpenAiChatRequest chatRequestVo = new OpenAiChatRequest();
      chatRequestVo.setStream(false);
      chatRequestVo.setResponse_format(ChatResponseFormatType.json_object);
      chatRequestVo.setChatMessages(messages);

      OpenAiChatResponse chat = useOpenAi(chatRequestVo);
      content = chat.getChoices().get(0).getMessage().getContent();
      if (content.startsWith("```json")) {
        content = content.substring(7, content.length() - 3);
      }
      content = FastJson2Utils.parseObject(content).toJSONString();
      PGobject json = PgObjectUtils.json(content);
      Row row = Row.by("id", SnowflakeIdUtils.id()).set("name", lowerCaseName).set("institution", institution).set("data", json);
      Db.save(AgentTableNames.social_media_accounts, row);
      return content;
    } finally {
      lock.unlock();
    }
  }

  private OpenAiChatResponse useDeepseek(OpenAiChatRequest chatRequestVo) {
    chatRequestVo.setModel(VolcEngineModels.DEEPSEEK_V3_250324);
    String apiKey = EnvUtils.get("VOLCENGINE_API_KEY");
    return OpenAiClient.chatCompletions(VolcEngineConst.API_PREFIX_URL, apiKey, chatRequestVo);
  }

  @SuppressWarnings("unused")
  private OpenAiChatResponse useOpenAi(OpenAiChatRequest chatRequestVo) {
    chatRequestVo.setModel(OpenAiModels.GPT_4O_MINI);
    OpenAiChatResponse chat = OpenAiClient.chatCompletions(chatRequestVo);
    return chat;
  }
}
