package com.litongjava.llm.vo;

import java.util.List;

import com.litongjava.tio.boot.admin.vo.UploadResultVo;

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
  private List<UploadResultVo> uploadFiles;

  public AiChatResponseVo(String content) {
    this.content = content;
  }
}
