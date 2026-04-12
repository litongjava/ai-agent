package nexus.io.llm.vo;


import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import nexus.io.chat.UniChatMessage;
import nexus.io.model.upload.UploadResult;
import nexus.io.openai.ChatProvider;
import nexus.io.tio.core.ChannelContext;

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
