package com.workbench.claims.claims;

import com.workbench.claims.persistence.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ClaimAudit {
  private final ClaimRepository repository;
  private final Clock clock;
  private final ObjectMapper json;

  ClaimAudit(ClaimRepository repository, Clock clock, ObjectMapper json) {
    this.repository = repository;
    this.clock = clock;
    this.json = json;
  }

  public UUID append(
      Claim claim,
      UUID actor,
      String type,
      String message,
      Map<String, Object> fields,
      boolean financial) {
    ClaimEvent event = new ClaimEvent();
    event.id = UUID.randomUUID();
    event.claimId = claim.id;
    event.claimVersion = claim.version;
    event.actorId = actor;
    event.eventType = type;
    event.visibility = type.equals("RESERVE_SET") ? "STAFF_ONLY" : "CLAIMANT_VISIBLE";
    var details = new LinkedHashMap<>(fields);
    details.put("message", message);
    event.details = json.writeValueAsString(details);
    event.occurredAt = clock.instant();
    repository.persist(event);
    if (financial) {
      var payload = new LinkedHashMap<String, Object>();
      payload.put("eventId", event.id);
      payload.put("claimId", claim.id);
      payload.put("claimRef", claim.claimRef);
      payload.put("claimVersion", claim.version);
      payload.put("currency", claim.currency);
      payload.put("isOpen", claim.closedAt == null);
      payload.put("reserve", ClaimViews.money(claim.reserve));
      Outbox outbox = new Outbox();
      outbox.eventId = event.id;
      outbox.payload = json.writeValueAsString(payload);
      repository.persist(outbox);
    }
    repository.flush();
    return event.id;
  }
}
