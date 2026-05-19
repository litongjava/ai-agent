package nexus.io.llm.vo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nexus.io.model.upload.UploadResult;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
  private String content;
  private List<String> citions;
  private Long quesiton_id;
  private Long answer_id;
  private String rewrite;
  private List<UploadResult> upload_files;
  private ToolResult tool_result;

  public AiChatResponse(String content) {
    this.content = content;
  }
}
