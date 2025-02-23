package com.litongjava.llm.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.jfinal.kit.Kv;
import com.jfinal.kit.StrKit;
import com.litongjava.db.TableInput;
import com.litongjava.db.TableResult;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.groq.GropConst;
import com.litongjava.groq.GropModel;
import com.litongjava.groq.GroqSpeechClient;
import com.litongjava.groq.TranscriptionsRequest;
import com.litongjava.groq.TranscriptionsResponse;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.llm.utils.DocxUtils;
import com.litongjava.llm.utils.PdfUtils;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.table.services.ApiTable;
import com.litongjava.tio.boot.admin.costants.TioBootAdminTableNames;
import com.litongjava.tio.boot.admin.dao.SystemUploadFileDao;
import com.litongjava.tio.boot.admin.services.StorageService;
import com.litongjava.tio.boot.admin.services.SystemUploadFileService;
import com.litongjava.tio.boot.admin.utils.AwsS3Utils;
import com.litongjava.tio.boot.admin.vo.UploadResultVo;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.utils.crypto.Md5Utils;
import com.litongjava.tio.utils.hutool.FilenameUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Slf4j
public class ChatUploadService implements StorageService {
  public RespBodyVo upload(String category, UploadFile uploadFile) {
    if (StrKit.isBlank(category)) {
      category = "default";
    }
    UploadResultVo uploadResultVo = uploadBytes(category, uploadFile);
    Long id = uploadResultVo.getId();
    if (!Db.exists(AgentTableNames.chat_upload_file, "id", id)) {
      try {
        String content = parseContent(uploadFile);
        if (content == null) {
          return RespBodyVo.fail("un support file type");
        } else {
          Row row = Row.by("id", id).set("name", uploadFile.getName()).set("content", content).set("md5", uploadResultVo.getMd5());
          Db.save(AgentTableNames.chat_upload_file, row);
        }
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        return RespBodyVo.fail(e.getMessage());
      }

    }
    return RespBodyVo.ok(uploadResultVo);
  }

  private String parseContent(UploadFile uploadFile) throws IOException {
    String name = uploadFile.getName();
    byte[] data = uploadFile.getData();
    String suffix = FilenameUtils.getSuffix(name).toLowerCase();
    String text = null;
    if ("txt".equals(suffix) || "md".equals(suffix)) {
      text = new String(uploadFile.getData(), StandardCharsets.UTF_8);
    } else if (GropConst.SUPPORT_LIST.contains(suffix)) {
      TranscriptionsRequest transcriptionsRequest = new TranscriptionsRequest();
      transcriptionsRequest.setModel(GropModel.WHISPER_LARGE_V3_TURBO);
      TranscriptionsResponse transcriptions = GroqSpeechClient.transcriptions(data, name, transcriptionsRequest);
      text = transcriptions.getText();

    } else if ("pdf".equals(suffix)) {
      text = PdfUtils.parseContent(data);
    } else if ("docx".equals(suffix)) {
      text = DocxUtils.parseDocx(data);

    } else if ("jpg".contentEquals(text) || "jpeg".contentEquals(text) || "png".contentEquals(text)) {
      text = Aop.get(LlmOcrService.class).parse(data, name);
    }
    return text;
  }

  public UploadResultVo uploadBytes(String category, UploadFile uploadFile) {
    // 上传文件
    long id = SnowflakeIdUtils.id();
    String suffix = FilenameUtils.getSuffix(uploadFile.getName());
    String newFilename = id + "." + suffix;

    String targetName = category + "/" + newFilename;

    return uploadBytes(id, targetName, uploadFile, suffix);
  }

  /**
   * @param id
   * @param originFilename
   * @param targetName
   * @param fileContent
   * @param size
   * @param suffix
   * @return
   */
  public UploadResultVo uploadBytes(long id, String targetName, UploadFile uploadFile, String suffix) {
    String originFilename = uploadFile.getName();
    long size = uploadFile.getSize();

    byte[] fileContent = uploadFile.getData();
    String md5 = Md5Utils.digestHex(fileContent);
    Row record = Aop.get(SystemUploadFileDao.class).getFileBasicInfoByMd5(md5);
    if (record != null) {
      log.info("select table reuslt:{}", record.toMap());
      id = record.getLong("id");
      String url = this.getUrl(record.getStr("bucket_name"), record.getStr("target_name"));
      Kv kv = record.toKv();
      kv.remove("target_name");
      kv.remove("bucket_name");
      kv.set("url", url);
      kv.set("md5", md5);
      return new UploadResultVo(id, uploadFile.getName(), uploadFile.getSize(), url, md5);
    } else {
      log.info("not found from cache table:{}", md5);
    }

    String etag = null;
    try (S3Client client = AwsS3Utils.buildClient();) {
      PutObjectResponse response = AwsS3Utils.upload(client, AwsS3Utils.bucketName, targetName, fileContent, suffix);
      etag = response.eTag();
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }

    // Log and save to database
    log.info("Uploaded with ETag: {}", etag);

    TableInput kv = TableInput.create().set("name", originFilename).set("size", size).set("md5", md5)
        //
        .set("platform", "aws s3").set("region_name", AwsS3Utils.regionName).set("bucket_name", AwsS3Utils.bucketName)
        //
        .set("target_name", targetName).set("file_id", etag);

    TableResult<Kv> save = ApiTable.save(TioBootAdminTableNames.tio_boot_admin_system_upload_file, kv);
    String downloadUrl = getUrl(AwsS3Utils.bucketName, targetName);

    return new UploadResultVo(save.getData().getLong("id"), originFilename, Long.valueOf(size), downloadUrl, md5);

  }

  @Override
  public String getUrl(String bucketName, String targetName) {
    return Aop.get(SystemUploadFileService.class).getUrl(bucketName, targetName);
  }

  @Override
  public UploadResultVo getUrlById(String id) {
    return Aop.get(SystemUploadFileService.class).getUrlById(id);
  }

  @Override
  public UploadResultVo getUrlById(long id) {
    return Aop.get(SystemUploadFileService.class).getUrlById(id);
  }

  @Override
  public UploadResultVo getUrlByMd5(String md5) {
    return Aop.get(SystemUploadFileService.class).getUrlByMd5(md5);
  }

  public RespBodyVo file(String md5) {
    boolean exists = Db.exists(AgentTableNames.chat_upload_file, "md5", md5);
    UploadResultVo uploadResultVo = Aop.get(SystemUploadFileService.class).getUrlByMd5(md5);

    if (exists && uploadResultVo != null) {
      return RespBodyVo.ok(uploadResultVo);
    }
    return RespBodyVo.fail();
  }
}
