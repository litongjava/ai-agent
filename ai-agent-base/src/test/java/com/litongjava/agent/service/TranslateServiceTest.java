package com.litongjava.agent.service;

import org.junit.Test;

import com.litongjava.jfinal.aop.Aop;

public class TranslateServiceTest {

  @Test
  public void test() {
    String augmenteQuesiton = Aop.get(TranslateService.class).augmenteQuesiton("层级转账需要7天,但是这次仅仅用了一天");
    System.out.println(augmenteQuesiton);
  }

}
