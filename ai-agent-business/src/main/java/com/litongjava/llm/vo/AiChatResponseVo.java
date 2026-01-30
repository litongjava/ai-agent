package com.litongjava.llm.vo;

import java.util.List;

import com.litongjava.model.upload.UploadResult;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
