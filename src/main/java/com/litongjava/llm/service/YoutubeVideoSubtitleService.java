package com.litongjava.llm.service;

import java.util.List;
import java.util.concurrent.locks.Lock;

import com.google.common.util.concurrent.Striped;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.kit.PgObjectUtils;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.supadata.SubTitleContent;
import com.litongjava.supadata.SubTitleResponse;
import com.litongjava.supadata.SupadataClient;
import com.litongjava.tio.utils.json.FastJson2Utils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;
import com.litongjava.tio.utils.video.VideoTimeUtils;

public class YoutubeVideoSubtitleService {
  public static final Striped<Lock> locks = Striped.lock(1024);

  public String get(String videoId) {
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
      } else {
        return null;
      }

      List<SubTitleContent> content = subTitle.getContent();
      StringBuffer stringBuffer = new StringBuffer();
      for (SubTitleContent subTitleContent : content) {
        long offset = subTitleContent.getOffset();
        long duration = subTitleContent.getDuration();
        // 计算结束时间 = 开始时间 + 持续时间
        long endTime = offset + duration;
        String startStr = VideoTimeUtils.formatTime(offset);
        String endStr = VideoTimeUtils.formatTime(endTime);
        String text = subTitleContent.getText();
        stringBuffer.append(startStr).append("-").append(endStr).append(" ").append(text).append("\r\n");
      }
      textSubTitle = stringBuffer.toString();
      Row.by("id", SnowflakeIdUtils.id()).set("video_id", videoId).set("text_subtitle", stringBuffer.toString())
          //
          .set("supadata_subtitle", PgObjectUtils.json(responseVo.getBodyString()));
      return textSubTitle;
    } finally {
      lock.unlock();
    }
  }
}
