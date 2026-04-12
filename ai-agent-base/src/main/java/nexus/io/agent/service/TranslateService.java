package nexus.io.agent.service;

import com.jfinal.kit.Kv;

import nexus.io.agent.utils.ChineseDetector;
import nexus.io.jfinal.aop.Aop;

public class TranslateService {
  private PromptService promptService = Aop.get(PromptService.class);

  public String augmenteQuesiton(String inputQestion) {
    String fileName = "translator_prompt.txt";
    boolean chinese = ChineseDetector.isChinese(inputQestion);
    Kv kv = Kv.by("data", inputQestion);
    if (chinese) {
      kv.set("targetLanguage", "English");
    } else {
      kv.set("targetLanguage", "Chinese");
    }

    String augmentedQuestion = promptService.render(fileName, kv);
    return augmentedQuestion;
  }
}
