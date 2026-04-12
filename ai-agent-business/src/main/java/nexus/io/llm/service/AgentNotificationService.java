package nexus.io.llm.service;

import nexus.io.llm.context.AiAgentContext;
import nexus.io.tio.utils.environment.EnvUtils;

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
