package nexus.io.ai.agent.handler;


import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.service.AiDocumentParseService;
import nexus.io.model.TaskResponse;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.http.server.handler.HttpRequestHandler;

public class DocumentParseTaskHandler implements HttpRequestHandler {

  @Override
  public HttpResponse handle(HttpRequest httpRequest) throws Exception {
    Long id = httpRequest.getLong("id");
    TaskResponse task = Aop.get(AiDocumentParseService.class).getTask(id);
    HttpResponse response = TioRequestContext.getResponse();
    return response.body(RespBodyVo.ok(task));
  }
}
