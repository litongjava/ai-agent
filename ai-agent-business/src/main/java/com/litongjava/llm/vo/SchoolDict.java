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
  private String professor_names;
  private String class_names;
  private String domain_name;

  public SchoolDict(Long id, String fullName) {
    this.id = id;
    this.full_name = fullName;
  }

  public SchoolDict(Long id, String fullName, String abbrName, String professor_names, String botName) {
    this.id = id;
    this.full_name = fullName;
    this.abbr_name = abbrName;
    this.professor_names = professor_names;
    this.bot_name = botName;
  }

  public SchoolDict(Long id, String fullName, String abbrName, String professor_names, String class_names, String botName) {
    this.id = id;
    this.full_name = fullName;
    this.abbr_name = abbrName;
    this.professor_names = professor_names;
    this.class_names = class_names;
    this.bot_name = botName;
  }
}
