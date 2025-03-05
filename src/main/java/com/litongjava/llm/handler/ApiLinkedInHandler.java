package com.litongjava.llm.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.service.LinkedInService;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;

public class ApiLinkedInHandler {
  public HttpResponse profile_scraper(HttpRequest request) {
    String url = request.getString("url");
    HttpResponse response = TioRequestContext.getResponse();
    LinkedInService linkedInService = Aop.get(LinkedInService.class);
    String profile = linkedInService.profileScraper(url);
    if(profile!=null) {
      response.setString(profile);
    }
    return response;
  }


}
