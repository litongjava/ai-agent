package nexus.io.llm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ToolResult {
  private String id;
  private String action;
  private String tool;
  private String code;
}
