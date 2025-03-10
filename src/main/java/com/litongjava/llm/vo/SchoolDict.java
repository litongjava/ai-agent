package com.litongjava.llm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchoolDict {
  private Long id;
  private String full_name;
  private String abbr_name;
  private String bot_name;
  private String domain_name;
}
