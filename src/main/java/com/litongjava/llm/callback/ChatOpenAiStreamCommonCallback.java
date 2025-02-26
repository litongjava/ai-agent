package com.litongjava.llm.callback;

import java.io.IOException;
import java.util.List;

import com.jfinal.kit.Kv;
import com.litongjava.db.TableResult;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.can.ChatStreamCallCan;
import com.litongjava.llm.config.AiAgentContext;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.service.LlmChatHistoryService;
import com.litongjava.llm.service.RunningNotificationService;
import com.litongjava.openai.chat.ChatResponseDelta;
import com.litongjava.openai.chat.Choice;
import com.litongjava.openai.chat.OpenAiChatResponseVo;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.sse.SsePacket;
import com.litongjava.tio.http.server.util.SseEmitter;
import com.litongjava.tio.utils.json.FastJson2Utils;
import com.litongjava.tio.utils.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

@Slf4j
public class ChatOpenAiStreamCommonCallback implements Callback {
  private boolean continueSend = true;
  private ChannelContext channelContext;
  private long chatId, answerId, start;

  public ChatOpenAiStreamCommonCallback(ChannelContext channelContext, long chatId, long answerId, long start) {
    this.channelContext = channelContext;
    this.chatId = chatId;
    this.answerId = chatId;
    this.start = start;
  }

  @Override
  public void onResponse(Call call, Response response) throws IOException {
    if (!response.isSuccessful()) {
      String data = "Chat model response an unsuccessful message:" + response.body().string();
      log.error(data);
      RunningNotificationService notification = AiAgentContext.me().getNotification();
      notification.sendError(data);
      SsePacket packet = new SsePacket(AiChatEventName.error, data);
      Tio.bSend(channelContext, packet);
      try {
        ChatStreamCallCan.remove(chatId);
      } catch (Exception e) {
        e.printStackTrace();
      }
      SseEmitter.closeSeeConnection(channelContext);
      return;
    }

    try (ResponseBody responseBody = response.body()) {
      if (responseBody == null) {
        String message = "response body is null";
        log.error(message);
        SsePacket ssePacket = new SsePacket(AiChatEventName.progress, message);
        Tio.bSend(channelContext, ssePacket);
        return;
      }
      StringBuffer completionContent = onResponseSuccess(channelContext, responseBody, start);

      if (completionContent != null && !completionContent.toString().isEmpty()) {
        TableResult<Kv> tr = Aop.get(LlmChatHistoryService.class).saveAssistant(answerId, chatId, completionContent.toString());
        if (tr.getCode() != 1) {
          log.error("Failed to save assistant answer: {}", tr);
        } else {
          Kv kv = Kv.by("answer_id", answerId);
          SsePacket packet = new SsePacket(AiChatEventName.message_id, JsonUtils.toJson(kv));
          Tio.bSend(channelContext, packet);
        }
      }
    }
    try {
      ChatStreamCallCan.remove(chatId);
    } catch (Exception e) {
      e.printStackTrace();
    }
    SseEmitter.closeSeeConnection(channelContext);
  }

  @Override
  public void onFailure(Call call, IOException e) {
    SsePacket packet = new SsePacket(AiChatEventName.progress, "error: " + e.getMessage());
    Tio.bSend(channelContext, packet);
    ChatStreamCallCan.remove(chatId);
    SseEmitter.closeSeeConnection(channelContext);
  }

  /**
   * 处理ChatGPT成功响应
   *
   * @param channelContext 通道上下文
   * @param responseBody    响应体
   * @return 完整内容
   * @throws IOException
   */
  public StringBuffer onResponseSuccess(ChannelContext channelContext, ResponseBody responseBody, Long start) throws IOException {
    StringBuffer completionContent = new StringBuffer();
    BufferedSource source = responseBody.source();
    String line;
    boolean sentCitations = false;
    while ((line = source.readUtf8Line()) != null) {
      if (line.length() < 1) {
        continue;
      }
      // 处理数据行
      if (line.length() > 6) {
        String data = line.substring(6);
        if (data.endsWith("}")) {
          OpenAiChatResponseVo chatResponse = FastJson2Utils.parse(data, OpenAiChatResponseVo.class);
          List<String> citations = chatResponse.getCitations();
          if (citations != null && !sentCitations) {
            SsePacket ssePacket = new SsePacket(AiChatEventName.citation, JsonUtils.toJson(citations));
            Tio.bSend(channelContext, ssePacket);
            sentCitations = true;
          }
          List<Choice> choices = chatResponse.getChoices();
          if (!choices.isEmpty()) {
            ChatResponseDelta delta = choices.get(0).getDelta();
            String content = delta.getContent();
            String part = delta.getContent();
            if (part != null && !part.isEmpty()) {
              completionContent.append(part);
              SsePacket ssePacket = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(Kv.by("content", content)));
              send(channelContext, ssePacket);
            }

            String reasoning_content = delta.getReasoning_content();
            if (reasoning_content != null && !reasoning_content.isEmpty()) {
              SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(Kv.by("content", reasoning_content)));
              send(channelContext, ssePacket);
            }
          }
        } else {
          log.info("Data does not end with }:{}", line);
        }
      }
    }

    // 关闭连接
    long end = System.currentTimeMillis();
    log.info("finish llm in {} (ms)", (end - start));
    return completionContent;
  }

  private void send(ChannelContext channelContext, SsePacket ssePacket) {
    if (continueSend) {
      boolean bSend = Tio.bSend(channelContext, ssePacket);
      if (!bSend && continueSend) {
        bSend = Tio.bSend(channelContext, ssePacket);
        if (!bSend && continueSend) {
          bSend = Tio.bSend(channelContext, ssePacket);
          if (!bSend && continueSend) {
            this.continueSend = false;
          }
        }
      }
    }
  }
}
