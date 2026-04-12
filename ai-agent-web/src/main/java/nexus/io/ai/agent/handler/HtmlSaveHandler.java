package nexus.io.ai.agent.handler;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.http.server.handler.HttpRequestHandler;
import nexus.io.tio.http.server.util.CORSUtils;
import nexus.io.tio.utils.crypto.Md5Utils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

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
    Kv kv = Kv.by("url", host + "/html/preview/" + id);
    return response.body(RespBodyVo.ok(kv));
  }
}
