package nexus.io.llm.service;

import java.util.concurrent.locks.Lock;

import com.google.common.util.concurrent.Striped;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.consts.AgentTableNames;
import nexus.io.model.TaskResponse;
import nexus.io.model.body.RespBodyVo;
import nexus.io.model.http.response.ResponseVo;
import nexus.io.model.upload.UploadFile;
import nexus.io.model.upload.UploadResult;
import nexus.io.openai.whisper.WhisperClient;
import nexus.io.openai.whisper.WhisperResponseFormat;
import nexus.io.tio.boot.admin.services.storage.AliyunStorageService;
import nexus.io.tio.utils.crypto.Md5Utils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

public class AiAudioAsrService {
  private static final Striped<Lock> locks = Striped.lock(1024);

  public RespBodyVo asr(UploadFile uploadFile) {
    byte[] data = uploadFile.getData();
    String md5Hex = Md5Utils.md5Hex(data);
    String sql = "select content from %s where md5=? and deleted=0";
    sql = String.format(sql, AgentTableNames.ai_audio_asr_cache);
    String content = Db.queryStr(sql, md5Hex);
    if (content == null) {
      Lock lock = locks.get(md5Hex);
      lock.lock();
      try {
        content = Db.queryStr(sql, md5Hex);
        if (content == null) {
          content = parse0(uploadFile, md5Hex);
        }
      } finally {
        lock.unlock();
      }

    }

    TaskResponse taskResponse = new TaskResponse(content);
    return RespBodyVo.ok(taskResponse);
  }

  public String parse0(UploadFile uploadFile, String md5Hex) {
    UploadResult uploadFileResult = Aop.get(AliyunStorageService.class).uploadFile("audio", uploadFile);
    Long fileId = uploadFileResult.getId();
    byte[] data = uploadFile.getData();
    String name = uploadFile.getName();

    ResponseVo transcriptions = WhisperClient.transcriptions(name, data, WhisperResponseFormat.text);
    String content = transcriptions.getBodyString();

    long id = SnowflakeIdUtils.id();
    Row row = Row.by("id", id).set("md5", md5Hex).set("file_id", fileId)
        //
        .set("content", content);
    Db.save(AgentTableNames.ai_audio_asr_cache, row);
    return content;
  }
}
