package com.litongjava.llm.service;

public interface RunningNotificationService {
  void sendQuestion(String string);

  void sendPredict(String requestJson);

  void sendLike(StringBuffer messageText);
}
