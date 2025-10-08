package com.litongjava.llm.handler;

import com.litongjava.annotation.RequestPath;
import com.litongjava.db.activerecord.Db;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.util.Resps;

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
