package com.litongjava.llm.func;

import java.util.function.Function;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.tio.boot.admin.services.storage.AliyunStorageService;
import com.litongjava.tio.boot.admin.vo.UploadResultVo;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.utils.http.HttpUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

public class MarkdownImageMoveFunc implements Function<String, String> {

  @Override
  public String apply(String url) {
    ResponseVo responseVo = HttpUtils.download(url);
    if (responseVo.isOk()) {
      byte[] bodyBytes = responseVo.getBodyBytes();
      long id = SnowflakeIdUtils.id();
      String suffix = "jpg";
      UploadFile uploadFile = new UploadFile(id + "." + suffix, bodyBytes);
      AliyunStorageService aliyunStorageService = Aop.get(AliyunStorageService.class);
      UploadResultVo resultVo = aliyunStorageService.uploadFile("document/images", uploadFile);
      url = resultVo.getUrl();
    }
    return url;
  }

}
