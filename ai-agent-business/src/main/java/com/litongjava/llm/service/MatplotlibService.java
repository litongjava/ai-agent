package com.litongjava.llm.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.linux.ExecuteCodeRequest;
import com.litongjava.linux.JavaKitClient;
import com.litongjava.llm.vo.ToolVo;
import com.litongjava.tio.boot.admin.services.storage.AwsS3StorageService;
import com.litongjava.tio.boot.admin.vo.UploadResultVo;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.utils.base64.Base64Utils;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.encoder.ImageVo;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.json.JsonUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MatplotlibService {

  private PythonCodeService pythonCodeService = Aop.get(PythonCodeService.class);

  public ProcessResult generateMatplot(String quesiton, String answer) {
    String text = pythonCodeService.generateMatplotlibCode(quesiton, answer);
    if (StrUtil.isBlank(text)) {
      return null;
    }
    ToolVo toolVo = null;
    try {
      toolVo = JsonUtils.parse(text, ToolVo.class);
    } catch (Exception e) {
      log.error("text:{}", text, e.getMessage(), e);
      StringWriter stringWriter = new StringWriter();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      e.printStackTrace(printWriter);
      String stackTrace = stringWriter.toString();
      String msg = "code:" + text + ",stackTrace" + stackTrace;
      Aop.get(AgentNotificationService.class).sendError(msg);
      return null;
    }
    if (toolVo != null && "execute_python".equals(toolVo.getTool())) {
      String code = toolVo.getCode();
      ExecuteCodeRequest codeRequest = new ExecuteCodeRequest(code);
      long id = SnowflakeIdUtils.id();
      codeRequest.setId(id);
      log.info("run code {}", id);
      ProcessResult result = JavaKitClient.executePythonCode(codeRequest);
      if (result != null) {
        String stdErr = result.getStdErr();
        if (StrUtil.isNotBlank(stdErr)) {
          String prompt = "python代码执行过程中出现了错误,请修正错误并仅输出修改后的代码,错误信息:%s";
          prompt = String.format(prompt, stdErr);
          code = pythonCodeService.fixCodeError(prompt, code);
          codeRequest = new ExecuteCodeRequest(code);
          id = SnowflakeIdUtils.id();
          codeRequest.setId(id);
          log.info("run code {}", id);
          result = JavaKitClient.executePythonCode(codeRequest);
        }

        List<String> images = result.getImages();
        if (images != null) {
          List<String> imageUrls = new ArrayList<>(images.size());
          for (String imageBase64Code : images) {
            ImageVo decodeImage = Base64Utils.decodeImage(imageBase64Code);
            UploadFile uploadFile = new UploadFile("matplotlib." + decodeImage.getExtension(), decodeImage.getData());
            UploadResultVo resultVo = Aop.get(AwsS3StorageService.class).uploadFile("matplotlib", uploadFile);
            String url = resultVo.getUrl();
            imageUrls.add(url);
          }
          result.setImages(imageUrls);
        }
        return result;
      }

    }
    return null;
  }
}
