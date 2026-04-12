package nexus.io.ai.agent.handler;

import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.service.LinkedInService;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

public class ApiLinkedInHandler {
  public HttpResponse profile_scraper(HttpRequest request) {
    String url = request.getString("url");
    HttpResponse response = TioRequestContext.getResponse();
    LinkedInService linkedInService = Aop.get(LinkedInService.class);
    String profile = linkedInService.profileScraper(url);
    if(profile!=null) {
      response.body(profile);
    }
    return response;
  }


}
