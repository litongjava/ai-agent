package com.litongjava.llm.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebPageService {

  public static final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";

  /**
   * 根据 URL 获取页面内容，提取 body 部分并转换为 Markdown 格式.
   *
   * @param url 要访问的页面 URL
   * @return 包含 Markdown 格式内容的 ResponseVo 对象
   */
  public ResponseVo get(String url) {
    if (!url.endsWith(".pdf")) {
      String sql = "SELECT text FROM %s WHERE url = ?";
      sql = String.format(sql, AgentTableNames.web_page_cache);
      String result = Db.queryStr(sql, url);
      if (result != null) {
        return ResponseVo.ok(result);
      }
      try {
        Connection connect = Jsoup.connect(url);
        connect.userAgent(userAgent);
        Document document = connect.get();
        Element bodyElement = document.body();
        //String bodyHtml = bodyElement.html(); // 获取body标签内的HTML内容
        result = bodyElement.text(); // 获取body标签内的纯文本内容
        Row newRow = new Row();
        newRow.set("id", SnowflakeIdUtils.id()).set("url", url).set("text", result);
        Db.save(AgentTableNames.web_page_cache, newRow);
        // 转换为 Markdown 格式
        //String markdown = MarkdownUtils.safeToMd(bodyHtml);
        return ResponseVo.ok(result);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        return null;
      }
    } else {
      return null;
    }
  }

  /**
   * 提取 HTML 中的 <body> 内容.
   * 这里为简单示例，实际情况可使用 Jsoup 等库进行更健壮的解析.
   *
   * @param html 完整的 HTML 字符串
   * @return <body> 标签内的内容，如果未找到则返回原始 HTML
   */
  private String extractBody(String html) {
    if (html == null || html.trim().isEmpty()) {
      return "";
    }
    // 简单方式提取 <body> 标签内内容
    int bodyStart = html.indexOf("<body");
    if (bodyStart != -1) {
      // 找到<body>标签起始位置
      int startTagEnd = html.indexOf(">", bodyStart);
      if (startTagEnd != -1) {
        int bodyEnd = html.indexOf("</body>", startTagEnd);
        if (bodyEnd != -1) {
          return html.substring(startTagEnd + 1, bodyEnd);
        }
      }
    }
    // 如果无法提取，返回原始 HTML
    return html;
  }
}
