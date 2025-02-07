package com.litongjava.llm.vo;

import java.util.List;

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

  public AiChatResponseVo(String content) {
    this.content = content;
  }
}
