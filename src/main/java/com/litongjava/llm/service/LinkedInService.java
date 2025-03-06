package com.litongjava.llm.service;

import org.postgresql.util.PGobject;

import com.alibaba.fastjson2.JSONArray;
import com.litongjava.apify.ApiFyClient;
import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.ehcache.EhCacheKit;
import com.litongjava.kit.PgObjectUtils;
import com.litongjava.llm.consts.AgentTableNames;
import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.json.FastJson2Utils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
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
      if (profile.startsWith("[")) {
        try {
          profile = FastJson2Utils.parseArray(profile).toJSONString();
          EhCacheKit.put(cacheName, url, profile);
          Row row = Row.by("id", SnowflakeIdUtils.id()).set("source", url).set("profile_data", PgObjectUtils.json(profile));
          Db.save(AgentTableNames.linkedin_profile_cache, row);
        } catch (Exception e) {
          log.error("Failed to parse:{},{}", profile, e.getMessage(), e);
        }
      } else {
        try {
          profile = FastJson2Utils.parseObject(profile).toJSONString();
        } catch (Exception e) {
          log.error("Failed to parse:{},{}", profile, e.getMessage(), e);
        }
      }

      return profile;
    }
    return null;
  }

  public String profilePostsScraper(String url) {
    String cacheName = "linkedin_profile_posts_scraper";
    String profile = EhCacheKit.getString(cacheName, url);
    if (StrUtil.isNotBlank(profile)) {
      return profile;
    }

    PGobject pgObject = Db.queryColumnByField(AgentTableNames.linkedin_profile_posts_cache, "data", "source", url);
    if (pgObject != null && pgObject.getValue() != null) {
      profile = pgObject.getValue();
      EhCacheKit.put(cacheName, url, profile);
      return profile;
    }

    ResponseVo responseVo = ApiFyClient.linkedinProfilePostsScraper(url);
    if (responseVo.isOk()) {
      profile = responseVo.getBodyString();
      if (profile.startsWith("[")) {
        try {
          JSONArray parseArray = FastJson2Utils.parseArray(profile);
          profile = parseArray.toJSONString();
          EhCacheKit.put(cacheName, url, profile);
          Row row = Row.by("id", SnowflakeIdUtils.id()).set("source", url).set("data", PgObjectUtils.json(profile));
          Db.save(AgentTableNames.linkedin_profile_posts_cache, row);
        } catch (Exception e) {
          log.error("Failed to parse:{},{}", profile, e.getMessage(), e);

        }

      } else {
        try {
          profile = FastJson2Utils.parseObject(profile).toJSONString();
        } catch (Exception e) {
          log.error("Failed to parse:{},{}", profile, e.getMessage(), e);
        }
      }
      return profile;
    }
    return null;
  }
}
