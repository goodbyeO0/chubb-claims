package com.workbench.claims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.workbench.claims.claims.ClaimRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ClaimWorkflowTest extends ApiTest {
  @MockitoSpyBean ClaimRepository repository;

  @Test
  void concurrentPickupHasExactlyOneWinner() throws Exception {
    var claim = call(login("aina"), command("/api/claims", ClaimIntakeTest.INPUT), 201);
    UUID id = UUID.fromString(claim.path("id").asText());
    var farah = login("farah");
    var arjun = login("arjun");
    var barrier = new CyclicBarrier(2);
    var connections = ConcurrentHashMap.<Integer>newKeySet();
    doAnswer(
            invocation -> {
              Object loaded = invocation.callRealMethod();
              connections.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
              barrier.await(15, TimeUnit.SECONDS);
              return loaded;
            })
        .when(repository)
        .get(id);
    try (var pool = Executors.newFixedThreadPool(2)) {
      List<Future<Integer>> results = new ArrayList<>();
      for (var browser : List.of(farah, arjun))
        results.add(
            pool.submit(
                () ->
                    mvc.perform(
                            command(
                                    "/api/staff/claims/" + id + "/assign",
                                    "{\"expectedVersion\":1}")
                                .session(browser.session())
                                .cookie(browser.csrf())
                                .header("X-XSRF-TOKEN", browser.csrf().getValue()))
                        .andReturn()
                        .getResponse()
                        .getStatus()));
      assertThat(
              List.of(
                  results.get(0).get(30, TimeUnit.SECONDS),
                  results.get(1).get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    } finally {
      reset(repository);
    }
    assertThat(connections).hasSize(2);
    assertThat(jdbc.queryForObject("select version from claim where id=?", Long.class, id))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("select count(*) from claim_event", Integer.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("select count(*) from outbox", Integer.class)).isEqualTo(1);
  }

  @Test
  void handoffChecksOwnershipStateAndRequestIdentity() throws Exception {
    var aina = login("aina");
    var daniel = login("daniel");
    var farah = login("farah");
    var arjun = login("arjun");
    String id = call(aina, command("/api/claims", ClaimIntakeTest.INPUT), 201).path("id").asText();
    String staff = "/api/staff/claims/" + id;
    call(farah, command(staff + "/assign", "{\"expectedVersion\":1}"), 200);
    var asked =
        call(
            farah,
            command(staff + "/requests", "{\"expectedVersion\":2,\"question\":\"Estimate?\"}"),
            200);
    String answer =
        "/api/claims/"
            + id
            + "/requests/"
            + asked.path("requests").get(0).path("id").asText()
            + "/answer";
    call(
        arjun,
        command(staff + "/requests", "{\"expectedVersion\":1,\"question\":\"Other?\"}"),
        403);
    assertThat(
            call(
                    farah,
                    command(staff + "/requests", "{\"expectedVersion\":3,\"question\":\"Again?\"}"),
                    409)
                .path("code")
                .asText())
        .isEqualTo("INVALID_STATE");
    call(daniel, command(answer, "{\"expectedVersion\":3,\"answer\":\"5000\"}"), 404);
    call(
        aina,
        command(
            "/api/claims/" + id + "/requests/" + UUID.randomUUID() + "/answer",
            "{\"expectedVersion\":3,\"answer\":\"5000\"}"),
        404);
    var answered = call(aina, command(answer, "{\"expectedVersion\":3,\"answer\":\"5000\"}"), 200);
    assertThat(answered.path("status").asText()).isEqualTo("UNDER_REVIEW");
    assertThat(answered.path("version").asLong()).isEqualTo(4);
    assertThat(answered.path("requests").get(0).path("answeredAt").isNull()).isFalse();
    call(aina, command(answer, "{\"expectedVersion\":3,\"answer\":\"Again\"}"), 409);
    assertThat(call(farah, get("/api/staff/claims?scope=MINE"), 200).size()).isEqualTo(1);
    assertThat(call(arjun, get("/api/staff/claims?scope=MINE"), 200).size()).isZero();
    assertThat(call(arjun, get("/api/staff/claims?scope=UNASSIGNED"), 200).size()).isZero();
    call(arjun, get("/api/staff/claims?scope=INVALID"), 400);
    assertThat(jdbc.queryForObject("select count(*) from outbox", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from claim_event", Integer.class)).isEqualTo(4);
  }
}
