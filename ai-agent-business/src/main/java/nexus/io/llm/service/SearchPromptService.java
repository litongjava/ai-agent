package nexus.io.llm.service;

import java.util.HashMap;
import java.util.Map;

import com.jfinal.template.Engine;
import com.jfinal.template.Template;

import lombok.extern.slf4j.Slf4j;
import nexus.io.agent.service.VectorService;
import nexus.io.constants.ServerConfigKeys;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.http.common.sse.SsePacket;
import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.consts.AiChatEventName;
import nexus.io.llm.dao.SchoolDictDao;
import nexus.io.llm.sql.LlmIntentClassificationSql;
import nexus.io.llm.vo.SchoolDict;
import nexus.io.openai.consts.OpenAiModels;
import nexus.io.openai.embedding.EmbeddingResponse;
import nexus.io.openai.utils.EmbeddingVectorUtils;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.Tio;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.json.JsonUtils;

@Slf4j
public class SearchPromptService {

  public String index(Long schoolId, String textQuestion, boolean stream, ChannelContext channelContext) {

    Map<String, String> params = new HashMap<>();

    // 向量
    EmbeddingResponse vector = Aop.get(VectorService.class).getVector(textQuestion, OpenAiModels.TEXT_EMBEDDING_3_LARGE);

    String string = EmbeddingVectorUtils.toString(vector.getData().get(0).getEmbedding());

    String sql = LlmIntentClassificationSql.intent;

    // 查询数据库
    Row record = Db.findFirst(sql, string, EnvUtils.get(ServerConfigKeys.APP_ENV));

    if (record != null) {
      if (stream && channelContext != null) {
        String json = JsonUtils.toJson(record.toMap());
        SsePacket packet = new SsePacket(AiChatEventName.progress, "category:" + json);
        Tio.bSend(channelContext, packet);
      }

      String category = record.getStr("name").replace('_', ' ');
      String additional_info = record.getStr("additional_info");
      params.put("category", category);
      params.put("additional_info", additional_info);
      params.put("additional_info", additional_info);
    } else {
      if (stream && channelContext != null) {
        SsePacket packet = new SsePacket(AiChatEventName.progress, "default");
        Tio.bSend(channelContext, packet);
      }
    }
    SchoolDict schoolDict = Aop.get(SchoolDictDao.class).getSchoolById(schoolId);

    log.info("school:{},{}", schoolId, schoolDict);
    if (schoolDict == null) {
      params.put("botName", "Spartan Assistant");
      params.put("schoolName", "San Jose State University (SJSU)");
    } else {
      params.put("botName", schoolDict.getBot_name());
      params.put("schoolName", schoolDict.getFull_name());
    }

    // 渲染模版
    Template template = Engine.use().getTemplate("search_init_prompt_v1.txt");
    return template.renderToString(params);
  }

}
