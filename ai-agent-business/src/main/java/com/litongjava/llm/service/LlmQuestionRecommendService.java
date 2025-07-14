package com.litongjava.llm.service;

import com.litongjava.db.TableInput;
import com.litongjava.db.TableResult;
import com.litongjava.db.activerecord.Row;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.model.page.Page;
import com.litongjava.table.services.ApiTable;

public class LlmQuestionRecommendService {
  public TableResult<Page<Row>> page(Integer num) {
    TableInput ti = TableInput.create();
    ti.columns("avatar,title,content").orderBy("orders").pageSize(num).set("deleted", 0);
    TableResult<Page<Row>> result = ApiTable.page(AgentTableNames.llm_question_recommend, ti);
    return result;
  }
}
