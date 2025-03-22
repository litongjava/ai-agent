package com.litongjava.llm.service;

public interface RunningNotificationService {
  boolean sendQuestion(Long tenant, String msg);

  boolean sendLike(Long tenant, String msg);

  boolean sendError(Long tenant, String msg);

  boolean sendRewrite(Long tenant, String msg);

  boolean sendPredict(Long tenant, String msg);
}
