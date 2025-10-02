package com.litongjava.llm.service;

import com.litongjava.agent.consts.AgentLLMTableNames;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;

public class UserAskQuesitonService {

  public boolean save(String content) {
    Row record = Row.by("content", content);
    return Db.save(AgentLLMTableNames.llm_user_asked_questions, record);
  }
}
