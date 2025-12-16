package com.litongjava.llm.callback;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.jfinal.kit.Kv;
import com.litongjava.gemini.GeminiCandidate;
import com.litongjava.gemini.GeminiChatResponse;
import com.litongjava.gemini.GeminiContentResponse;
import com.litongjava.gemini.GeminiPart;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.can.ChatStreamCallCan;
import com.litongjava.llm.config.AiAgentContext;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.consts.ApiChatAskType;
import com.litongjava.llm.service.FollowUpQuestionService;
import com.litongjava.llm.service.LlmChatHistoryService;
import com.litongjava.llm.service.MatplotlibService;
import com.litongjava.llm.service.RunningNotificationService;
import com.litongjava.llm.vo.ChatAskVo;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.sse.SsePacket;
import com.litongjava.tio.http.server.util.SseEmitter;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.json.FastJson2Utils;
import com.litongjava.tio.utils.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;

@Slf4j
public class ChatGeminiEventSourceListener extends EventSourceListener {

  private final LlmChatHistoryService llmChatHistoryService = Aop.get(LlmChatHistoryService.class);
  private final MatplotlibService matplotlibService = Aop.get(MatplotlibService.class);

  private final ChannelContext channelContext;
  private final ChatAskVo apiChatSendVo;
  private final long answerId;
  private final long startTs;
  private final String textQuestion;
  private final CountDownLatch latch;

  private String model;
  private final StringBuilder completionContent = new StringBuilder();
  private boolean continueSend = true;

  public ChatGeminiEventSourceListener(ChannelContext channelContext, ChatAskVo apiChatSendVo, long answerId, long startTs,
      String textQuestion) {
    this(channelContext, apiChatSendVo, answerId, startTs, textQuestion, null);
  }

  public ChatGeminiEventSourceListener(ChannelContext channelContext, ChatAskVo apiChatSendVo, long answerId, long startTs,
      String textQuestion, CountDownLatch latch) {
    this.channelContext = channelContext;
    this.apiChatSendVo = apiChatSendVo;
    this.answerId = answerId;
    this.startTs = startTs;
    this.textQuestion = textQuestion;
    this.latch = latch;
  }

  @Override
  public void onOpen(EventSource eventSource, Response response) {
    log.debug("Gemini SSE opened for session {}", apiChatSendVo.getSession_id());
  }

  @Override
  public void onEvent(EventSource eventSource, String id, String type, String data) {
    // 如果服务端约定用 [DONE] 作为结束标记，可以在这里处理
    if ("[DONE]".equals(data)) {
      return;
    }

    try {
      // 解析 Gemini 流式 JSON 片段
      GeminiChatResponse chatResponse = FastJson2Utils.parse(data, GeminiChatResponse.class);
      if (chatResponse == null) {
        log.warn("GeminiChatResponse is null, raw data: {}", data);
        return;
      }

      this.model = chatResponse.getModelVersion();

      List<GeminiCandidate> candidates = chatResponse.getCandidates();
      if (candidates == null || candidates.isEmpty()) {
        return;
      }

      GeminiContentResponse contentVo = candidates.get(0).getContent();
      if (contentVo == null) {
        return;
      }

      List<GeminiPart> parts = contentVo.getParts();
      if (parts == null || parts.isEmpty()) {
        return;
      }

      for (GeminiPart part : parts) {
        if (part == null) {
          continue;
        }
        String text = part.getText();
        if (text != null && !text.isEmpty() && continueSend) {
          completionContent.append(text);
          Kv kv = Kv.by("content", text).set("model", model);
          SsePacket packet = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(kv));
          sendPacket(packet);
        }
      }

    } catch (Exception ex) {
      log.error("Error parsing Gemini SSE chunk: {}", ex.getMessage(), ex);
    }
  }

  @Override
  public void onClosed(EventSource eventSource) {
    // 连接正常关闭时也执行收尾逻辑
    finish(eventSource);
  }

  @Override
  public void onFailure(EventSource eventSource, Throwable t, Response response) {
    String err = "Gemini SSE error: " + t.getMessage();
    log.error(err, t);

    // 发送错误事件给前端
    Tio.bSend(channelContext, new SsePacket(AiChatEventName.error, err));

    // 业务告警通知（保持和旧版本 onResponse 失败逻辑一致）
    try {
      RunningNotificationService notification = AiAgentContext.me().getNotification();
      if (notification != null) {
        Long appTenant = EnvUtils.getLong("app.tenant");
        notification.sendError(appTenant, err);
      }
    } catch (Exception e) {
      log.error("sendError notification failed: {}", e.getMessage(), e);
    }

    ChatStreamCallCan.removeCall(apiChatSendVo.getSession_id());
    finish(eventSource);
  }

  private void finish(EventSource eventSource) {
    long elapsed = System.currentTimeMillis() - startTs;
    log.info("Finished Gemini LLM session {} answerId={} in {}ms", apiChatSendVo.getSession_id(), answerId, elapsed);

    // 1. 保存助手最终回答（带 tutor 图形逻辑）
    ProcessResult codeResult = null;
    try {
      boolean genGraph = EnvUtils.getBoolean("chat.tutor.gen.functiom.graph", false);
      if (genGraph && latch != null && latch.getCount() == 1 && ApiChatAskType.tutor.equals(apiChatSendVo.getType())) {

        codeResult = matplotlibService.generateMatplot(channelContext, textQuestion, completionContent.toString());
        if (codeResult != null) {
          SsePacket codePacket = new SsePacket(AiChatEventName.code_result, JsonUtils.toJson(codeResult));
          Tio.bSend(channelContext, codePacket);
        }
        llmChatHistoryService.saveAssistant(answerId, apiChatSendVo.getSession_id(), model, completionContent.toString(), codeResult);
      } else {
        llmChatHistoryService.saveAssistant(answerId, apiChatSendVo.getSession_id(), model, completionContent.toString());
      }
    } catch (Exception ex) {
      log.error("Error saving Gemini assistant result", ex);
    }

    // 2. 通知前端 message_id
    Kv kv = Kv.by("answer_id", answerId);
    Tio.bSend(channelContext, new SsePacket(AiChatEventName.message_id, JsonUtils.toJson(kv)));

    // 3. 清理调用缓存
    ChatStreamCallCan.removeCall(apiChatSendVo.getSession_id());

    // 4. 生成后续提问 & 关闭 SSE 连接
    ChatCompletionVo completionVo = new ChatCompletionVo(model, completionContent.toString());
    if (latch == null) {
      Aop.get(FollowUpQuestionService.class).generate(channelContext, apiChatSendVo, completionVo);
      SseEmitter.closeSeeConnection(channelContext);
    } else {
      latch.countDown();
      if (latch.getCount() == 0) {
        Aop.get(FollowUpQuestionService.class).generate(channelContext, apiChatSendVo, completionVo);
        SseEmitter.closeSeeConnection(channelContext);
      }
    }
  }

  /**
   * 最多重试三次发送 SSE 包，失败则停止继续发送
   */
  private void sendPacket(SsePacket packet) {
    if (!continueSend) {
      return;
    }
    if (!Tio.bSend(channelContext, packet)) {
      if (!Tio.bSend(channelContext, packet)) {
        if (!Tio.bSend(channelContext, packet)) {
          continueSend = false;
        }
      }
    }
  }
}
