package nexus.io.ai.agent.handler;

import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.service.AiDocumentParseService;
import nexus.io.model.body.RespBodyVo;
import nexus.io.model.upload.UploadFile;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.http.server.handler.HttpRequestHandler;

public class DocumentParseHandler implements HttpRequestHandler {

  @Override
  public HttpResponse handle(HttpRequest httpRequest) throws Exception {
    UploadFile uploadFile = httpRequest.getUploadFile("file");
    RespBodyVo vo = Aop.get(AiDocumentParseService.class).parse(uploadFile);
    HttpResponse response = TioRequestContext.getResponse();
    return response.body(vo);
  }
}
