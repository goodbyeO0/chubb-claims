package com.workbench.claims.reporting;

import java.math.BigDecimal;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import tools.jackson.databind.*;

@Service
public class ExposureConsumer {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private static final Set<String> KEYS =
      Set.of("eventId", "claimId", "claimRef", "claimVersion", "currency", "isOpen", "reserve");
  private static final Set<String> CURRENCIES = Set.of("MYR", "SGD", "THB", "IDR", "PHP", "HKD");

  ExposureConsumer(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Transactional
  public void apply(String key, String payload) {
    JsonNode body = json.readTree(payload);
    if (!body.isObject()
        || !body.propertyNames().equals(KEYS)
        || !body.path("claimVersion").isIntegralNumber()
        || !body.path("claimVersion").canConvertToLong()
        || body.path("claimVersion").asLong() < 1
        || !body.path("isOpen").isBoolean()
        || !body.path("claimRef").isTextual()
        || body.path("claimRef").asText().isEmpty()
        || !CURRENCIES.contains(body.path("currency").asText()))
      throw new IllegalArgumentException("Invalid exposure envelope");
    UUID eventId = UUID.fromString(body.path("eventId").asText());
    UUID id = UUID.fromString(body.path("claimId").asText());
    if (!id.toString().equals(key)) throw new IllegalArgumentException("Exposure key mismatch");
    JsonNode reserve = body.path("reserve");
    if (!reserve.isNull()
        && (!reserve.isTextual() || !reserve.asText().matches("^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$")))
      throw new IllegalArgumentException("Invalid reserve");
    long version = body.path("claimVersion").asLong();
    String reference = body.path("claimRef").asText();
    int changed =
        jdbc.update(
            "insert into"
                + " exposure_snapshot(claim_id,claim_version,currency,is_open,reserve,applied_at)"
                + " values (?,?,?,?,?,current_timestamp) on conflict(claim_id) do update set"
                + " claim_version=excluded.claim_version,currency=excluded.currency,is_open=excluded.is_open,reserve=excluded.reserve,applied_at=excluded.applied_at"
                + " where exposure_snapshot.claim_version<excluded.claim_version",
            id,
            version,
            body.path("currency").asText(),
            body.path("isOpen").asBoolean(),
            reserve.isNull() ? null : new BigDecimal(reserve.asText()));
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            LoggerFactory.getLogger(ExposureConsumer.class)
                .info(
                    "{} eventId={} claimRef={} claimVersion={} reason={}",
                    changed == 1 ? "snapshot_updated" : "snapshot_skipped",
                    eventId,
                    reference,
                    version,
                    changed == 1 ? "newer_version" : "equal_or_older_version");
          }
        });
  }
}
