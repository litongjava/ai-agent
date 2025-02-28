package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;
import com.litongjava.db.TableInput;
import com.litongjava.db.TableResult;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.ehcache.EhCacheKit;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.model.page.Page;
import com.litongjava.table.services.ApiTable;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

public class LlmChatSessionService {

  public TableResult<Kv> create(String userId, String name, Long school_id, String type, Integer chat_type, Long appId) {

    long id = SnowflakeIdUtils.id();
    Row record = Row.by("id", id);
    record.setTableName(AgentTableNames.llm_chat_session);
    record.set("name", name);
    record.set("user_id", userId);
    record.set("school_id", school_id);
    record.set("type", type);
    record.set("chat_type", chat_type);
    if (appId != null) {
      record.set("app_id", appId);
    }
    boolean save = Db.save(record);
    if (save) {
      return TableResult.ok(Kv.by("id", id).set("name", name));
    } else {
      return TableResult.fail();
    }
  }

  public boolean exists(Long id, String userId) {
    String cacheName = AgentTableNames.llm_chat_session + "_exists";
    String key = id + "_" + userId;
    Boolean exists = EhCacheKit.getBoolean(cacheName, key);
    if (exists != null && exists) {
      return exists;
    }
    String sql = "select count(1) from %s where id=? and user_id=? and deleted=0";
    sql = String.format(sql, AgentTableNames.llm_chat_session);
    exists = Db.existsBySql(sql, id, userId);
    if (exists) {
      EhCacheKit.put(cacheName, key, exists);
    }
    return exists;
  }

  public List<Kv> page(int pageNo, int pageSize, String userId, Long schoolId, Integer chat_type) {

    TableInput ti = TableInput.create();
    ti.set("user_id", userId);
    ti.set("chat_type", chat_type);
    ti.set("deleted", 0);

    if (schoolId != null) {
      ti.set("school_id", schoolId);
    }
    ti.pageNo(pageNo).pageSize(pageSize);
    ti.orderBy("create_time").asc(false);

    TableResult<Page<Row>> result = ApiTable.page(AgentTableNames.llm_chat_session, ti);
    Page<Row> paginate = result.getData();

    List<Row> list = paginate.getList();
    List<Kv> kvs = new ArrayList<>();
    for (Row record : list) {
      kvs.add(record.toKv());
    }
    return kvs;
  }

  public int updateSessionName(String name, Long id, String userId) {
    String sql = String.format("update %s set name=? where id=? and user_id=?", AgentTableNames.llm_chat_session);
    return Db.updateBySql(sql, name, id, userId);
  }

  public int softDelete(Long id, String userId) {
    String sql = "update %s set deleted=1 where id=? and user_id=?";
    sql = String.format(sql, AgentTableNames.llm_chat_session);
    return Db.updateBySql(sql, id, userId);
  }
}
