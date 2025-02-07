package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.can.ChatStreamCallCan;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.vo.AiChatResponseVo;
import com.litongjava.llm.vo.ApiChatSendVo;
import com.litongjava.llm.vo.ChatParamVo;
import com.litongjava.openai.chat.ChatMessage;
import com.litongjava.openai.chat.OpenAiChatRequestVo;
import com.litongjava.openai.chat.OpenAiChatResponseVo;
import com.litongjava.openai.client.OpenAiClient;
import com.litongjava.openai.constants.OpenAiModels;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.sse.SsePacket;
import com.litongjava.tio.utils.json.JsonUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;

@Slf4j
public class LLmChatOpenAiService {

  /**
   * 使用搜索模型处理消息
   *
   * @param schoolId        学校ID
   * @param sessionId       会话ID
   * @param chatMessages    消息对象
   * @param isFirstQuestion 是否为首次提问
   * @param history  关联消息
   * @param stream          是否使用流式响应
   * @param channelContext  通道上下文
   * @param aiChatResponseVo 
   * @return 响应对象
   */
  public AiChatResponseVo predict(ApiChatSendVo apiSendVo, ChatParamVo paramVo, AiChatResponseVo aiChatResponseVo) {
    Boolean stream = apiSendVo.isStream();
    ChannelContext channelContext = paramVo.getChannelContext();
    List<ChatMessage> history = paramVo.getHistory();
    Long sesionId = apiSendVo.getSession_id();
    List<ChatMessage> messages = apiSendVo.getMessages();
    String rewrite_quesiton = apiSendVo.getRewrite_quesiton();
    String textQuestion = null;
    if (rewrite_quesiton == null) {
      textQuestion = apiSendVo.getInput_quesiton();
    }

    // 发送搜索进度
    if (stream && channelContext != null) {
      Kv kv = Kv.by("content", "- Searching... \r\n");
      SsePacket packet = new SsePacket(AiChatEventName.progress, JsonUtils.toJson(kv));
      Tio.bSend(channelContext, packet);
    }

    if (messages == null) {
      messages = new ArrayList<>();
    }
    // 添加初始提示词
    //    if (intent) {
    //      String initPrompt = Aop.get(SearchPromptService.class).index(schoolId, textQuestion, stream, channelContext);
    //      messages.add(0, new ChatMessage("system", initPrompt));
    //    }

    //添加历史
    messages.addAll(history);
    // 添加用户问题
    messages.add(new ChatMessage("user", textQuestion));
    if (stream) {
      SsePacket packet = new SsePacket(AiChatEventName.input, JsonUtils.toJson(history));
      Tio.bSend(channelContext, packet);
    }

    OpenAiChatRequestVo chatRequestVo = new OpenAiChatRequestVo().setModel(OpenAiModels.GPT_4O_MINI)
        //
        .setChatMessages(messages);

    log.info("chatRequestVo:{}", JsonUtils.toSkipNullJson(chatRequestVo));
    if (stream) {
      // 发送回复提示
      //      Kv kv = Kv.by("content", "- Reply to your question.\r\n\r\n");
      //      SsePacket replyPacket = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(kv));
      //      Tio.bSend(channelContext, replyPacket);

      chatRequestVo.setStream(true);
      long start = System.currentTimeMillis();

      Callback callback = Aop.get(ChatOpenAiStreamCommonService.class).getCallback(channelContext, sesionId, start);
      Call call = OpenAiClient.chatCompletions(chatRequestVo, callback);
      ChatStreamCallCan.put(sesionId, call);
      return null;
    } else {
      OpenAiChatResponseVo chatCompletions = OpenAiClient.chatCompletions(chatRequestVo);
      List<String> citations = chatCompletions.getCitations();
      String answerContent = chatCompletions.getChoices().get(0).getMessage().getContent();
      long answerId = SnowflakeIdUtils.id();
      Aop.get(LlmChatHistoryService.class).saveAssistant(answerId, sesionId, answerContent);
      aiChatResponseVo.setContent(answerContent);
      aiChatResponseVo.setAnswerId(answerId);
      aiChatResponseVo.setCition(citations);
      return aiChatResponseVo;
    }
  }

}