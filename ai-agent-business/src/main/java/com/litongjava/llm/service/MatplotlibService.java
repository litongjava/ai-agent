package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.linux.ExecuteCodeRequest;
import com.litongjava.linux.JavaKitClient;
import com.litongjava.llm.consts.AiChatEventName;
import com.litongjava.model.upload.UploadFile;
import com.litongjava.model.upload.UploadResult;
import com.litongjava.tio.boot.admin.services.storage.AwsS3StorageService;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.sse.SsePacket;
import com.litongjava.tio.utils.base64.Base64Utils;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.encoder.ImageVo;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.json.JsonUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;
import com.litongjava.utils.CodeBlockUtils;

import lombok.extern.slf4j.Slf4j;

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
