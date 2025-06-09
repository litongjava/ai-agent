package com.litongjava.llm.can;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Call;
import okhttp3.sse.EventSource;

public class ChatStreamCallCan {
  public static Map<Long, okhttp3.Call> callMap = new ConcurrentHashMap<>();
  public static Map<Long, EventSource> eventMap = new ConcurrentHashMap<>();
  public static Map<Long, List<okhttp3.Call>> callsMap = new ConcurrentHashMap<>();

  public static void stop(Long id) {
    stopCalls(id);
    stopCall(id);
    stopEvent(id);

  }

  public static List<Call> stopCalls(Long id) {
    List<Call> list = callsMap.get(id);

    if (list != null) {
      for (Call call2 : list) {
        if (!call2.isCanceled()) {
          call2.cancel();
        }

      }
    }
    return null;
  }

  private static okhttp3.Call stopCall(Long id) {
    Call call = callMap.get(id);
    if (call != null && !call.isCanceled()) {
      call.cancel();
      return callMap.remove(id);
    }
    return null;
  }

  private static EventSource stopEvent(Long id) {
    EventSource eventSource = eventMap.get(id);
    if (eventSource != null) {
      eventSource.cancel();
      return eventMap.remove(id);
    }
    return null;

  }

  public static okhttp3.Call removeCall(Long id) {
    return callMap.remove(id);
  }

  public static void put(Long chatId, Call call) {
    callMap.put(chatId, call);
  }

  public static void put(Long sessionId, List<Call> calls) {
    callsMap.put(sessionId, calls);
  }

  public static List<okhttp3.Call> removeCalls(Long sessionId, List<Call> calls) {
    return callsMap.remove(sessionId);
  }

  public static void put(Long sessionId, EventSource eventSource) {
    eventMap.put(sessionId, eventSource);
  }

  public static EventSource removeEvent(Long id) {
    return eventMap.remove(id);
  }

}
