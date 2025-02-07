package com.litongjava.llm.service;

import java.util.HashMap;
import java.util.Map;

import com.jfinal.template.Engine;
import com.jfinal.template.Template;
import com.litongjava.constants.ServerConfigKeys;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.dao.SchoolDictDao;
import com.litongjava.llm.vo.SchoolDict;
import com.litongjava.openai.constants.OpenAiModels;
import com.litongjava.openai.embedding.EmbeddingResponseVo;
import com.litongjava.openai.utils.EmbeddingVectorUtils;
import com.litongjava.template.SqlTemplates;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.sse.SsePacket;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SearchPromptService {

  public String index(Long schoolId, String textQuestion, boolean stream, ChannelContext channelContext) {

    Map<String, String> params = new HashMap<>();

    // 向量
    EmbeddingResponseVo vector = Aop.get(VectorService.class).getVector(textQuestion, OpenAiModels.TEXT_EMBEDDING_3_LARGE);

    String string = EmbeddingVectorUtils.toString(vector.getData().get(0).getEmbedding());

    String sql = SqlTemplates.get("llm_intent_classification.intent");

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
    SchoolDict schoolDict = Aop.get(SchoolDictDao.class).getNameById(schoolId);

    log.info("school:{},{}", schoolId, schoolDict);
    if (schoolDict == null) {
      params.put("botName", "Spartan Assistant");
      params.put("schoolName", "San Jose State University (SJSU)");
    } else {
      params.put("botName", schoolDict.getBotName());
      params.put("schoolName", schoolDict.getFullName());
    }

    // 渲染模版
    Template template = Engine.use().getTemplate("search_init_prompt_v1.txt");
    return template.renderToString(params);
  }

}
