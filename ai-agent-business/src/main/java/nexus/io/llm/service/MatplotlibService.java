package nexus.io.llm.service;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import nexus.io.http.common.sse.SsePacket;
import nexus.io.jfinal.aop.Aop;
import nexus.io.linux.ExecuteCodeRequest;
import nexus.io.linux.JavaKitClient;
import nexus.io.llm.consts.AiChatEventName;
import nexus.io.model.upload.UploadFile;
import nexus.io.model.upload.UploadResult;
import nexus.io.tio.boot.admin.services.storage.AwsS3StorageService;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.Tio;
import nexus.io.tio.utils.base64.Base64Utils;
import nexus.io.tio.utils.commandline.ProcessResult;
import nexus.io.tio.utils.encoder.ImageVo;
import nexus.io.tio.utils.hutool.StrUtil;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;
import nexus.io.utils.CodeBlockUtils;

@Slf4j
public class MatplotlibService {

  private PythonCodeService pythonCodeService = Aop.get(PythonCodeService.class);

  public ProcessResult generateMatplot(ChannelContext channelContext, String quesiton, String answer) {
    String text = pythonCodeService.generateMatplotlibCode(quesiton, answer);
    if (StrUtil.isBlank(text)) {
      return null;
    }
    if (text.equals("not_needed")) {
      return null;
    }

    String code = CodeBlockUtils.parsePythonCode(text);
    if (StrUtil.isBlank(code)) {
      if (channelContext != null) {
        Kv by = Kv.by("content", text).set("model", "qwen3");
        SsePacket ssePacket = new SsePacket(AiChatEventName.delta, JsonUtils.toJson(by));
        Tio.send(channelContext, ssePacket);
      }
      return null;
    }
    if ("not_needed".equals(code)) {
      return null;
    }
    ProcessResult result = null;
    for (int i = 0; i < 10; i++) {
      long id = SnowflakeIdUtils.id();
      ExecuteCodeRequest codeRequest = new ExecuteCodeRequest(code);
      codeRequest.setId(id);
      log.info("run code {}", id);
      try {
        result = JavaKitClient.executePythonCode(codeRequest);
        if (result == null) {
          continue;
        }
        if (result != null) {
          List<String> images = result.getImages();
          String stdErr = result.getStdErr();
          if (images.size() < 1) {
            if (channelContext != null) {
              Kv by = Kv.by("content", stdErr).set("model", "qwen3");
              SsePacket ssePacket = new SsePacket(AiChatEventName.code_error, JsonUtils.toJson(by));
              Tio.send(channelContext, ssePacket);
            }

            code = pythonCodeService.fixCodeError(stdErr, code);
            continue;
          }
        }
        break;
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    List<String> images = result.getImages();
    if (images != null) {
      List<String> imageUrls = new ArrayList<>(images.size());
      for (String imageBase64Code : images) {
        ImageVo decodeImage = Base64Utils.decodeImage(imageBase64Code);
        UploadFile uploadFile = new UploadFile("matplotlib." + decodeImage.getExtension(), decodeImage.getData());
        UploadResult resultVo = Aop.get(AwsS3StorageService.class).uploadFile("matplotlib", uploadFile);
        String url = resultVo.getUrl();
        imageUrls.add(url);
      }
      result.setImages(imageUrls);
    }
    return result;
  }
}
