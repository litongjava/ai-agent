package nexus.io.agent.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.postgresql.util.PGobject;

import nexus.io.agent.consts.AiAgentBaseTableNames;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.db.utils.PgVectorUtils;
import nexus.io.openai.client.OpenAiClient;
import nexus.io.openai.consts.OpenAiModels;
import nexus.io.openai.embedding.EmbeddingData;
import nexus.io.openai.embedding.EmbeddingRequest;
import nexus.io.openai.embedding.EmbeddingResponse;
import nexus.io.openai.utils.EmbeddingVectorUtils;
import nexus.io.tio.utils.crypto.Md5Utils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;
import nexus.io.tio.utils.thread.TioThreadUtils;

public class VectorService {

  private final Object vectorLock = new Object();
  private final Object writeLock = new Object();

  public String getVector(String text) {
    String v = null;
    String md5 = Md5Utils.md5Hex(text);
    String sql = String.format("select v from %s where md5=? and m=?", AiAgentBaseTableNames.llm_vector_embedding);
    PGobject pGobject = Db.queryFirst(sql, md5, OpenAiModels.TEXT_EMBEDDING_3_LARGE);
    if (pGobject != null) {
      v = pGobject.getValue();
    } else {
      float[] embeddingArray = null;
      synchronized (vectorLock) {
        embeddingArray = OpenAiClient.embeddingArray(text, OpenAiModels.TEXT_EMBEDDING_3_LARGE);
      }

      String string = Arrays.toString(embeddingArray);
      long id = SnowflakeIdUtils.id();
      v = (String) string;
      PGobject pgVector = PgVectorUtils.getPgVector(v);
      Row saveRecord = new Row().set("t", text).set("v", pgVector).set("id", id).set("md5", md5)
          //
          .set("m", OpenAiModels.TEXT_EMBEDDING_3_LARGE);
      synchronized (writeLock) {
        Db.save("rumi_embedding", saveRecord);
      }
    }
    return v;
  }

  public synchronized EmbeddingResponse getVector(String text, String model) {
    String md5 = Md5Utils.md5Hex(text);
    String sql = String.format("select v from %s where md5=? and m=?", AiAgentBaseTableNames.llm_vector_embedding);
    PGobject pGobject = Db.queryFirst(sql, md5, model);
    if (pGobject != null) {
      String value = pGobject.getValue();
      float[] floats = EmbeddingVectorUtils.toFloats(value);
      List<EmbeddingData> lists = new ArrayList<>(1);
      EmbeddingData embeddingData = new EmbeddingData();
      embeddingData.setEmbedding(floats);
      lists.add(embeddingData);

      EmbeddingResponse embeddingResponseVo = new EmbeddingResponse();
      embeddingResponseVo.setModel(model);
      embeddingResponseVo.setData(lists);
      return embeddingResponseVo;
    } else {
      EmbeddingRequest embeddingRequestVo = new EmbeddingRequest(model, text);
      EmbeddingResponse embeddingResponseVo = null;
      synchronized (vectorLock) {
        embeddingResponseVo = OpenAiClient.embeddings(embeddingRequestVo);
      }

      float[] embeddingArray = embeddingResponseVo.getData().get(0).getEmbedding();
      String string = Arrays.toString(embeddingArray);

      TioThreadUtils.submit(() -> {
        long id = SnowflakeIdUtils.id();
        PGobject pgVector = PgVectorUtils.getPgVector(string);
        Row saveRecord = Row.by("id", id).set("md5", md5).set("t", text).set("v", pgVector)
            //
            .set("m", model).setTableName(AiAgentBaseTableNames.llm_vector_embedding);
        synchronized (writeLock) {
          Db.save(saveRecord);
        }

      });

      return embeddingResponseVo;

    }
  }
}
