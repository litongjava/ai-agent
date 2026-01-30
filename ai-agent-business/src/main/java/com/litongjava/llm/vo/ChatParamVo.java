package com.litongjava.llm.vo;

import java.util.List;

import com.litongjava.chat.UniChatMessage;
import com.litongjava.model.upload.UploadResult;
import com.litongjava.openai.ChatProvider;
import com.litongjava.tio.core.ChannelContext;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ChatParamVo {
  private boolean isFirstQuestion;
  private String rewriteQuestion;
  private String systemPrompt;
  private String textQuestion;
  private List<UniChatMessage> history;
  private ChannelContext channelContext;
  private List<UploadResult> uploadFiles;
  private ChatProvider chatProvider;
}
