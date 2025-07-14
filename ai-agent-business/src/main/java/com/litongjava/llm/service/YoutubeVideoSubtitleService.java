package com.litongjava.llm.service;

import java.util.List;
import java.util.concurrent.locks.Lock;

import com.google.common.util.concurrent.Striped;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.gemini.GeminiClient;
import com.litongjava.gemini.GoogleGeminiModels;
import com.litongjava.kit.PgObjectUtils;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.supadata.SubTitleContent;
import com.litongjava.supadata.SubTitleResponse;
import com.litongjava.supadata.SupadataClient;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.json.FastJson2Utils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;
import com.litongjava.tio.utils.video.VideoTimeUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YoutubeVideoSubtitleService {
  public static final Striped<Lock> locks = Striped.lock(1024);

  public String get(String videoId) {
    String url = "https://www.youtube.com/watch?v=" + videoId;
    if (StrUtil.isBlank(videoId)) {
      return null;
    }
    Lock lock = locks.get(videoId);
    lock.lock();
    try {
      String sql = "select text_subtitle from %s where video_id=?";
      sql = String.format(sql, AgentTableNames.youtube_video_subtitle);
      String textSubTitle = Db.queryStr(sql, videoId);
      if (textSubTitle != null) {
        return textSubTitle;
      }
      ResponseVo responseVo = SupadataClient.get(videoId);
      SubTitleResponse subTitle = null;
      if (responseVo.isOk()) {
        subTitle = FastJson2Utils.parse(responseVo.getBodyString(), SubTitleResponse.class);
        List<SubTitleContent> content = subTitle.getContent();
        if (content == null) {
          return null;
        }
        StringBuffer stringBuffer = new StringBuffer();
        for (SubTitleContent subTitleContent : content) {
          long offset = subTitleContent.getOffset();
          long duration = subTitleContent.getDuration();
          // 计算结束时间 = 开始时间 + 持续时间
          long endTime = offset + duration;
          String startStr = VideoTimeUtils.formatTime(offset);
          String endStr = VideoTimeUtils.formatTime(endTime);
          String text = subTitleContent.getText();
          stringBuffer.append(startStr).append("-").append(endStr).append(" ").append(text).append("  \r\n");
        }
        textSubTitle = stringBuffer.toString();
      } else {
        textSubTitle = this.transcriptWithGemini(videoId, url);
      }
      if (textSubTitle != null) {
        Row row = Row.by("id", SnowflakeIdUtils.id()).set("video_id", videoId).set("text_subtitle", textSubTitle)
            //
            .set("supadata_subtitle", PgObjectUtils.json(responseVo.getBodyString()));
        Db.save(AgentTableNames.youtube_video_subtitle, row);
      }
      return textSubTitle;
    } finally {
      lock.unlock();
    }
  }

  public String transcriptWithGemini(String url, String videoId) {
    Lock lock = locks.get(videoId);
    lock.lock();
    try {
      String sql = "select text_subtitle from %s where video_id=?";
      sql = String.format(sql, AgentTableNames.youtube_video_subtitle);
      String textSubTitle = Db.queryStr(sql, videoId);
      if (textSubTitle != null) {
        return textSubTitle;
      }
      textSubTitle = transcriptWithGemini(url);
      if (textSubTitle == null) {
        return null;
      }
      Row row = Row.by("id", SnowflakeIdUtils.id()).set("video_id", videoId).set("text_subtitle", textSubTitle);
      Db.save(AgentTableNames.youtube_video_subtitle, row);
      return textSubTitle;
    } finally {
      lock.unlock();
    }
  }

  private String transcriptWithGemini(String url) {
    String model = GoogleGeminiModels.GEMINI_2_5_FLASH_PREVIEW_04_17;
    log.info("parse subtitile:{},{}", model, url);
    String userPrompt = "Extract video subtitles, output format [hh:mm:ss-hh:mm:ss] subtitle  \r\n";
    String text = GeminiClient.parseYoutubeSubtitle(model, url, userPrompt);
    return text;
  }

}
