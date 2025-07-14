package com.litongjava.llm.callback;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatCallbackVo {
  private String model;
  private String content;
}
