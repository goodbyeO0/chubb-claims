package com.chubb.claims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.chubb.claims.reporting.Reports;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(properties = {"claims.reporting.enabled=true", "claims.reporting.poll-ms=300"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaFlowTest extends ApiTest {
  static final KafkaContainer broker =
      new KafkaContainer("apache/kafka:4.2.2").withEnv("KAFKA_HEAP_OPTS", "-Xms256m -Xmx256m");

  static {
    try (var socket = new java.net.ServerSocket(0)) {
      int port = socket.getLocalPort();
      broker.withCreateContainerCmdModifier(
          cmd ->
              cmd.getHostConfig()
                  .withPortBindings(
                      new com.github.dockerjava.api.model.PortBinding(
                          com.github.dockerjava.api.model.Ports.Binding.bindPort(port),
                          new com.github.dockerjava.api.model.ExposedPort(9092))));
    } catch (java.io.IOException ex) {
      throw new java.io.UncheckedIOException(ex);
    }
    broker.start();
  }

  @DynamicPropertySource
  static void kafka(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", broker::getBootstrapServers);
  }

  @Autowired Reports reports;

  @AfterAll
  static void stopBroker() throws Exception {
    java.nio.file.Files.writeString(
        java.nio.file.Path.of("target/kafka-recovery-broker.log"), broker.getLogs());
    broker.stop();
  }

  @Test
  void storedEventsCatchUpAfterRealBrokerStopAndRestart() throws Exception {
    var aina = login("aina");
    var farah = login("farah");
    String id = call(aina, command("/api/claims", ClaimIntakeTest.INPUT), 201).path("id").asText();
    awaitVersion(id, 1);
    String base = "/api/staff/claims/" + id;
    call(farah, command(base + "/assign", "{\"expectedVersion\":1}"), 200);
    var docker = broker.getDockerClient();
    docker.stopContainerCmd(broker.getContainerId()).withTimeout(10).exec();
    try {
      call(
          farah, command(base + "/reserve", "{\"expectedVersion\":2,\"amount\":\"5000.00\"}"), 200);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from outbox o join claim_event e on e.id=o.event_id where"
                      + " e.claim_id=?::uuid and e.claim_version=3 and o.published_at is null",
                  Integer.class,
                  id))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "select claim_version from exposure_snapshot where claim_id=?::uuid",
                  Long.class,
                  id))
          .isEqualTo(1);
    } finally {
      docker.startContainerCmd(broker.getContainerId()).exec();
    }
    awaitVersion(id, 3);
    assertThat(reports.exposure().get("byCurrency")).isEqualTo(reports.reference());
    call(farah, command(base + "/settle", "{\"expectedVersion\":3,\"amount\":\"4200.00\"}"), 200);
    awaitVersion(id, 4);
    assertThat(reports.exposure().get("byCurrency")).isEqualTo(reports.reference());
    assertThat(
            jdbc.queryForObject(
                "select reserve from exposure_snapshot where claim_id=?::uuid", String.class, id))
        .isEqualTo("0.00");
  }

  void awaitVersion(String id, long version) {
    await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofMillis(400))
        .untilAsserted(
            () -> {
              assertThat(
                      jdbc.queryForList(
                          "select claim_version from exposure_snapshot where claim_id=?::uuid",
                          Long.class,
                          id))
                  .as(
                      "latest snapshot for %s; pending=%s",
                      id,
                      jdbc.queryForList("select event_id from outbox where published_at is null"))
                  .containsExactly(version);
              assertThat(
                      jdbc.queryForObject(
                          "select count(*) from outbox where published_at is null", Integer.class))
                  .isZero();
            });
  }
}
