package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.List;

import org.postgresql.util.PGobject;

import com.jfinal.kit.Kv;
import com.litongjava.db.TableInput;
import com.litongjava.db.TableResult;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.kit.PgObjectUtils;
import com.litongjava.llm.consts.AgentMessageType;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.llm.utils.AgentBotUserThumbUtils;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.model.page.Page;
import com.litongjava.openai.chat.ChatMessageArgs;
import com.litongjava.openai.chat.MessageRole;
import com.litongjava.table.services.ApiTable;
import com.litongjava.tio.boot.admin.vo.UploadResultVo;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;
import com.litongjava.tio.utils.thread.TioThreadUtils;

public class LlmChatHistoryService {

  public RespBodyVo getHistory(Long session_id, int pageNo, int pageSize) {
    TableInput ti = TableInput.create().setColumns("id,role,model,content,liked,metadata,citations,images,create_time")
        //
        .setJsonFields("metadata,citations,images")
        //
        .set("session_id", session_id).set("hidden", false)
        //
        .orderBy("create_time").asc(true)
        //
        .pageNo(pageNo).pageSize(pageSize);

    TableResult<Page<Row>> ts = ApiTable.page(AgentTableNames.llm_chat_history, ti);
    List<Row> list = ts.getData().getList();
    List<Kv> kvs = new ArrayList<>();
    for (Row record : list) {
      kvs.add(record.toKv());
    }
    return RespBodyVo.ok(kvs);
  }

  public List<Row> getHistory(Long sessionId) {
    String sql = "select role,type,content,metadata,args,create_time from %s where session_id =? order by create_time";
    sql = String.format(sql, AgentTableNames.llm_chat_history);
    return Db.find(sql, sessionId);
  }

  public TableResult<Kv> saveUser(Long id, Long sessionId, String textQuestion) {
    TableInput ti = TableInput.by("id", id).set("content", textQuestion).set("role", "user").set("session_id", sessionId);
    TableResult<Kv> ts = ApiTable.save(AgentTableNames.llm_chat_history, ti);
    return ts;
  }

  public TableResult<Kv> saveUser(long id, Long sessionId, String textQuestion, List<UploadResultVo> fileInfo) {
    TableInput ti = TableInput.by("id", id).set("content", textQuestion).set("role", "user").set("session_id", sessionId);
    ti.set("type", AgentMessageType.FILE).set("metadata", fileInfo);
    ti.setJsonFields("metadata");
    TableResult<Kv> ts = ApiTable.save(AgentTableNames.llm_chat_history, ti);
    return ts;
  }

  public void saveUser(long questionId, Long sessionId, String inputQestion, List<UploadResultVo> fileInfo, ChatMessageArgs chatSendArgs) {
    Row ti = Row.by("id", questionId).set("session_id", sessionId).set("content", inputQestion).set("role", "user");
    if (fileInfo != null) {
      ti.set("type", AgentMessageType.FILE).set("metadata", PgObjectUtils.json(fileInfo));

    }
    if (chatSendArgs != null) {
      ti.set("args", PgObjectUtils.json(chatSendArgs));
    }
    Db.save(AgentTableNames.llm_chat_history, ti);
  }

  public TableResult<Kv> saveAssistant(Long id, Long sessionId, String message) {
    TableInput ti = TableInput.by("id", id).set("content", message).set("role", "assistant").set("session_id", sessionId);
    TableResult<Kv> ts = ApiTable.save(AgentTableNames.llm_chat_history, ti);
    return ts;
  }

  public void saveAssistant(long id, long chatId, String model, String message) {
    Row row = Row.by("id", id).set("content", message).set("role", "assistant").set("model", model)
        //
        .set("session_id", chatId);
    Db.save(AgentTableNames.llm_chat_history, row);
  }

  public void like(Long questionId, Long answerId, Boolean like, String userId) {
    String sql = "update %s set liked=? where id=?";
    sql = String.format(sql, AgentTableNames.llm_chat_history);
    Db.updateBySql(sql, like, questionId);
    Db.updateBySql(sql, like, answerId);

    if (like) {
      TioThreadUtils.submit(() -> {
        String queryContentSql = "select content from %s where id=?";
        queryContentSql = String.format(queryContentSql, AgentTableNames.llm_chat_history);

        String question = Db.queryStr(queryContentSql, questionId);
        String answer = Db.queryStr(queryContentSql, answerId);

        StringBuffer messageText = new StringBuffer();
        if (like) {
          messageText.append("like").append("\r\n");
        } else {
          messageText.append("dislike").append("\r\n");
        }
        messageText.append("user_id:").append(userId).append("\r\n");
        messageText.append("question_id:").append(questionId).append("\r\n");
        messageText.append("question:").append(question).append("\r\n\r\n");
        messageText.append("answer_id:").append(answerId).append("\r\n");
        messageText.append("answer:").append(answer).append("\r\n");

        AgentBotUserThumbUtils.send(messageText.toString());
      });
    }
  }

  public void saveFuntionValue(Long chatId, String fnName, String fnValue) {
    PGobject pgJson = PgObjectUtils.json(fnValue);

    Row saveMessage = Row.by("id", SnowflakeIdUtils.id()).set("session_id", chatId)
        //
        .set("role", MessageRole.function).set("content", fnName).set("metadata", pgJson);
    Db.save(AgentTableNames.llm_chat_history, saveMessage);
  }

  public void remove(Long previous_question_id, Long previous_answer_id) {
    String sql = "delete from %s where id=? or id=?";
    sql = String.format(sql, AgentTableNames.llm_chat_history);
    Db.delete(sql, previous_question_id, previous_answer_id);
  }

}
