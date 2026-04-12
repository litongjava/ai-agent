package nexus.io.llm.service;

import java.util.ArrayList;
import java.util.List;

import org.postgresql.util.PGobject;

import com.jfinal.kit.Kv;

import nexus.io.agent.consts.AiAgentBaseTableNames;
import nexus.io.agent.model.LlmChatHistory;
import nexus.io.chat.ChatMessageArgs;
import nexus.io.db.TableInput;
import nexus.io.db.TableResult;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.kit.PgObjectUtils;
import nexus.io.llm.consts.AgentMessageType;
import nexus.io.model.body.RespBodyVo;
import nexus.io.model.page.Page;
import nexus.io.model.upload.UploadResult;
import nexus.io.openai.chat.MessageRole;
import nexus.io.table.services.ApiTable;
import nexus.io.tio.utils.commandline.ProcessResult;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

public class LlmChatHistoryService {

  public RespBodyVo getHistory(Long session_id, int pageNo, int pageSize) {
    TableInput ti = TableInput.create()
        .setColumns("id,role,model,content,liked,metadata,citations,images,create_time,code_result")
        //
        .setJsonFields("metadata,citations,images,code_result")
        //
        .set("session_id", session_id).set("hidden", false)
        //
        .orderBy("create_time").asc(true)
        //
        .pageNo(pageNo).pageSize(pageSize);

    TableResult<Page<Row>> ts = ApiTable.page(AiAgentBaseTableNames.llm_chat_history, ti);
    List<Row> list = ts.getData().getList();
    List<Kv> kvs = new ArrayList<>();
    for (Row record : list) {
      kvs.add(record.toKv());
    }
    return RespBodyVo.ok(kvs);
  }

  public List<LlmChatHistory> getHistory(Long sessionId) {
    String sql = "select role,type,content,metadata,code_result,args,create_time from %s where session_id =? order by create_time";
    sql = String.format(sql, AiAgentBaseTableNames.llm_chat_history);
    // Db.find(sql, sessionId);
    return LlmChatHistory.dao.find(sql, sessionId);
  }

  public TableResult<Kv> saveUser(Long id, Long sessionId, String textQuestion) {
    TableInput ti = TableInput.by("id", id).set("content", textQuestion).set("role", "user").set("session_id",
        sessionId);
    TableResult<Kv> ts = ApiTable.save(AiAgentBaseTableNames.llm_chat_history, ti);
    return ts;
  }

  public TableResult<Kv> saveUser(long id, Long sessionId, String textQuestion, List<UploadResult> fileInfo) {
    TableInput ti = TableInput.by("id", id).set("content", textQuestion).set("role", "user").set("session_id",
        sessionId);
    ti.set("type", AgentMessageType.FILE).set("metadata", fileInfo);
    ti.setJsonFields("metadata");
    TableResult<Kv> ts = ApiTable.save(AiAgentBaseTableNames.llm_chat_history, ti);
    return ts;
  }

  public void saveUser(long questionId, Long sessionId, String inputQestion, List<UploadResult> fileInfo,
      ChatMessageArgs chatSendArgs) {
    Row ti = Row.by("id", questionId).set("session_id", sessionId).set("content", inputQestion).set("role", "user");
    if (fileInfo != null) {
      ti.set("type", AgentMessageType.FILE).set("metadata", PgObjectUtils.json(fileInfo));

    }
    if (chatSendArgs != null) {
      ti.set("args", PgObjectUtils.json(chatSendArgs));
    }
    Db.save(AiAgentBaseTableNames.llm_chat_history, ti);
  }

  public TableResult<Kv> saveAssistant(Long id, Long sessionId, String message) {
    TableInput ti = TableInput.by("id", id).set("content", message).set("role", "assistant").set("session_id",
        sessionId);
    TableResult<Kv> ts = ApiTable.save(AiAgentBaseTableNames.llm_chat_history, ti);
    return ts;
  }

  public void saveAssistant(long id, long chatId, String model, String message) {
    Row row = Row.by("id", id).set("content", message).set("role", "assistant").set("model", model)
        //
        .set("session_id", chatId);
    Db.save(AiAgentBaseTableNames.llm_chat_history, row);
  }

  public void saveAssistant(long answerId, Long session_id, String model, String message, ProcessResult codeResult) {
    Row row = Row.by("id", answerId).set("content", message).set("role", "assistant").set("model", model)
        //
        .set("session_id", session_id);
    if (codeResult != null) {
      row.set("code_result", PgObjectUtils.json(codeResult));
    }
    Db.save(AiAgentBaseTableNames.llm_chat_history, row);
  }

  public void like(Long questionId, Long answerId, Boolean like, String userId) {
    String sql = "update %s set liked=? where id=?";
    sql = String.format(sql, AiAgentBaseTableNames.llm_chat_history);
    Db.updateBySql(sql, like, questionId);
    Db.updateBySql(sql, like, answerId);
  }

  public void saveAnswer(Long chatId, String answer, long answer_id) {
    Row row = Row.by("id", answer_id).set("content", answer).set("role", "assistant").set("session_id", chatId);
    Db.save(AiAgentBaseTableNames.llm_chat_history, row);
  }

  public void saveFuntionValue(Long chatId, String fnName, String fnValue) {
    PGobject pgJson = PgObjectUtils.json(fnValue);

    Row saveMessage = Row.by("id", SnowflakeIdUtils.id()).set("session_id", chatId)
        //
        .set("role", MessageRole.function).set("content", fnName).set("metadata", pgJson);
    Db.save(AiAgentBaseTableNames.llm_chat_history, saveMessage);
  }

  public void remove(Long previous_question_id, Long previous_answer_id) {
    String sql = "delete from %s where id=? or id=?";
    sql = String.format(sql, AiAgentBaseTableNames.llm_chat_history);
    Db.delete(sql, previous_question_id, previous_answer_id);
  }

}
