package nexus.io.llm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ToolFunction {
  private String id;
  private String action;
  private String tool;
  private String code;

  public ToolFunction(String id, String action, String code) {
    this.id = id;
    this.action = action;
    this.code = code;
  }

  public ToolFunction(String id, String action) {
    this.id = id;
    this.action = action;
  }
}
