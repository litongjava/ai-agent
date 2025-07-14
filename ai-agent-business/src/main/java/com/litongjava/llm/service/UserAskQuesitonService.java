package com.litongjava.llm.service;

import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.llm.consts.AgentTableNames;

public class UserAskQuesitonService {

  public boolean save(String content) {
    Row record = Row.by("content", content);
    return Db.save(AgentTableNames.llm_user_asked_questions, record);
  }
}
