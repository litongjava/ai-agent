package com.litongjava.llm.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSONObject;
import com.jfinal.kit.Kv;
import com.litongjava.db.TableResult;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.can.ChatStreamCallCan;
import com.litongjava.llm.config.AiAgentContext;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.llm.service.LlmChatHistoryService;
import com.litongjava.llm.service.LlmChatSessionService;
import com.litongjava.llm.service.LlmQuestionRecommendService;
import com.litongjava.llm.service.RunningNotificationService;
import com.litongjava.llm.service.SearchPromptService;
import com.litongjava.llm.service.VectorService;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.model.page.Page;
import com.litongjava.openai.embedding.EmbeddingResponseVo;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.json.FastJson2Utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiChatHandler {

  public HttpResponse recommend(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    int num = 4;
    String name = request.getParam("num");
    if (StrUtil.isNotEmpty(name)) {
      num = Integer.parseInt(name);
    }

    TableResult<Page<Row>> tr = Aop.get(LlmQuestionRecommendService.class).page(num);

    RespBodyVo respBodyVo = null;
    if (tr.isOk()) {
      List<Row> list = tr.getData().getList();
      List<Kv> kvs = list.stream().map(e -> e.toKv()).collect(Collectors.toList());
      respBodyVo = RespBodyVo.ok(kvs);
    } else {
      respBodyVo = RespBodyVo.fail();
    }

    return response.setJson(respBodyVo);
  }

  public HttpResponse createSession(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    String bodyString = request.getBodyString();

    String userId = TioRequestContext.getUserIdString();
    String name = null;
    String schoolIdString = null;
    Integer chatType = null;
    String type = null;
    Long appId = null;
    if (bodyString == null) {
      name = request.getParam("name");
      schoolIdString = request.getParam("school_id");
      if (schoolIdString == null) {
        schoolIdString = request.getParam("schoolId");
      }
      chatType = request.getInt("chat_type");
      type = request.getString("type");
      appId = request.getLong("app_id");
      if (userId == null) {
        userId = request.getString("user_id");
      }
    } else {
      JSONObject parseObject = FastJson2Utils.parseObject(bodyString);
      name = parseObject.getString("name");
      schoolIdString = parseObject.getString("school_id");
      if (schoolIdString == null) {
        schoolIdString = parseObject.getString("schoolId");
      }
      chatType = parseObject.getInteger("chat_type");
      type = parseObject.getString("type");
      appId = parseObject.getLong("app_id");
      if (userId == null) {
        userId = parseObject.getString("user_id");
      }
    }

    if (chatType == null) {
      chatType = 0;
    }

    RespBodyVo respBodyVo = null;

    if (StrUtil.isBlank(name)) {
      response.setStatus(400);
      respBodyVo = RespBodyVo.fail("name cannot be empty");
      return response.setJson(respBodyVo);
    }
    if (StrUtil.isBlank(schoolIdString)) {
      schoolIdString = "0";
    }

    Long school_id = Long.parseLong(schoolIdString);
    TableResult<Kv> tr = Aop.get(LlmChatSessionService.class).create(userId, name, school_id, type, chatType, appId);
    if (tr.isOk()) {
      respBodyVo = RespBodyVo.ok(tr.getData());
    } else {
      respBodyVo = RespBodyVo.fail();
    }

    return response.setJson(respBodyVo);
  }

  public HttpResponse deleteSession(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    Long sesion_id = request.getLong("session_id");
    log.info("sesion_id", sesion_id);
    if (sesion_id == null) {
      try {
        sesion_id = request.getLong("chat_id");
      }catch (Exception e) {
        return response.fail(RespBodyVo.fail(e.getMessage()));
      }
      
      if (sesion_id == null) {
        return response.fail(RespBodyVo.fail("sesion_id can not be empty"));
      }
    }

    String userId = TioRequestContext.getUserIdString();
    int updateResult = Aop.get(LlmChatSessionService.class).softDelete(sesion_id, userId);
    if (updateResult > 0) {
      response.setJson(RespBodyVo.ok());
    } else {
      response.setJson(RespBodyVo.fail());
    }
    return response;
  }

  public HttpResponse listSession(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    Integer pageNo = request.getInt("offset");
    Integer pageSize = request.getInt("limit");
    Long schoolId = request.getLong("school_id");
    Integer chatType = request.getInt("chat_type");

    if (pageNo == null) {
      pageNo = 1;
    }
    if (pageSize == null) {
      pageSize = 10;
    }

    if (chatType == null) {
      chatType = 0;
    }
    String userId = TioRequestContext.getUserIdString();
    if (userId == null) {
      userId = request.getString("user_id");
    }

    List<Kv> list = Aop.get(LlmChatSessionService.class).page(pageNo, pageSize, userId, schoolId, chatType);

    return response.setJson(RespBodyVo.ok(list));
  }

  public HttpResponse setSessionName(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    Long sessionId = request.getLong("session_id");
    if (sessionId == null) {
      sessionId = request.getLong("chat_id");
      if (sessionId == null) {
        return response.fail(RespBodyVo.fail("session_id can not be empty"));
      }
    }

    String name = request.getParam("name");

    if (StrUtil.isEmpty(name)) {
      return response.fail(RespBodyVo.fail("name can not be empty"));
    }

    String userId = TioRequestContext.getUserIdString();

    boolean exists = Aop.get(LlmChatSessionService.class).exists(userId, sessionId);
    if (!exists) {
      return response.fail(RespBodyVo.fail("invalid session"));
    }

    int updateResult = Aop.get(LlmChatSessionService.class).updateSessionName(name, sessionId, userId);
    RespBodyVo respBodyVo = null;
    if (updateResult > 0) {
      respBodyVo = RespBodyVo.ok();
    } else {
      respBodyVo = RespBodyVo.fail();
    }
    return response.setJson(respBodyVo);
  }

  public HttpResponse getChatHistory(HttpRequest request) {
    String userId = TioRequestContext.getUserIdString();

    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    Long session_id = request.getLong("session_id");
    if (session_id == null) {
      try {
        session_id = request.getLong("chat_id");
      } catch (Exception e) {
        return response.fail(RespBodyVo.fail(e.getMessage()));
      }

      if (session_id == null) {
        return response.fail(RespBodyVo.fail("chat_id can not be empty"));
      }
    }

    Integer pageNo = request.getInt("offset");
    Integer pageSize = request.getInt("limit");

    if (userId == null) {
      userId = request.getString("user_id");
    }
    if (pageNo == null) {
      pageNo = 1;

    }

    if (pageSize == null) {
      pageSize = 100;
    }

    LlmChatSessionService llmChatSessionService = Aop.get(LlmChatSessionService.class);
    boolean exists = llmChatSessionService.exists(userId, session_id);
    if (!exists) {
      log.info("invalid session:{},{}", session_id, userId);
      return response.fail(RespBodyVo.fail("invalid session"));
    }

    LlmChatHistoryService chatHistoryService = Aop.get(LlmChatHistoryService.class);
    RespBodyVo ok = chatHistoryService.getHistory(session_id, pageNo, pageSize);
    return response.setJson(ok);
  }

  public HttpResponse like(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    String bodyString = request.getBodyString();

    JSONObject requestVo = FastJson2Utils.parseObject(bodyString);
    Long questionId = requestVo.getLong("question_id");
    Long answerId = requestVo.getLong("answer_id");
    Boolean like = requestVo.getBoolean("like");

    if (questionId == null) {
      return response.fail(RespBodyVo.fail("question_id can not be empty"));
    }

    if (answerId == null) {
      return response.fail(RespBodyVo.fail("answer_id can not be empty"));
    }

    if (like == null) {
      return response.fail(RespBodyVo.fail("like can not be empty"));
    }

    String userId = TioRequestContext.getUserIdString();

    boolean exists = Db.exists(AgentTableNames.llm_chat_history, "id", questionId);
    if (!exists) {
      return response.fail(RespBodyVo.fail("invalid quesion id"));
    }
    exists = Db.exists(AgentTableNames.llm_chat_history, "id", answerId);
    if (!exists) {
      return response.fail(RespBodyVo.fail("invalid answer id"));
    }

    Aop.get(LlmChatHistoryService.class).like(questionId, answerId, like, userId);

    String sql = "SELECT (SELECT content FROM %s WHERE id = ?) AS question,(SELECT content FROM %s WHERE id = ?) AS answer";
    sql = String.format(sql, AgentTableNames.llm_chat_history, AgentTableNames.llm_chat_history);

    Row row = Db.findFirst(sql, questionId, answerId);
    StringBuffer messageText = new StringBuffer();
    if (like) {
      messageText.append("赞").append("\r\n");
    } else {
      messageText.append("踩").append("\r\n");
    }
    String question = row.getStr("question");
    String answer = row.getString("answer");

    messageText.append("app.env:").append(EnvUtils.env()).append("\r\n");
    messageText.append("user_id:").append(userId).append("\r\n");
    messageText.append("question_id:").append(questionId).append("\r\n");
    messageText.append("question:").append(question).append("\r\n\r\n");
    messageText.append("answer_id:").append(answerId).append("\r\n");
    messageText.append("answer:").append(answer).append("\r\n");

    RunningNotificationService notification = AiAgentContext.me().getNotification();
    if (notification != null) {
      Long appTenant = EnvUtils.getLong("app.tenant");
      notification.sendLike(appTenant, messageText.toString());
    }
    return response.setJson(RespBodyVo.ok());
  }

  public HttpResponse stop(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    Long session_id = request.getLong("session_id");
    if (session_id == null) {
      session_id = request.getLong("chat_id");
      if (session_id == null) {
        return response.setJson(RespBodyVo.fail("id can not be empty"));
      }
    }

    ChatStreamCallCan.stop(session_id);
    return response.setJson(RespBodyVo.ok());
  }

  /**
   * 意图识别与提示词生成
   *
   * @param request HTTP请求对象
   * @return HTTP响应对象
   */
  public HttpResponse recognizeIntent(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    Long schoolId = request.getLong("school_id");
    String queryText = request.getParam("query_text");

    if (schoolId == null) {
      return response.fail(RespBodyVo.fail("school_id cannot be empty"));
    }

    if (StrUtil.isBlank(queryText)) {
      return response.fail(RespBodyVo.fail("query_text cannot be empty"));
    }

    SearchPromptService searchPromptService = Aop.get(SearchPromptService.class);
    String prompt = searchPromptService.index(schoolId, queryText, false, null);

    Map<String, String> data = new HashMap<>();
    data.put("prompt", prompt);

    return response.setJson(RespBodyVo.ok(data));
  }

  /**
   * 获取文本向量
   *
   * @param request HTTP请求对象
   * @return HTTP响应对象
   */
  public HttpResponse getVector(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    String text = request.getParam("text");
    String model = request.getParam("model");

    if (StrUtil.isBlank(text)) {
      return response.fail(RespBodyVo.fail("text cannot be empty"));
    }

    if (StrUtil.isBlank(model)) {
      return response.fail(RespBodyVo.fail("model cannot be empty"));
    }

    VectorService vectorService = Aop.get(VectorService.class);
    EmbeddingResponseVo embedding = vectorService.getVector(text, model);

    Map<String, Object> data = new HashMap<>();
    data.put("embedding", embedding.getData().get(0).getEmbedding());
    data.put("model", embedding.getModel());

    return response.setJson(RespBodyVo.ok(data));
  }
}
