package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.litongjava.google.search.GoogleCustomSearchClient;
import com.litongjava.google.search.GoogleCustomSearchResponse;
import com.litongjava.google.search.SearchResultItem;
import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.searchapi.SearchapiClient;
import com.litongjava.searchapi.SearchapiOrganicResult;
import com.litongjava.searchapi.SearchapiRelatedQuestion;
import com.litongjava.searchapi.SearchapiResult;
import com.litongjava.searxng.SearxngResult;
import com.litongjava.searxng.SearxngSearchClient;
import com.litongjava.searxng.SearxngSearchResponse;
import com.litongjava.tio.utils.json.FastJson2Utils;

public class SearchService {

  public String search(String q) {
    SearxngSearchResponse searchResponse = SearxngSearchClient.search(q);
    List<SearxngResult> results = searchResponse.getResults();
    StringBuffer markdown = new StringBuffer();
    for (int i = 0; i < results.size(); i++) {
      SearxngResult searxngResult = results.get(i);
      markdown.append("source " + (i + 1) + " " + searxngResult.getContent());
    }
    return markdown.toString();
  }

  public SearxngSearchResponse google(String q) {
    // 调用 Google 搜索接口获取结果
    GoogleCustomSearchResponse googleResponse = GoogleCustomSearchClient.search(q);

    // 构建 SearxngSearchResponse 对象
    SearxngSearchResponse searxngResponse = new SearxngSearchResponse();
    searxngResponse.setQuery(q);

    List<SearxngResult> searxngResults = new ArrayList<>();
    if (googleResponse != null && googleResponse.getItems() != null) {
      for (SearchResultItem item : googleResponse.getItems()) {
        SearxngResult result = new SearxngResult();
        // 将 Google 的结果字段映射到 SearxngResult 对象
        result.setUrl(item.getLink());
        result.setTitle(item.getTitle());
        result.setContent(item.getSnippet());
        // 设置其他字段，若无对应值，可设为默认值或 null
        result.setEngine("GoogleCustomSearch");
        result.setScore(0.0);
        result.setThumbnail(null);
        result.setParsed_url(null);
        result.setTemplate(null);
        result.setEngines(null);
        result.setPositions(null);
        result.setPublishedDate(null);
        result.setCategory(null);
        searxngResults.add(result);
      }
      searxngResponse.setNumber_of_results(googleResponse.getItems().size());
    } else {
      searxngResponse.setNumber_of_results(0);
    }

    searxngResponse.setResults(searxngResults);
    // 其他属性可按需要设置为空列表
    searxngResponse.setAnswers(new ArrayList<>());
    searxngResponse.setCorrections(new ArrayList<>());
    searxngResponse.setInfoboxes(new ArrayList<>());
    searxngResponse.setSuggestions(new ArrayList<>());
    searxngResponse.setUnresponsive_engines(new ArrayList<>());

    return searxngResponse;
  }

  public SearxngSearchResponse searchapi(String q) {
    ResponseVo responseVo = SearchapiClient.search(q);
    String bodyString = responseVo.getBodyString();
    SearchapiResult result = FastJson2Utils.parse(bodyString, SearchapiResult.class);
    // 构建 SearxngSearchResponse 对象
    SearxngSearchResponse searxngResponse = new SearxngSearchResponse();
    // 设置查询条件，优先使用 search_parameters 中的查询
    if (result.getSearch_parameters() != null) {
      searxngResponse.setQuery(result.getSearch_parameters().getQ());
    } else {
      searxngResponse.setQuery(q);
    }

    // 设置结果总数
    if (result.getSearch_information() != null) {
      searxngResponse.setNumber_of_results(result.getSearch_information().getTotal_results());
    } else {
      searxngResponse.setNumber_of_results(0);
    }

    // 转换 organic_results 为 SearxngResult 列表
    List<SearxngResult> searxngResults = new ArrayList<>();
    if (result.getOrganic_results() != null) {
      for (SearchapiOrganicResult organicResult : result.getOrganic_results()) {
        SearxngResult sr = new SearxngResult();
        sr.setUrl(organicResult.getLink());
        sr.setTitle(organicResult.getTitle());
        sr.setContent(organicResult.getSnippet());
        // 优先使用 thumbnail 字段，没有则可考虑 favicon
        sr.setThumbnail(organicResult.getThumbnail());
        // 使用 SearchapiParameters 中的 engine 标识
        if (result.getSearch_parameters() != null) {
          sr.setEngine(result.getSearch_parameters().getEngine());
        } else {
          sr.setEngine("Searchapi");
        }
        // 如需处理 URL 解析，可自行实现逻辑
        sr.setParsed_url(null);
        sr.setTemplate(null);
        sr.setEngines(null);
        // 将 organicResult 的 position 转为 List<Integer>
        sr.setPositions(Collections.singletonList(organicResult.getPosition()));
        sr.setPublishedDate(organicResult.getDate());
        sr.setScore(0.0);
        // 可将 source 字段作为 category
        sr.setCategory(organicResult.getSource());
        searxngResults.add(sr);
      }
    }
    searxngResponse.setResults(searxngResults);

    // 将 related_questions 中的问题作为 suggestions
    List<String> suggestions = new ArrayList<>();
    if (result.getRelated_questions() != null) {
      for (SearchapiRelatedQuestion relatedQuestion : result.getRelated_questions()) {
        suggestions.add(relatedQuestion.getQuestion());
      }
    }
    searxngResponse.setSuggestions(suggestions);

    // 其他字段置空或空集合
    searxngResponse.setAnswers(new ArrayList<>());
    searxngResponse.setCorrections(new ArrayList<>());
    searxngResponse.setInfoboxes(new ArrayList<>());
    searxngResponse.setUnresponsive_engines(new ArrayList<>());

    return searxngResponse;
  }
}
