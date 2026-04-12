package nexus.io.llm.service;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.chat.UniChatMessage;
import nexus.io.http.common.sse.SsePacket;
import nexus.io.llm.callback.ChatCompletionVo;
import nexus.io.llm.consts.AiChatEventName;
import nexus.io.llm.consts.ApiChatAskType;
import nexus.io.llm.vo.ChatAskRequest;
import nexus.io.openai.chat.ChatResponseFormatType;
import nexus.io.openai.chat.OpenAiChatRequest;
import nexus.io.openai.chat.OpenAiChatResponse;
import nexus.io.openai.client.OpenAiClient;
import nexus.io.openai.consts.OpenAiModels;
import nexus.io.template.PromptEngine;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.Tio;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.json.FastJson2Utils;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.volcengine.VolcEngineConst;
import nexus.io.volcengine.VolcEngineModels;

public class FollowUpQuestionService {

  public void generate(ChannelContext channelContext, ChatAskRequest apiChatSendVo, ChatCompletionVo callbackVo) {
    String type = apiChatSendVo.getType();
    if (ApiChatAskType.celebrity.equals(type)) {
      List<UniChatMessage> messages = apiChatSendVo.getMessages();
      String input_quesiton = apiChatSendVo.getUser_input_quesiton();
      String answer = callbackVo.getContent();
      messages.add(new UniChatMessage("user", input_quesiton));
      messages.add(new UniChatMessage("assistant", answer));
      String json = JsonUtils.toJson(messages);
      String prompt = PromptEngine.renderToStringFromDb("generate_celebrity_follow_up_prompt.txt", Kv.by("data", json));

      UniChatMessage chatMessage = new UniChatMessage("user", prompt);

      messages = new ArrayList<>();
      messages.add(chatMessage);

      OpenAiChatRequest chatRequestVo = new OpenAiChatRequest();
      chatRequestVo.setStream(false);
      chatRequestVo.setResponse_format(ChatResponseFormatType.json_object);
      chatRequestVo.setChatMessages(messages);

      OpenAiChatResponse chat = useDeepseek(chatRequestVo);
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

  private OpenAiChatResponse useDeepseek(OpenAiChatRequest chatRequestVo) {
    chatRequestVo.setModel(VolcEngineModels.DEEPSEEK_V3_250324);
    String apiKey = EnvUtils.get("VOLCENGINE_API_KEY");
    return OpenAiClient.chatCompletions(VolcEngineConst.API_PREFIX_URL, apiKey, chatRequestVo);
  }

  @SuppressWarnings("unused")
  private OpenAiChatResponse useOpenAi(OpenAiChatRequest chatRequestVo) {
    chatRequestVo.setModel(OpenAiModels.GPT_4O_MINI);
    OpenAiChatResponse chat = OpenAiClient.chatCompletions(chatRequestVo);
    return chat;
  }

}
