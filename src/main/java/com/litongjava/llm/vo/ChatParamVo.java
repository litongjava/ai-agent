package com.litongjava.llm.vo;

import java.util.List;

import com.litongjava.openai.chat.ChatMessage;
import com.litongjava.tio.core.ChannelContext;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain=true)
public class ChatParamVo {
  private boolean isFirstQuestion;
  private String rewriteQuestion;
  private String textQuestion;
  private List<ChatMessage> history;
  private ChannelContext channelContext;
}
