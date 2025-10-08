package com.litongjava.llm.handler;

import com.jfinal.kit.Kv;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.crypto.Md5Utils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

public class HtmlSaveHandler implements HttpRequestHandler {

  @Override
  public HttpResponse handle(HttpRequest httpRequest) throws Exception {
    String host = httpRequest.getHost();
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);
    String bodyString = httpRequest.getBodyString();
    if (bodyString == null) {
      RespBodyVo fail = RespBodyVo.fail();
      return response.body(fail);
    }
    String md5 = Md5Utils.md5Hex(bodyString);
    String sql = "select id from llm_html_preview where md5=?";
    Long id = Db.queryLong(sql, md5);
    if (id == null) {
      id = SnowflakeIdUtils.id();
      Row row = Row.by("id", id).set("md5", md5).set("html", bodyString);
      Db.save("llm_html_preview", row);
    }
    Kv kv = Kv.by("url", "//" + host + "/html/preview/" + id);
    return response.body(RespBodyVo.ok(kv));
  }
}
