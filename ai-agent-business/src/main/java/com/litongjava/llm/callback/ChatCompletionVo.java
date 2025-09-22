package com.litongjava.llm.callback;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * completion
 * @author Tong Li
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionVo {
  private String model;
  private String content;
}
