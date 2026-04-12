package nexus.io.llm.service;

import nexus.io.agent.consts.AiAgentBaseTableNames;
import nexus.io.db.TableInput;
import nexus.io.db.TableResult;
import nexus.io.db.activerecord.Row;
import nexus.io.model.page.Page;
import nexus.io.table.services.ApiTable;

public class LlmQuestionRecommendService {
  public TableResult<Page<Row>> page(Integer num) {
    TableInput ti = TableInput.create();
    ti.columns("avatar,title,content").orderBy("orders").pageSize(num).set("deleted", 0);
    TableResult<Page<Row>> result = ApiTable.page(AiAgentBaseTableNames.llm_question_recommend, ti);
    return result;
  }
}
