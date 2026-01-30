package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.List;

import org.postgresql.util.PGobject;

import com.jfinal.kit.Kv;
import com.litongjava.agent.consts.AgentLLMTableNames;
import com.litongjava.chat.ChatMessageArgs;
import com.litongjava.db.TableInput;
import com.litongjava.db.TableResult;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.kit.PgObjectUtils;
import com.litongjava.llm.consts.AgentMessageType;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.model.page.Page;
import com.litongjava.model.upload.UploadResult;
import com.litongjava.openai.chat.MessageRole;
import com.litongjava.table.services.ApiTable;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

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

    TableResult<Page<Row>> ts = ApiTable.page(AgentLLMTableNames.llm_chat_history, ti);
    List<Row> list = ts.getData().getList();
    List<Kv> kvs = new ArrayList<>();
    for (Row record : list) {
      kvs.add(record.toKv());
    }
    return RespBodyVo.ok(kvs);
  }

  public List<Row> getHistory(Long sessionId) {
    String sql = "select role,type,content,metadata,code_result,args,create_time from %s where session_id =? order by create_time";
    sql = String.format(sql, AgentLLMTableNames.llm_chat_history);
    return Db.find(sql, sessionId);
  }

  public TableResult<Kv> saveUser(Long id, Long sessionId, String textQuestion) {
    TableInput ti = TableInput.by("id", id).set("content", textQuestion).set("role", "user").set("session_id",
        sessionId);
    TableResult<Kv> ts = ApiTable.save(AgentLLMTableNames.llm_chat_history, ti);
    return ts;
  }

  public TableResult<Kv> saveUser(long id, Long sessionId, String textQuestion, List<UploadResult> fileInfo) {
    TableInput ti = TableInput.by("id", id).set("content", textQuestion).set("role", "user").set("session_id",
        sessionId);
    ti.set("type", AgentMessageType.FILE).set("metadata", fileInfo);
    ti.setJsonFields("metadata");
    TableResult<Kv> ts = ApiTable.save(AgentLLMTableNames.llm_chat_history, ti);
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
    Db.save(AgentLLMTableNames.llm_chat_history, ti);
  }

  public TableResult<Kv> saveAssistant(Long id, Long sessionId, String message) {
    TableInput ti = TableInput.by("id", id).set("content", message).set("role", "assistant").set("session_id",
        sessionId);
    TableResult<Kv> ts = ApiTable.save(AgentLLMTableNames.llm_chat_history, ti);
    return ts;
  }

  public void saveAssistant(long id, long chatId, String model, String message) {
    Row row = Row.by("id", id).set("content", message).set("role", "assistant").set("model", model)
        //
        .set("session_id", chatId);
    Db.save(AgentLLMTableNames.llm_chat_history, row);
  }

  public void saveAssistant(long answerId, Long session_id, String model, String message, ProcessResult codeResult) {
    Row row = Row.by("id", answerId).set("content", message).set("role", "assistant").set("model", model)
        //
        .set("session_id", session_id);
    if (codeResult != null) {
      row.set("code_result", PgObjectUtils.json(codeResult));
    }
    Db.save(AgentLLMTableNames.llm_chat_history, row);
  }

  public void like(Long questionId, Long answerId, Boolean like, String userId) {
    String sql = "update %s set liked=? where id=?";
    sql = String.format(sql, AgentLLMTableNames.llm_chat_history);
    Db.updateBySql(sql, like, questionId);
    Db.updateBySql(sql, like, answerId);
  }

  public void saveAnswer(Long chatId, String answer, long answer_id) {
    Row row = Row.by("id", answer_id).set("content", answer).set("role", "assistant").set("session_id", chatId);
    Db.save(AgentLLMTableNames.llm_chat_history, row);
  }

  public void saveFuntionValue(Long chatId, String fnName, String fnValue) {
    PGobject pgJson = PgObjectUtils.json(fnValue);

    Row saveMessage = Row.by("id", SnowflakeIdUtils.id()).set("session_id", chatId)
        //
        .set("role", MessageRole.function).set("content", fnName).set("metadata", pgJson);
    Db.save(AgentLLMTableNames.llm_chat_history, saveMessage);
  }

  public void remove(Long previous_question_id, Long previous_answer_id) {
    String sql = "delete from %s where id=? or id=?";
    sql = String.format(sql, AgentLLMTableNames.llm_chat_history);
    Db.delete(sql, previous_question_id, previous_answer_id);
  }

}
