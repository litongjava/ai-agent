package nexus.io.ai.agent.handler;

import nexus.io.annotation.RequestPath;
import nexus.io.db.activerecord.Db;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.http.server.util.Resps;

@RequestPath("/html/preview")
public class HtmlPreviewController {

  @RequestPath("/{id}")
  public HttpResponse preview(Long id) {
    HttpResponse response = TioRequestContext.getResponse();
    String sql = "select html from llm_html_preview where id=?";
    String html = Db.queryStr(sql, id);
    return Resps.html(response, html);
  }
}
