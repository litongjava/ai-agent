package nexus.io.llm.service;

import nexus.io.agent.consts.AiAgentBaseTableNames;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;

public class UserAskQuesitonService {

  public boolean save(String content) {
    Row record = Row.by("content", content);
    return Db.save(AiAgentBaseTableNames.llm_user_asked_questions, record);
  }
}
