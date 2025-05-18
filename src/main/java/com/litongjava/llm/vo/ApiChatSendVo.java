package com.litongjava.llm.vo;

import java.util.List;

import com.litongjava.chat.ChatMessage;
import com.litongjava.chat.ChatMessageArgs;

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
  private String cmd;
  private ChatMessageArgs args;
  private boolean stream;
  private boolean rewrite;
  private Long previous_question_id;
  private Long previous_answer_id;
  private List<ChatMessage> messages;
  private List<Long> file_ids;
  private String input_quesiton;
}
