package com.litongjava.llm.sql;

public interface LlmIntentClassificationSql {

  String intent = """
      --# llm_intent_classification.intent
       SELECT
         c.id,
         c.name,
         c.action,
         c.additional_info,
         c.include_general,
         q.question,
         (1 - (q.question_vector <=> input.input_vector)) AS similarity
       FROM
         llm_intent_question q
         LEFT JOIN llm_intent_classification c ON q.category_id = c.id,
         LATERAL (
           VALUES (
             ?::VECTOR(3072)
           )
         ) AS input(input_vector)
       WHERE
         c.env = ?
         AND (1 - (q.question_vector <=> input.input_vector)) > 0.4
       ORDER BY
         similarity DESC
       LIMIT 1;
             """;
}
