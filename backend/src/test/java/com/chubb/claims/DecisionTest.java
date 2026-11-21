package com.chubb.claims;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import org.junit.jupiter.api.Test;

class DecisionTest extends ApiTest {
  String assigned(Browser aina, Browser farah) throws Exception {
    String id = call(aina, command("/api/claims", ClaimIntakeTest.INPUT), 201).path("id").asText();
    call(farah, command("/api/staff/claims/" + id + "/assign", "{\"expectedVersion\":1}"), 200);
    return id;
  }

  @Test
  void moneyVisibilityAndTerminalStates() throws Exception {
    var aina = login("aina");
    var farah = login("farah");
    String id = assigned(aina, farah);
    String base = "/api/staff/claims/" + id;
    for (String invalid : new String[] {"-1.00", "1.001", "1", "00.00"})
      call(
          farah,
          command(base + "/reserve", "{\"expectedVersion\":2,\"amount\":\"" + invalid + "\"}"),
          400);
    call(farah, command(base + "/settle", "{\"expectedVersion\":2,\"amount\":\"0.00\"}"), 400);
    call(farah, command(base + "/reject", "{\"expectedVersion\":2,\"reason\":\" \"}"), 400);
    call(farah, command(base + "/reserve", "{\"expectedVersion\":2.5,\"amount\":\"1.00\"}"), 400);
    call(farah, command(base + "/reserve", "{\"expectedVersion\":\"2\",\"amount\":\"1.00\"}"), 400);
    call(farah, command(base + "/reserve", "{\"expectedVersion\":2,\"amount\":1.00}"), 400);
    var zero =
        call(farah, command(base + "/reserve", "{\"expectedVersion\":2,\"amount\":\"0.00\"}"), 200);
    assertThat(zero.path("reserve").asText()).isEqualTo("0.00");
    assertThat(
            call(
                    farah,
                    command(base + "/reserve", "{\"expectedVersion\":3,\"amount\":\"0.00\"}"),
                    200)
                .path("version")
                .asLong())
        .isEqualTo(4);
    call(farah, command(base + "/reserve", "{\"expectedVersion\":4,\"amount\":\"5000.00\"}"), 200);
    var publicView = call(aina, get("/api/claims/" + id), 200);
    assertThat(publicView.toString())
        .doesNotContain("reserve", "actorId", "officerId", "STAFF_ONLY", "RESERVE_SET");
    call(farah, command(base + "/settle", "{\"expectedVersion\":4,\"amount\":\"4200.00\"}"), 409);
    var settled =
        call(
            farah,
            command(base + "/settle", "{\"expectedVersion\":5,\"amount\":\"4200.00\"}"),
            200);
    assertThat(settled.path("status").asText()).isEqualTo("SETTLED");
    assertThat(settled.path("reserve").asText()).isEqualTo("0.00");
    assertThat(settled.path("settlement").asText()).isEqualTo("4200.00");
    assertThat(settled.path("closedAt").isNull()).isFalse();
    for (String action : new String[] {"assign", "requests", "reserve", "settle", "reject"}) {
      String fields =
          switch (action) {
            case "requests" -> ",\"question\":\"More?\"";
            case "reserve", "settle" -> ",\"amount\":\"1.00\"";
            case "reject" -> ",\"reason\":\"No\"";
            default -> "";
          };
      call(farah, command(base + "/" + action, "{\"expectedVersion\":6" + fields + "}"), 409);
    }
    assertThat(jdbc.queryForObject("select count(*) from claim_event", Integer.class)).isEqualTo(6);
    assertThat(jdbc.queryForObject("select count(*) from outbox", Integer.class)).isEqualTo(5);
  }

  @Test
  void rejectionPreservesUnknownAndReleasesKnownReserve() throws Exception {
    var aina = login("aina");
    var farah = login("farah");
    for (boolean assessed : new boolean[] {false, true}) {
      String id = assigned(aina, farah), base = "/api/staff/claims/" + id;
      int version = 2;
      if (assessed) {
        call(
            farah,
            command(base + "/reserve", "{\"expectedVersion\":2,\"amount\":\"5000.00\"}"),
            200);
        version++;
      }
      var rejected =
          call(
              farah,
              command(
                  base + "/reject",
                  "{\"expectedVersion\":" + version + ",\"reason\":\"Duplicate confirmed.\"}"),
              200);
      assertThat(rejected.path("reserve").isNull()).isEqualTo(!assessed);
      if (assessed) assertThat(rejected.path("reserve").asText()).isEqualTo("0.00");
      assertThat(rejected.path("settlement").isNull()).isTrue();
      assertThat(call(aina, get("/api/claims/" + id), 200).toString())
          .contains("Duplicate confirmed.");
    }
  }

  @Test
  void outboxFailureRollsBackAllFinancialWrites() throws Exception {
    var aina = login("aina");
    var farah = login("farah");
    String id = assigned(aina, farah);
    jdbc.execute(
        "alter table outbox add constraint test_reject_reserve check"
            + " ((payload->>'claimVersion')::bigint < 3)");
    try {
      assertThatThrownBy(
              () ->
                  call(
                      farah,
                      command(
                          "/api/staff/claims/" + id + "/reserve",
                          "{\"expectedVersion\":2,\"amount\":\"5000.00\"}"),
                      200))
          .hasMessageContaining("Request processing failed");
    } finally {
      jdbc.execute("alter table outbox drop constraint test_reject_reserve");
    }
    assertThat(
            jdbc.queryForObject(
                "select version=2 and reserve is null from claim where id=?::uuid",
                Boolean.class,
                id))
        .isTrue();
    assertThat(jdbc.queryForObject("select count(*) from claim_event", Integer.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("select count(*) from outbox", Integer.class)).isEqualTo(1);
  }
}
