package com.chubb.claims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import org.junit.jupiter.api.Test;

class ClaimIntakeTest extends ApiTest {
  static final String INPUT =
      "{\"kind\":\"MOTOR\",\"market\":\"MY\",\"incidentOn\":\"2026-11-20\",\"description\":\"Bumper"
          + " damage\"}";

  @Test
  void submissionIsAtomicVersionOneAndPrivate() throws Exception {
    var aina = login("aina");
    var created = call(aina, command("/api/claims", INPUT), 201);
    assertThat(created.path("version").asLong()).isEqualTo(1);
    assertThat(created.path("currency").asText()).isEqualTo("MYR");
    assertThat(created.path("submittedAt").asText()).isEqualTo("2026-11-20T02:00:00Z");
    assertThat(created.has("reserve")).isFalse();
    for (String table : new String[] {"claim", "claim_event", "outbox"})
      assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select reserve is null and officer_id is null and version=1 from claim",
                Boolean.class))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select payload->>'eventId'=event_id::text and payload->>'claimVersion'='1' and"
                    + " payload->'reserve'='null'::jsonb from outbox",
                Boolean.class))
        .isTrue();
    assertThat(call(login("daniel"), get("/api/claims"), 200).size()).isZero();
    call(login("daniel"), get("/api/claims/" + created.path("id").asText()), 404);
    assertThat(call(aina, get("/api/claims"), 200).size()).isEqualTo(1);
    call(login("farah"), command("/api/claims", INPUT), 403);
  }

  @Test
  void invalidAndForgedInputsLeaveNoWrites() throws Exception {
    var aina = login("aina");
    for (String input :
        new String[] {
          INPUT.replace("Bumper damage", "   "),
          INPUT.replace("MY", "XX"),
          INPUT.replace("2026-11-20", "2026-11-21"),
          INPUT.replace("}", ",\"claimantId\":\"00000000-0000-4000-8000-000000000002\"}")
        }) {
      assertThat(call(aina, command("/api/claims", input), 400).path("code").asText())
          .isEqualTo("INVALID_INPUT");
    }
    assertThat(jdbc.queryForObject("select count(*) from claim", Integer.class)).isZero();
  }
}
