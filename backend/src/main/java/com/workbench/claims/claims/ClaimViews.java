package com.workbench.claims.claims;

import com.workbench.claims.persistence.Claim;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Explicit wire allowlists. Persistence entities never cross the HTTP boundary. */
@Component
public class ClaimViews {
  private final ClaimRepository repository;
  private final ObjectMapper json;

  ClaimViews(ClaimRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  public static String money(BigDecimal amount) {
    return amount == null ? null : amount.setScale(2).toPlainString();
  }

  public Map<String, Object> view(Claim claim, boolean staff) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", claim.id);
    dto.put("claimRef", claim.claimRef);
    dto.put("kind", claim.kind);
    dto.put("market", claim.market);
    dto.put("currency", claim.currency);
    dto.put("incidentOn", claim.incidentOn);
    dto.put("description", claim.description);
    dto.put("status", claim.status);
    dto.put("version", claim.version);
    dto.put("submittedAt", claim.submittedAt);
    dto.put("closedAt", claim.closedAt);
    dto.put("settlement", money(claim.settlement));
    if (staff) {
      dto.put("claimantId", claim.claimantId);
      dto.put("officerId", claim.officerId);
      dto.put("reserve", money(claim.reserve));
    }
    dto.put(
        "requests",
        repository.requests(claim.id).stream()
            .map(
                r -> {
                  Map<String, Object> request = new LinkedHashMap<>();
                  request.put("id", r.id);
                  request.put("question", r.question);
                  request.put("askedAt", r.askedAt);
                  request.put("answer", r.answer);
                  request.put("answeredAt", r.answeredAt);
                  return request;
                })
            .toList());
    dto.put(
        "timeline",
        repository.events(claim.id).stream()
            .filter(e -> staff || e.visibility.equals("CLAIMANT_VISIBLE"))
            .map(
                e -> {
                  Map<String, Object> event = new LinkedHashMap<>();
                  event.put("type", e.eventType);
                  event.put("at", e.occurredAt);
                  event.put("message", json.readTree(e.details).path("message").asText());
                  if (staff) {
                    event.put("actorId", e.actorId);
                    event.put("visibility", e.visibility);
                  }
                  return event;
                })
            .toList());
    return dto;
  }
}
