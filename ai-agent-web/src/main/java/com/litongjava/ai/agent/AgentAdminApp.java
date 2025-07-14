package com.litongjava.ai.agent;

import com.litongjava.annotation.AComponentScan;
import com.litongjava.tio.boot.TioApplication;

@AComponentScan
public class AgentAdminApp {
  public static void main(String[] args) {
    long start = System.currentTimeMillis();
    TioApplication.run(AgentAdminApp.class, args);
    long end = System.currentTimeMillis();
    System.out.println((end - start) + "(ms)");
  }
}