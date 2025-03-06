package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;
import com.litongjava.llm.callback.ChatCallbackVo;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.consts.ApiChatSendType;
import com.litongjava.llm.vo.ApiChatSendVo;
import com.litongjava.openai.chat.ChatMessage;
import com.litongjava.openai.chat.ChatResponseFormatType;
import com.litongjava.openai.chat.OpenAiChatRequestVo;
import com.litongjava.openai.chat.OpenAiChatResponseVo;
import com.litongjava.openai.client.OpenAiClient;
import com.litongjava.openai.constants.OpenAiModels;
import com.litongjava.template.PromptEngine;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.sse.SsePacket;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.json.FastJson2Utils;
import com.litongjava.tio.utils.json.JsonUtils;
import com.litongjava.volcengine.VolcEngineConst;
import com.litongjava.volcengine.VolcEngineModels;

public class FollowUpQuestionService {

  public void generate(ChannelContext channelContext, ApiChatSendVo apiChatSendVo, ChatCallbackVo callbackVo) {
    String type = apiChatSendVo.getType();
    if (ApiChatSendType.celebrity.equals(type)) {
      List<ChatMessage> messages = apiChatSendVo.getMessages();
      String input_quesiton = apiChatSendVo.getInput_quesiton();
      String answer = callbackVo.getContent();
      messages.add(new ChatMessage("user", input_quesiton));
      messages.add(new ChatMessage("assistant", answer));
      String json = JsonUtils.toJson(messages);
      String prompt = PromptEngine.renderToString("generate_celebrity_follow_up_prompt.txt", Kv.by("data", json));

      ChatMessage chatMessage = new ChatMessage("user", prompt);

      messages = new ArrayList<>();
      messages.add(chatMessage);

      OpenAiChatRequestVo chatRequestVo = new OpenAiChatRequestVo();
      chatRequestVo.setStream(false);
      chatRequestVo.setResponse_format(ChatResponseFormatType.json_object);
      chatRequestVo.setChatMessages(messages);

      OpenAiChatResponseVo chat = useDeepseek(chatRequestVo);
      String content = chat.getChoices().get(0).getMessage().getContent();
      if (content.startsWith("```json")) {
        content = content.substring(7, content.length() - 3);
      }
      content = FastJson2Utils.parseObject(content).getJSONArray("questions").toJSONString();
      if (channelContext != null) {
        SsePacket packet = new SsePacket(AiChatEventName.suggested_questions, content);
        Tio.send(channelContext, packet);
      }

    }

  }

  private OpenAiChatResponseVo useDeepseek(OpenAiChatRequestVo chatRequestVo) {
    chatRequestVo.setModel(VolcEngineModels.DEEPSEEK_V3_241226);
    String apiKey = EnvUtils.get("VOLCENGINE_API_KEY");
    return OpenAiClient.chatCompletions(VolcEngineConst.BASE_URL, apiKey, chatRequestVo);
  }

  @SuppressWarnings("unused")
  private OpenAiChatResponseVo useOpenAi(OpenAiChatRequestVo chatRequestVo) {
    chatRequestVo.setModel(OpenAiModels.GPT_4O_MINI);
    OpenAiChatResponseVo chat = OpenAiClient.chatCompletions(chatRequestVo);
    return chat;
  }

}
