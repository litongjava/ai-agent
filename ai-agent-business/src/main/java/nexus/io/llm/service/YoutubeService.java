package nexus.io.llm.service;

import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.chat.ChatMessageArgs;
import nexus.io.chat.UniChatMessage;
import nexus.io.http.common.sse.SsePacket;
import nexus.io.jfinal.aop.Aop;
import nexus.io.llm.consts.AiChatEventName;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.Tio;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.youtube.YouTubeIdUtil;

public class YoutubeService {

  private YoutubeVideoSubtitleService youtubeVideoSubtitleService = Aop.get(YoutubeVideoSubtitleService.class);

  public void youtube(ChannelContext channelContext, ChatMessageArgs chatSendArgs, List<UniChatMessage> historyMessage) {
    String message = null;
    if (channelContext != null) {
      if (chatSendArgs != null && chatSendArgs.getUrl() != null) {
        String url = chatSendArgs.getUrl();
        message = "First, let me get the YouTube video sub title. It will take a few minutes " + url + ".  ";

        Kv by = Kv.by("content", message);
        SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
        Tio.send(channelContext, ssePacket);

        String videoId = YouTubeIdUtil.extractVideoId(url);
        String subTitle = youtubeVideoSubtitleService.get(videoId);
        if (subTitle == null) {
          message = "Sorry, No transcript is available for this video, let me downlaod video.  It will take a few minutes.  ";
          by = Kv.by("content", message);
          ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
          Tio.send(channelContext, ssePacket);

          subTitle = youtubeVideoSubtitleService.transcriptWithGemini(url, videoId);
        }

        if (subTitle != null) {
          by = Kv.by("content", subTitle);
          ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
          Tio.send(channelContext, ssePacket);

          historyMessage.add(0, new UniChatMessage("user", subTitle));
        } else {
          message = "Sorry, No transcript is available for this video, please try again later.";
          by = Kv.by("content", message);
          ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
          Tio.send(channelContext, ssePacket);
        }

      } else {
        message = "First, let me review the YouTube video. It will take a few minutes .";
        Kv by = Kv.by("content", message);
        SsePacket ssePacket = new SsePacket(AiChatEventName.reasoning, JsonUtils.toJson(by));
        Tio.send(channelContext, ssePacket);
      }
    }
  }
}
