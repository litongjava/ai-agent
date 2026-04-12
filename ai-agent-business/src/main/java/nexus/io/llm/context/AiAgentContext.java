package nexus.io.llm.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nexus.io.llm.service.RunningNotificationService;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAgentContext {
  private static AiAgentContext me = new AiAgentContext();

  public static AiAgentContext me() {
    return me;
  }

  private RunningNotificationService notification;
}
