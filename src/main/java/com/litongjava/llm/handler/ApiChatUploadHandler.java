package com.litongjava.llm.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.service.ChatUploadService;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.tio.boot.admin.vo.UploadResultVo;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.http.server.model.HttpCors;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.http.server.util.Resps;

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
