package com.litongjava.llm.service;

public interface RunningNotificationService {
  void sendQuestion(String msg);

  void sendPredict(String msg);

  void sendLike(String msg);

  void sendError(String msg);

  void sendRewrite(String string);
}
