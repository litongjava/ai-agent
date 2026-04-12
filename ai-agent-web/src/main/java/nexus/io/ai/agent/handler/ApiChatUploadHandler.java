package nexus.io.ai.agent.handler;

import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.service.ChatUploadService;
import nexus.io.model.body.RespBodyVo;
import nexus.io.model.upload.UploadFile;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.http.server.model.HttpCors;
import nexus.io.tio.http.server.util.CORSUtils;
import nexus.io.tio.http.server.util.Resps;

public class ApiChatUploadHandler {
  ChatUploadService chatUploadService = Aop.get(ChatUploadService.class);

  public HttpResponse upload(HttpRequest request) {
    HttpResponse httpResponse = TioRequestContext.getResponse();
    CORSUtils.enableCORS(httpResponse, new HttpCors());
    UploadFile uploadFile = request.getUploadFile("file");
    String category = request.getParam("category");

    if (uploadFile != null) {
      RespBodyVo respBodyVo = chatUploadService.upload(category, uploadFile);
      return Resps.json(httpResponse, respBodyVo);
    }
    return Resps.json(httpResponse, RespBodyVo.ok("Fail"));
  }

  public HttpResponse file(HttpRequest request) {
    HttpResponse httpResponse = TioRequestContext.getResponse();
    CORSUtils.enableCORS(httpResponse, new HttpCors());
    String md5 = request.getParam("md5");

    RespBodyVo vo = chatUploadService.file(md5);
    return Resps.json(httpResponse, vo);
  }
}
