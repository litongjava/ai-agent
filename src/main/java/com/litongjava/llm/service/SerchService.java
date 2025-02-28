package com.litongjava.llm.service;

import java.util.List;

import com.litongjava.searxng.SearxngResult;
import com.litongjava.searxng.SearxngSearchClient;
import com.litongjava.searxng.SearxngSearchResponse;

public class SerchService {

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
}
