package com.litongjava.llm.service;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.litongjava.openai.chat.ChatMesageContent;
import com.litongjava.openai.chat.ChatMessage;
import com.litongjava.openai.chat.ChatRequestImage;
import com.litongjava.openai.chat.OpenAiChatMessage;

public class LlmChatMessageService {

  public void parse(JSONArray reqMessages) {
    List<OpenAiChatMessage> openAiChatmessages = new ArrayList<>();
    List<ChatMessage> messages = new ArrayList<>();
    boolean hasImage = false;
    String textQuestion = null;

    for (int i = 0; i < reqMessages.size(); i++) {
      JSONObject message = reqMessages.getJSONObject(i);
      String role = message.getString("role");
      Object content = message.get("content");

      if ("system".equals(role)) {
        textQuestion = content.toString();
        messages.add(new ChatMessage().role(role).content(content.toString()));
        OpenAiChatMessage openAiChatMessage = new OpenAiChatMessage(role, content.toString());
        openAiChatmessages.add(openAiChatMessage);

      } else if ("user".equals(role)) {
        if (content instanceof String) {
          textQuestion = content.toString();
          // 文本消息单独返回,不添加到最终的消息体中
          // messages.add(new ChatMessage().role(role).content(content.toString()));

        } else if (content instanceof JSONArray) {
          JSONArray contentsArray = (JSONArray) content;
          for (int j = 0; j < contentsArray.size(); j++) {
            JSONObject contentObj = contentsArray.getJSONObject(j);
            String type = contentObj.getString("type");
            if ("image_url".equals(type)) {
              hasImage = true;
              JSONObject imageUrl = contentObj.getJSONObject("image_url");
              String url = imageUrl.getString("url");

              if (url.startsWith("data:image/")) {
                ChatRequestImage image = new ChatRequestImage();
                image.setUrl(url);
                image.setDetail(imageUrl.getString("detail"));

                ChatMesageContent multiContent = new ChatMesageContent();
                multiContent.setType("image_url");
                multiContent.setImage_url(image);

                List<ChatMesageContent> multiContents = new ArrayList<>();
                multiContents.add(multiContent);

                OpenAiChatMessage multiContents2 = new OpenAiChatMessage().role(role).multiContents(multiContents);
                openAiChatmessages.add(multiContents2);
              } else {
                throw new RuntimeException("image is not encoded with base64");
              }
            } else if ("text".equals(type)) {
              messages.add(new ChatMessage().role(role).content(contentObj.getString("text")));
            }
          }
        }
      }
    }
  }
}
