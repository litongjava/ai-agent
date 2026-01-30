package com.litongjava.llm.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.service.AiAudioAsrService;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.model.upload.UploadFile;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;

public class ApiAsrHandler implements HttpRequestHandler {

  @Override
  public HttpResponse handle(HttpRequest httpRequest) throws Exception {
    UploadFile uploadFile = httpRequest.getUploadFile("file");
    RespBodyVo vo = Aop.get(AiAudioAsrService.class).asr(uploadFile);
    HttpResponse response = TioRequestContext.getResponse();
    return response.body(vo);
  }

}
