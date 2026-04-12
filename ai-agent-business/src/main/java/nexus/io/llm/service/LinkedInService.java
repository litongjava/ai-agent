package nexus.io.llm.service;

import org.postgresql.util.PGobject;

import com.alibaba.fastjson2.JSONArray;

import lombok.extern.slf4j.Slf4j;
import nexus.io.apify.ApiFyClient;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.kit.PgObjectUtils;
import nexus.io.llm.consts.AgentTableNames;
import nexus.io.model.http.response.ResponseVo;
import nexus.io.tio.utils.json.FastJson2Utils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

@Slf4j
public class LinkedInService {
  public String profileScraper(String url) {
    PGobject pgObject = Db.queryColumnByField(AgentTableNames.linkedin_profile_cache, "profile_data", "source", url);
    if (pgObject != null && pgObject.getValue() != null) {
      return pgObject.getValue();
    }

    ResponseVo responseVo = ApiFyClient.linkedinProfileScraper(url);
    if (responseVo.isOk()) {
      String profile = responseVo.getBodyString();
      if (profile.startsWith("[")) {
        try {
          profile = FastJson2Utils.parseArray(profile).toJSONString();
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
    PGobject pgObject = Db.queryColumnByField(AgentTableNames.linkedin_profile_posts_cache, "data", "source", url);
    if (pgObject != null && pgObject.getValue() != null) {
      return pgObject.getValue();
    }

    ResponseVo responseVo = ApiFyClient.linkedinProfilePostsScraper(url);
    if (responseVo.isOk()) {
      String profile = responseVo.getBodyString();
      if (profile.startsWith("[")) {
        try {
          JSONArray parseArray = FastJson2Utils.parseArray(profile);
          profile = parseArray.toJSONString();
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
