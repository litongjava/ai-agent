package com.litongjava.llm.callback;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.jfinal.kit.Kv;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.can.ChatStreamCallCan;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.llm.consts.ApiChatAskType;
import com.litongjava.llm.service.FollowUpQuestionService;
import com.litongjava.llm.service.LlmChatHistoryService;
import com.litongjava.llm.service.MatplotlibService;
import com.litongjava.llm.vo.ChatAskVo;
import com.litongjava.openai.chat.ChatResponseDelta;
import com.litongjava.openai.chat.Choice;
import com.litongjava.openai.chat.OpenAiChatResponseVo;
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
public class ChatOpenAiEventSourceListener extends EventSourceListener {

  private LlmChatHistoryService llmChatHistoryService = Aop.get(LlmChatHistoryService.class);
  private MatplotlibService matplotlibService = Aop.get(MatplotlibService.class);

  private final ChannelContext channelContext;
  private final ChatAskVo apiChatSendVo;
  private final long answerId;
  private final long startTs;
  private final String textQuestion;
  private final CountDownLatch latch;

  private String model;
  private final StringBuilder completionContent = new StringBuilder();
  private boolean sentCitations = false;
  private boolean continueSend = true;

  public ChatOpenAiEventSourceListener(ChannelContext channelContext, ChatAskVo apiChatSendVo, long answerId,
      long startTs, String textQuestion) {
    this(channelContext, apiChatSendVo, answerId, startTs, textQuestion, null);
  }

  public ChatOpenAiEventSourceListener(ChannelContext channelContext, ChatAskVo apiChatSendVo, long answerId,
      long startTs, String textQuestion, CountDownLatch latch) {
    this.channelContext = channelContext;
    this.apiChatSendVo = apiChatSendVo;
    this.answerId = answerId;
    this.startTs = startTs;
    this.textQuestion = textQuestion;
    this.latch = latch;
  }

  @Override
  public void onOpen(EventSource eventSource, Response response) {
    // 可以在这里推送一个“连接建立”事件，如果需要
    log.debug("SSE opened for session {}", apiChatSendVo.getSession_id());
  }

  @Override
  public void onEvent(EventSource eventSource, String id, String type, String data) {
    // OpenAI 最后会推送一个 [DONE] 事件
    if ("[DONE]".equals(data)) {
      finish(eventSource);
      return;
    }

    try {
      // 1. 解析 JSON
      OpenAiChatResponseVo chatResp = FastJson2Utils.parse(data, OpenAiChatResponseVo.class);
      this.model = chatResp.getModel();

      // 2. 第一次发 citations
      List<String> citations = chatResp.getCitations();
      if (!sentCitations && citations != null) {
        Tio.bSend(channelContext, new SsePacket(AiChatEventName.citation, JsonUtils.toJson(citations)));
        sentCitations = true;
      }

      // 3. 拿到 delta
      Choice choice = chatResp.getChoices().get(0);
      ChatResponseDelta delta = choice.getDelta();

      // 4. reasoning_content
      String reason = delta.getReasoning_content();
      if (reason != null && !reason.isEmpty()) {
        Kv kv = Kv.by("content", reason).set("model", model);
        Tio.bSend(channelContext, new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(kv)));
      }

      // 5. content
      String chunk = delta.getContent();
      if (chunk != null && !chunk.isEmpty() && continueSend) {
        completionContent.append(chunk);
        Kv kv = Kv.by("content", chunk).set("model", model);
        SsePacket p = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(kv));
        sendPacket(p);
      }

    } catch (Exception ex) {
      log.error("Error parsing SSE chunk: {}", ex.getMessage(), ex);
    }
  }

  @Override
  public void onClosed(EventSource eventSource) {
    // 如果后端主动关流，也走 finish 逻辑
    finish(eventSource);
  }

  @Override
  public void onFailure(EventSource eventSource, Throwable t, Response response) {
    String err = "SSE error: " + t.getMessage();
    Tio.bSend(channelContext, new SsePacket(AiChatEventName.error, err));
    ChatStreamCallCan.removeCall(apiChatSendVo.getSession_id());
    finish(eventSource);
  }

  private void finish(EventSource eventSource) {
    long elapsed = System.currentTimeMillis() - startTs;
    log.info("Finished LLM session {} answerId={} in {}ms", apiChatSendVo.getSession_id(), answerId, elapsed);

    // 1. 保存助手的最终回答
    ProcessResult codeResult = null;
    try {
      boolean genGraph = EnvUtils.getBoolean("chat.tutor.gen.functiom.graph", false);
      if (genGraph && latch != null && latch.getCount() == 1 && ApiChatAskType.tutor.equals(apiChatSendVo.getType())) {
        codeResult = matplotlibService.generateMatplot(channelContext, textQuestion, completionContent.toString());
        if (codeResult != null) {
          Tio.bSend(channelContext, new SsePacket(AiChatEventName.code_result, JsonUtils.toJson(codeResult)));
        }
        llmChatHistoryService.saveAssistant(answerId, apiChatSendVo.getSession_id(), model,
            completionContent.toString(), codeResult);
      } else {
        llmChatHistoryService.saveAssistant(answerId, apiChatSendVo.getSession_id(), model,
            completionContent.toString());
      }
    } catch (Exception ex) {
      log.error("Error saving assistant result", ex);
    }

    // 2. 通知前端 message_id
    Kv kv = Kv.by("answer_id", answerId);
    Tio.bSend(channelContext, new SsePacket(AiChatEventName.message_id, JsonUtils.toJson(kv)));

    // 3. 清理
    ChatStreamCallCan.removeCall(apiChatSendVo.getSession_id());

    // 4. 后续提问生成 & 关闭 SSE
    if (latch == null) {
      Aop.get(FollowUpQuestionService.class).generate(channelContext, apiChatSendVo,
          new ChatCompletionVo(model, completionContent.toString()));
      SseEmitter.closeSeeConnection(channelContext);
    } else {
      latch.countDown();
      if (latch.getCount() == 0) {
        Aop.get(FollowUpQuestionService.class).generate(channelContext, apiChatSendVo,
            new ChatCompletionVo(model, completionContent.toString()));
        SseEmitter.closeSeeConnection(channelContext);
      }
    }

    // 5. 主动 cancel 底层连接（可选）
    eventSource.cancel();
  }

  /** 三次重试发送 SSE，遇断就放弃 */
  private void sendPacket(SsePacket packet) {
    if (!continueSend)
      return;
    if (!Tio.bSend(channelContext, packet)) {
      if (!Tio.bSend(channelContext, packet)) {
        if (!Tio.bSend(channelContext, packet)) {
          continueSend = false;
        }
      }
    }
  }
}
