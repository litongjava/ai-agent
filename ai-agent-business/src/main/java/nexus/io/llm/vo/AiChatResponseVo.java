package nexus.io.llm.vo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nexus.io.model.upload.UploadResult;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponseVo {
  private String content;
  private List<String> cition;
  private Long quesitonId;
  private Long answerId;
  private String rewrite;
  private List<UploadResult> uploadFiles;

  public AiChatResponseVo(String content) {
    this.content = content;
  }
}
