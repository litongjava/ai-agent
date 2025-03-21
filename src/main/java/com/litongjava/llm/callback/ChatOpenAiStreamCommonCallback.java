package com.litongjava.llm.callback;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.jfinal.kit.Kv;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.linux.ProcessResult;
import com.litongjava.llm.can.ChatStreamCallCan;
import com.litongjava.llm.config.AiAgentContext;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.consts.ApiChatSendType;
import com.litongjava.llm.service.FollowUpQuestionService;
import com.litongjava.llm.service.LlmChatHistoryService;
import com.litongjava.llm.service.MatplotlibService;
import com.litongjava.llm.service.RunningNotificationService;
import com.litongjava.llm.vo.ApiChatSendVo;
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

  LlmChatHistoryService llmChatHistoryService = Aop.get(LlmChatHistoryService.class);
  MatplotlibService matplotlibService = Aop.get(MatplotlibService.class);

  private boolean continueSend = true;
  private ChannelContext channelContext;
  private long answerId, start;
  private ApiChatSendVo apiChatSendVo;
  private CountDownLatch latch;
  private ChatCallbackVo callbackVo;

  public ChatOpenAiStreamCommonCallback(ChannelContext channelContext, ApiChatSendVo apiChatSendVo, long answerId, long start) {
    this.channelContext = channelContext;
    this.apiChatSendVo = apiChatSendVo;
    this.answerId = answerId;
    this.start = start;
  }

  public ChatOpenAiStreamCommonCallback(ChannelContext channelContext, ApiChatSendVo apiChatSendVo, long answerId, long start, CountDownLatch latch) {
    this.channelContext = channelContext;
    this.apiChatSendVo = apiChatSendVo;
    this.answerId = answerId;
    this.start = start;
    this.latch = latch;
  }

  @Override
  public void onResponse(Call call, Response response) throws IOException {
    if (!response.isSuccessful()) {
      String errorBody = response.body().string();
      String data = "Chat model response an unsuccessful message:" + errorBody;
      log.error(data);
      RunningNotificationService notification = AiAgentContext.me().getNotification();
      if (notification != null) {
        notification.sendError(data);
      }
      SsePacket packet = new SsePacket(AiChatEventName.error, data);
      Tio.bSend(channelContext, packet);
      try {
        ChatStreamCallCan.remove(apiChatSendVo.getSchool_id());
      } catch (Exception e) {
        e.printStackTrace();
      }
      response.close();
      close();
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
      ChatCallbackVo success = onSuccess(channelContext, responseBody, start);
      this.callbackVo = success;

      if (success != null && !success.getContent().isEmpty()) {
        String content = success.getContent();
        if (latch.getCount() == 1 && ApiChatSendType.tutor.equals(apiChatSendVo.getType())) {

          ProcessResult codeResult = matplotlibService.generateMatplot(content);

          if (codeResult != null) {
            String json = JsonUtils.toJson(codeResult);
            SsePacket packet = new SsePacket(AiChatEventName.code_result, json);
            Tio.bSend(channelContext, packet);
          }

          try {
            llmChatHistoryService.saveAssistant(answerId, apiChatSendVo.getSession_id(), success.getModel(), content.toString(), codeResult);
          } catch (Exception e) {
            log.error(e.getMessage(), e);
          }

        } else {
          try {
            llmChatHistoryService.saveAssistant(answerId, apiChatSendVo.getSession_id(), success.getModel(), content.toString());
          } catch (Exception e) {
            log.error(e.getMessage(), e);
          }
        }

        Kv kv = Kv.by("answer_id", answerId);
        SsePacket packet = new SsePacket(AiChatEventName.message_id, JsonUtils.toJson(kv));
        Tio.bSend(channelContext, packet);
      }
    }
    try {
      ChatStreamCallCan.remove(apiChatSendVo.getSession_id());
    } catch (Exception e) {
      e.printStackTrace();
    }
    close();
  }

  //关闭连接
  private void close() {
    if (latch == null) {
      Aop.get(FollowUpQuestionService.class).generate(channelContext, apiChatSendVo, callbackVo);
      SseEmitter.closeSeeConnection(channelContext);
    } else {
      latch.countDown();
      if (latch.getCount() == 0) {
        Aop.get(FollowUpQuestionService.class).generate(channelContext, apiChatSendVo, callbackVo);
        SseEmitter.closeSeeConnection(channelContext);
      }
    }
  }

  @Override
  public void onFailure(Call call, IOException e) {
    SsePacket packet = new SsePacket(AiChatEventName.progress, "error: " + e.getMessage());
    Tio.bSend(channelContext, packet);
    ChatStreamCallCan.remove(apiChatSendVo.getSession_id());
    close();
  }

  /**
   * 处理ChatGPT成功响应
   *
   * @param channelContext 通道上下文
   * @param responseBody    响应体
   * @return 完整内容
   * @throws IOException
   */
  public ChatCallbackVo onSuccess(ChannelContext channelContext, ResponseBody responseBody, Long start) throws IOException {
    String model = null;
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
          model = chatResponse.getModel();
          List<String> citations = chatResponse.getCitations();
          if (citations != null && !sentCitations) {
            SsePacket ssePacket = new SsePacket(AiChatEventName.citation, JsonUtils.toJson(citations));
            Tio.bSend(channelContext, ssePacket);
            sentCitations = true;
          }
          List<Choice> choices = chatResponse.getChoices();
          if (!choices.isEmpty()) {
            ChatResponseDelta delta = choices.get(0).getDelta();
            String reasoning_content = delta.getReasoning_content();
            if (reasoning_content != null && !reasoning_content.isEmpty()) {
              Kv by = Kv.by("content", reasoning_content).set("model", model);
              SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
              send(channelContext, ssePacket);
            }

            String content = delta.getContent();
            if (content != null && !content.isEmpty()) {
              completionContent.append(content);
              Kv by = Kv.by("content", content).set("model", model);
              SsePacket ssePacket = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(by));
              try {
                send(channelContext, ssePacket);
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                continueSend = false;
              }

            }
          }
        }
      }
    }

    long end = System.currentTimeMillis();
    log.info("finish llm in {} {} {}(ms)", apiChatSendVo.getSession_id(), answerId, (end - start));
    return new ChatCallbackVo(model, completionContent.toString());
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
