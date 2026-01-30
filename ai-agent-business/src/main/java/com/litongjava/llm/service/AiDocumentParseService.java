package com.litongjava.llm.service;

import java.util.concurrent.locks.Lock;

import com.google.common.util.concurrent.Striped;
import com.litongjava.db.DbJsonObject;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.gitee.GiteeClient;
import com.litongjava.gitee.GiteeDocumentOutput;
import com.litongjava.gitee.GiteeSimpleMarkdownUtils;
import com.litongjava.gitee.GiteeTaskResponse;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.llm.func.MarkdownImageMoveFunc;
import com.litongjava.model.TaskResponse;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.model.upload.UploadFile;
import com.litongjava.model.upload.UploadResult;
import com.litongjava.tio.boot.admin.services.storage.AliyunStorageService;
import com.litongjava.tio.utils.crypto.Md5Utils;
import com.litongjava.tio.utils.json.FastJson2Utils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

public class AiDocumentParseService {
  private static final Striped<Lock> locks = Striped.lock(1024);

  private GiteeClient giteeClient = Aop.get(GiteeClient.class);

  public RespBodyVo parse(UploadFile uploadFile) {
    byte[] data = uploadFile.getData();
    String md5Hex = Md5Utils.md5Hex(data);
    String sql = "select id from %s where md5=? and deleted=0";
    sql = String.format(sql, AgentTableNames.llm_document_parsed_file_cache);
    Long output = Db.queryLong(sql, md5Hex);
    Long taskId = null;
    if (output != null) {
      taskId = output;
    } else {
      Lock lock = locks.get(md5Hex);
      lock.lock();
      try {
        output = Db.queryLong(sql, md5Hex);
        if (output != null) {
          taskId = output;
        } else {
          taskId = parse0(uploadFile, md5Hex);
        }
      } finally {
        lock.unlock();
      }

    }

    return RespBodyVo.ok(new TaskResponse(taskId));
  }

  public Long parse0(UploadFile uploadFile, String md5Hex) {
    UploadResult uploadFileResult = Aop.get(AliyunStorageService.class).uploadFile("document", uploadFile);
    Long fileId = uploadFileResult.getId();
    byte[] data = uploadFile.getData();
    String name = uploadFile.getName();

    GiteeTaskResponse taskResponse = giteeClient.parseDocument(data, name);
    String task_id = taskResponse.getTask_id();
    long id = SnowflakeIdUtils.id();
    Row row = Row.by("id", id).set("md5", md5Hex).set("file_id", fileId)
        //
        .set("model", "deepseek-ocr").set("task_id", task_id);
    Db.save(AgentTableNames.llm_document_parsed_file_cache, row);
    return id;
  }

  public TaskResponse getTask(Long id) {
    String sql = "select content from %s where id=? and deleted=0";
    sql = String.format(sql, AgentTableNames.llm_document_parsed_file_cache);
    String markdown = Db.queryStr(sql, id);
    if (markdown == null) {
      Lock lock = locks.get(id);
      lock.lock();
      try {
        markdown = Db.queryStr(sql, id);
        if (markdown == null) {
          markdown = getTask0(id);
        }
      } finally {
        lock.unlock();
      }
    }

    return new TaskResponse(markdown);
  }

  private String getTask0(Long id) {
    String sql = "select task_id from %s where id=?";
    sql = String.format(sql, AgentTableNames.llm_document_parsed_file_cache);
    String taskId = Db.queryStr(sql, id);

    GiteeTaskResponse task = giteeClient.getTask(taskId);
    if (task.getOutput() != null) {
      GiteeDocumentOutput output = task.getOutput();
      DbJsonObject dbJsonObject = new DbJsonObject(FastJson2Utils.toJson(output));
      MarkdownImageMoveFunc markdownImageMoveFunc = new MarkdownImageMoveFunc();
      String markdown = GiteeSimpleMarkdownUtils.toMarkdown(output, markdownImageMoveFunc);

      Row row = Row.by("id", id).set("content", markdown).set("output", dbJsonObject);
      Db.update(AgentTableNames.llm_document_parsed_file_cache, row);
      return markdown;
    }
    return null;
  }
}
