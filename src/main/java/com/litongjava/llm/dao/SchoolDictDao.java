package com.litongjava.llm.dao;

import com.litongjava.db.activerecord.Db;
import com.litongjava.db.activerecord.Row;
import com.litongjava.llm.vo.SchoolDict;

/*
 * { "id": "1", "rmp_school_id": 881, "full_name":
 * "San Jose State University (SJSU)", "abbr_name": "SJSU", "bot_name":
 * "Spartan", "remark": null, "creator": "", "create_time": 1725192484794,
 * "updater": "", "update_time": 1725192484794, "deleted": 0, "tenant_id": "0" }
 */
public class SchoolDictDao {
  public Row getById(Long id) {
    String sql = "select * from rumi_school_dict where id=?";
    return Db.findFirstByCache("rumi_school_dict", id, 600, sql, id);
  }

  public SchoolDict getNameById(Long id) {
    String sql = "select id,full_name,abbr_name,bot_name from rumi_school_dict where id=?";
    Row record = Db.findFirstByCache("rumi_school_dict", id, 600, sql, id);
    if (record == null) {
      return null;
    }
    String fullName = record.getStr("full_name");
    String abbrName = record.getStr("abbr_name");
    String botName = record.getStr("botName");
    SchoolDict schoolDict = new SchoolDict(id, fullName, abbrName, botName);
    return schoolDict;
  }

}
