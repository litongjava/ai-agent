package com.litongjava.ai.agent.config;

import java.util.ArrayList;
import java.util.List;

import com.litongjava.ai.agent.handler.HtmlPreviewController;
import com.litongjava.tio.boot.http.handler.controller.TioBootHttpControllerRouter;
import com.litongjava.tio.boot.server.TioBootServer;

public class AgentControllerConfiguration {

  public void config() {
    TioBootHttpControllerRouter controllerRouter = TioBootServer.me().getControllerRouter();
    if (controllerRouter == null) {
      return;
    }
    List<Class<?>> scannedClasses = new ArrayList<>();
    scannedClasses.add(HtmlPreviewController.class);
    controllerRouter.addControllers(scannedClasses);
  }
}
