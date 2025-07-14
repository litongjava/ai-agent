package com.litongjava.llm.service;

import com.litongjava.llm.config.AiAgentContext;
import com.litongjava.tio.utils.environment.EnvUtils;

public class AgentNotificationService {

  public boolean sendError(String msg) {
    RunningNotificationService notification = AiAgentContext.me().getNotification();
    if (notification != null) {
      Long appTenant = EnvUtils.getLong("app.tenant");
      return notification.sendError(appTenant, msg);
    }
    return false;
  }

  public boolean sendPredict(String msg) {
    RunningNotificationService notification = AiAgentContext.me().getNotification();
    if (notification != null) {
      Long appTenant = EnvUtils.getLong("app.tenant");
      return notification.sendPredict(appTenant, msg);
    }
    return false;
  }
}
