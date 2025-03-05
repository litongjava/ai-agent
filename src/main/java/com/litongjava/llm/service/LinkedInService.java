package com.litongjava.llm.service;

import org.postgresql.util.PGobject;

import com.litongjava.apify.ApiFyClient;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.ehcache.EhCacheKit;
import com.litongjava.kit.PgObjectUtils;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

public class LinkedInService {
  public String profileScraper(String url) {
    String cacheName = "linkedin_profile_scraper";
    String profile = EhCacheKit.getString(cacheName, url);
    if (StrUtil.isNotBlank(profile)) {
      return profile;
    }

    PGobject pgObject = Db.queryColumnByField(AgentTableNames.linkedin_profile_cache, "profile_data", "source", url);
    if (pgObject != null && pgObject.getValue() != null) {
      profile = pgObject.getValue();
      EhCacheKit.put(cacheName, url, profile);
      return profile;
    }

    ResponseVo responseVo = ApiFyClient.linkedinProfileScraper(url);
    if (responseVo.isOk()) {
      profile = responseVo.getBodyString();
      EhCacheKit.put(cacheName, url, profile);
      Row row = Row.by("id", SnowflakeIdUtils.id()).set("source", url).set("profile_data", PgObjectUtils.json(profile));
      Db.save(AgentTableNames.linkedin_profile_cache, row);
      return profile;
    }
    return null;
  }
}
