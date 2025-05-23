package com.litongjava.llm.handler;

import com.litongjava.searxng.SearxngSearchClient;
import com.litongjava.searxng.SearxngSearchParam;
import com.litongjava.searxng.SearxngSearchResponse;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.utils.environment.EnvUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SearxngSearchHandler {

  public HttpResponse search(HttpRequest request) {
    log.info("request line:{}", request.requestLine.toString());
    // 从请求中获取各参数
    String format = "json";
    String q = request.getString("q");
    String language = request.getString("language");
    String categories = request.getString("categories");
    String engines = request.getString("engines");
    Integer pageno = request.getInt("pageno");
    String time_range = request.getString("time_range");
    Integer safesearch = request.getInt("safesearch");
    String autocomplete = request.getString("autocomplete");
    String locale = request.getString("locale");
    Boolean no_cache = request.getBoolean("no_cache");
    String theme = request.getString("theme");

    // 创建并设置 SearxngSearchParam 对象的属性
    SearxngSearchParam param = new SearxngSearchParam();
    param.setFormat(format);
    param.setQ(q);
    param.setLanguage(language);
    param.setCategories(categories);
    param.setEngines(engines);
    param.setPageno(pageno);
    param.setTime_range(time_range);
    param.setSafesearch(safesearch);
    param.setAutocomplete(autocomplete);
    param.setLocale(locale);
    param.setNo_cache(no_cache);
    param.setTheme(theme);

    String baseUrl = EnvUtils.getStr("SEARXNG_API_BASE");
    String endpoint = baseUrl + "/search";

    // 使用封装后的参数调用服务
    SearxngSearchResponse search = SearxngSearchClient.search(endpoint, param);
    return TioRequestContext.getResponse().setJson(search);
  }
}
