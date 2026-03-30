package com.litongjava.llm.service;

import org.junit.Test;

import com.litongjava.jfinal.aop.Aop;

public class SerchServiceTest {

  @Test
  public void test() {
    String search = Aop.get(SearchService.class).search("KaiZhao SJSU");
    System.out.println(search);
  }

}
