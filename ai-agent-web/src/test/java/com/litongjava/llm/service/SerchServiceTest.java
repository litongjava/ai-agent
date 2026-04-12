package com.litongjava.llm.service;

import org.junit.Test;

import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.service.SearchService;

public class SerchServiceTest {

  @Test
  public void test() {
    String search = Aop.get(SearchService.class).search("KaiZhao SJSU");
    System.out.println(search);
  }

}
