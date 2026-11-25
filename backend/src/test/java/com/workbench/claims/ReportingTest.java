package com.workbench.claims;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.workbench.claims.reporting.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReportingTest extends ApiTest {
  @Autowired ExposureConsumer consumer;
  @Autowired Reports reports;

  String create(Browser aina, Browser farah, String market, String reserve) throws Exception {
    String id =
        call(aina, command("/api/claims", ClaimIntakeTest.INPUT.replace("MY", market)), 201)
            .path("id")
            .asText();
    if (reserve != null) {
      call(farah, command("/api/staff/claims/" + id + "/assign", "{\"expectedVersion\":1}"), 200);
      call(
          farah,
          command(
              "/api/staff/claims/" + id + "/reserve",
              "{\"expectedVersion\":2,\"amount\":\"" + reserve + "\"}"),
          200);
    }
    return id;
  }

  void drain() {
    jdbc.queryForList("select payload::text as payload from outbox")
        .forEach(
            row -> {
              String payload = row.get("payload").toString();
              consumer.apply(json.readTree(payload).path("claimId").asText(), payload);
            });
  }

  @Test
  void reportsReconcileByCurrencyAndReleaseFullReserve() throws Exception {
    var aina = login("aina");
    var farah = login("farah");
    String first = create(aina, farah, "MY", "5000.00");
    create(aina, farah, "MY", "1200.00");
    create(aina, farah, "MY", null);
    create(aina, farah, "SG", "800.00");
    drain();
    assertThat(reports.exposure().get("byCurrency")).isEqualTo(reports.reference());
    var exposure = call(farah, get("/api/staff/reports/exposure"), 200).path("byCurrency");
    assertThat(exposure.get(0).path("knownOutstanding").asText()).isEqualTo("6200.00");
    assertThat(exposure.get(0).path("openClaims").asLong()).isEqualTo(3);
    assertThat(exposure.get(0).path("awaitingEstimate").asLong()).isEqualTo(1);
    assertThat(exposure.get(1).path("knownOutstanding").asText()).isEqualTo("800.00");
    call(
        farah,
        command(
            "/api/staff/claims/" + first + "/settle",
            "{\"expectedVersion\":3,\"amount\":\"4200.00\"}"),
        200);
    drain();
    assertThat(reports.exposure().get("byCurrency")).isEqualTo(reports.reference());
    assertThat(
            call(farah, get("/api/staff/reports/exposure"), 200)
                .path("byCurrency")
                .get(0)
                .path("knownOutstanding")
                .asText())
        .isEqualTo("1200.00");
  }

  @Test
  void duplicateAndOlderEventsNeverTouchAppliedAtAndPoisonDoesNotWrite() throws Exception {
    String id = create(login("aina"), login("farah"), "MY", null);
    String original = jdbc.queryForObject("select payload::text from outbox", String.class);
    var latest = json.readTree(original).deepCopy();
    ((tools.jackson.databind.node.ObjectNode) latest)
        .put("claimVersion", 5)
        .put("reserve", "5000.00");
    consumer.apply(id, latest.toString());
    var before = jdbc.queryForMap("select * from exposure_snapshot");
    consumer.apply(id, latest.toString());
    consumer.apply(id, original);
    assertThat(jdbc.queryForMap("select * from exposure_snapshot")).isEqualTo(before);
    assertThatThrownBy(() -> consumer.apply("wrong", original))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                consumer.apply(id, original.replace("\"claimVersion\": 1", "\"claimVersion\": 0")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(jdbc.queryForMap("select * from exposure_snapshot")).isEqualTo(before);
  }

  @Test
  void workloadIncludesZeroOfficersAndRollingClosures() throws Exception {
    var aina = login("aina");
    var farah = login("farah");
    create(aina, farah, "MY", null);
    String waiting = create(aina, farah, "MY", "0.00");
    call(
        farah,
        command(
            "/api/staff/claims/" + waiting + "/requests",
            "{\"expectedVersion\":3,\"question\":\"Estimate?\"}"),
        200);
    for (int days : new int[] {1, 8}) {
      String closed = create(aina, farah, "MY", "0.00");
      jdbc.update(
          "update claim set"
              + " status='REJECTED',submitted_at='2026-11-01T00:00:00Z',closed_at=?::timestamptz"
              + " where id=?::uuid",
          "2026-11-" + (20 - days) + "T02:00:00Z",
          closed);
    }
    var report = call(farah, get("/api/staff/reports/workload"), 200);
    assertThat(report.path("unassigned").asLong()).isEqualTo(1);
    assertThat(report.path("awaitingInfo").asLong()).isEqualTo(1);
    assertThat(report.path("closedLast7Days").asLong()).isEqualTo(1);
    assertThat(report.path("openByOfficer").get(0).path("count").asLong()).isEqualTo(1);
    assertThat(report.path("openByOfficer").get(1).path("count").asLong()).isZero();
  }
}
