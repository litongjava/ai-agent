package nexus.io.llm.func;

import java.util.function.Function;

import nexus.io.jfinal.aop.Aop;
import nexus.io.model.http.response.ResponseVo;
import nexus.io.model.upload.UploadFile;
import nexus.io.model.upload.UploadResult;
import nexus.io.tio.boot.admin.services.storage.AliyunStorageService;
import nexus.io.tio.utils.http.HttpUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

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
      UploadResult resultVo = aliyunStorageService.uploadFile("document/images", uploadFile);
      url = resultVo.getUrl();
    }
    return url;
  }

}
