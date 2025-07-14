package com.litongjava.llm.config;

import com.litongjava.llm.service.RunningNotificationService;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
