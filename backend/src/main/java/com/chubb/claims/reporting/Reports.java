package com.chubb.claims.reporting;

import com.chubb.claims.claims.ClaimViews;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class Reports {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  Reports(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public Map<String, Object> workload() {
    var officers =
        jdbc.query(
            "select u.id,u.display_name,count(c.id) as count from app_user u left join claim c on"
                + " c.officer_id=u.id and c.closed_at is null where u.role='OFFICER' group by"
                + " u.id,u.display_name order by u.id",
            (rs, n) ->
                Map.of(
                    "officerId",
                    rs.getObject("id"),
                    "displayName",
                    rs.getString("display_name"),
                    "count",
                    rs.getLong("count")));
    return Map.of(
        "unassigned",
        jdbc.queryForObject("select count(*) from claim where officer_id is null", Long.class),
        "awaitingInfo",
        jdbc.queryForObject("select count(*) from claim where status='AWAITING_INFO'", Long.class),
        "openByOfficer",
        officers,
        "closedLast7Days",
        jdbc.queryForObject(
            "select count(*) from claim where closed_at >= ? and closed_at <= ?",
            Long.class,
            Timestamp.from(clock.instant().minus(Duration.ofDays(7))),
            Timestamp.from(clock.instant())));
  }

  public Map<String, Object> exposure() {
    Map<String, Object> result = new LinkedHashMap<>();
    Timestamp applied =
        jdbc.queryForObject("select max(applied_at) from exposure_snapshot", Timestamp.class);
    result.put("lastAppliedAt", applied == null ? null : applied.toInstant());
    result.put("byCurrency", groups("exposure_snapshot", "is_open"));
    return result;
  }

  public List<Map<String, Object>> reference() {
    return groups("claim", "closed_at is null");
  }

  private List<Map<String, Object>> groups(String table, String where) {
    return jdbc.query(
        "select currency,count(*) as open,count(*) filter(where reserve is null) as"
            + " unknown,coalesce(sum(reserve),0) as known from "
            + table
            + " where "
            + where
            + " group by currency order by currency",
        (rs, n) ->
            Map.<String, Object>of(
                "currency",
                rs.getString("currency"),
                "openClaims",
                rs.getLong("open"),
                "awaitingEstimate",
                rs.getLong("unknown"),
                "knownOutstanding",
                ClaimViews.money(rs.getBigDecimal("known"))));
  }
}
