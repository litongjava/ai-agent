package com.litongjava.llm.consts;

public interface AiChatEventName {
  String progress = "progress";
  String question = "question";
  String rewrite = "rewrite";
  String markdown = "markdown";

  String delta = "delta";
  String reasoning = "reasoning";
  String citation = "citation";
  String video = "video";
  String message_id = "message_id";

  String summary_question = "summary_question";
  String table = "table";
  String input = "input";
  String rerank = "rerank";
  String need_login = "need_login";
  String error = "error";
  String paragraph = "paragraph";
  String documents = "documents";

  String linkedin = "linkedin";
  String linkedin_posts = "linkedin_posts";
  String social_media = "social_media";
  String suggested_questions = "suggested_questions";
}