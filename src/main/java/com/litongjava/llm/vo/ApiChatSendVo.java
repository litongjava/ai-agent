package com.litongjava.llm.vo;

import java.util.List;

import com.litongjava.openai.chat.ChatMessage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ApiChatSendVo {
  private String provider;
  private String model;
  private String type;
  private String user_id;
  private Long session_id;
  private Long app_id;
  private Long school_id;
  private Integer chat_type;
  private boolean rewrite;
  private boolean stream;
  private List<ChatMessage> messages;
  private List<Long> file_ids;
  private String input_quesiton;
}
