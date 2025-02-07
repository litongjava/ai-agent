package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;
import com.litongjava.db.TableInput;
import com.litongjava.db.TableResult;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.llm.utils.AgentBotUserThumbUtils;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.model.page.Page;
import com.litongjava.table.services.ApiTable;
import com.litongjava.tio.utils.thread.TioThreadUtils;

public class LlmChatHistoryService {

  public RespBodyVo getHistory(Long session_id, int pageNo, int pageSize) {
    TableInput ti = TableInput.create().setColumns("id,role,content,liked,metadata,create_time")
        //
        .setJsonFields("metadata")
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
    String sql = "select create_time,role,content,metadata from %s where session_id =? order by create_time";
    sql = String.format(sql, AgentTableNames.llm_chat_history);
    return Db.find(sql, sessionId);
  }

  public TableResult<Kv> saveUser(Long id, Long sessionId, String textQuestion) {
    TableInput ti = TableInput.by("id", id).set("content", textQuestion).set("role", "user").set("session_id", sessionId);
    TableResult<Kv> ts = ApiTable.save(AgentTableNames.llm_chat_history, ti);
    return ts;
  }

  public TableResult<Kv> saveAssistant(Long id, Long sessionId, String message) {
    TableInput ti = TableInput.by("id", id).set("content", message).set("role", "assistant").set("session_id", sessionId);
    TableResult<Kv> ts = ApiTable.save(AgentTableNames.llm_chat_history, ti);
    return ts;
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
}
