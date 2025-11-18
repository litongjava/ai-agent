package com.litongjava.llm.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;

public class DocuementParseHandler implements HttpRequestHandler {

  @Override
  public HttpResponse handle(HttpRequest httpRequest) throws Exception {
    UploadFile uploadFile = httpRequest.getUploadFile("file");
    RespBodyVo vo = Aop.get(DocuementParseService.class).parse(uploadFile);
    HttpResponse response = TioRequestContext.getResponse();
    return response.body(vo);
  }
}
