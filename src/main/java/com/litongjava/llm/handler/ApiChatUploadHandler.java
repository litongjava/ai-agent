package com.litongjava.llm.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.tio.boot.admin.services.AwsS3StorageService;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.http.server.model.HttpCors;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.http.server.util.Resps;

public class ApiChatUploadHandler {
  public HttpResponse recommend(HttpRequest request) {
    HttpResponse httpResponse = TioRequestContext.getResponse();
    CORSUtils.enableCORS(httpResponse, new HttpCors());
    UploadFile uploadFile = request.getUploadFile("file");
    String category = request.getParam("category");

    AwsS3StorageService storageService = Aop.get(AwsS3StorageService.class);
    if (uploadFile != null) {
      RespBodyVo respBodyVo = storageService.upload(category, uploadFile);
      return Resps.json(httpResponse, respBodyVo);
    }
    return Resps.json(httpResponse, RespBodyVo.ok("Fail"));
  }
}
