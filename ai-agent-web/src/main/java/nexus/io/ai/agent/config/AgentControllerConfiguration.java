package nexus.io.ai.agent.config;

import java.util.ArrayList;
import java.util.List;

import nexus.io.ai.agent.handler.HtmlPreviewController;
import nexus.io.tio.boot.http.handler.controller.TioBootHttpControllerRouter;
import nexus.io.tio.boot.server.TioBootServer;

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
