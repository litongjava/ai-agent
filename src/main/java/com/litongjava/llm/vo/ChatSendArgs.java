package com.litongjava.llm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ChatSendArgs {
  private String name;
  private String institution;
  private String url;
}
