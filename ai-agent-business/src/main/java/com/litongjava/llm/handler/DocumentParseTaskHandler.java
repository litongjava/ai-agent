package com.litongjava.llm.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.service.AiDocumentParseService;
import com.litongjava.model.TaskResponse;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;

public class DocumentParseTaskHandler implements HttpRequestHandler {

  @Override
  public HttpResponse handle(HttpRequest httpRequest) throws Exception {
    Long id = httpRequest.getLong("id");
    TaskResponse task = Aop.get(AiDocumentParseService.class).getTask(id);
    HttpResponse response = TioRequestContext.getResponse();
    return response.body(RespBodyVo.ok(task));
  }
}
