package com.workbench.claims.claims;

import com.workbench.claims.api.ApiException;
import com.workbench.claims.auth.Identity;
import com.workbench.claims.persistence.Claim;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
@Transactional
public class DecisionService {
  private final ClaimRepository repository;
  private final ClaimViews views;
  private final ClaimAudit audit;
  private final EntityManager em;
  private final Clock clock;

  DecisionService(
      ClaimRepository repository,
      ClaimViews views,
      ClaimAudit audit,
      EntityManager em,
      Clock clock) {
    this.repository = repository;
    this.views = views;
    this.audit = audit;
    this.em = em;
    this.clock = clock;
  }

  private Claim editable(Identity actor, UUID id, long expected) {
    Claim claim = repository.get(id);
    WorkflowService.assigned(claim, actor);
    WorkflowService.version(claim, expected);
    WorkflowService.state(claim, "UNDER_REVIEW");
    return claim;
  }

  public Map<String, Object> reserve(Identity actor, UUID id, Commands.Money input) {
    Claim claim = editable(actor, id, input.expectedVersion());
    String previous = ClaimViews.money(claim.reserve);
    // A repeated absolute estimate is still a command: increment exactly once even when the value
    // is unchanged.
    int changed =
        em.createNativeQuery(
                "update claim set reserve=:amount,version=version+1 where id=:id and"
                    + " version=:version")
            .setParameter("amount", new BigDecimal(input.amount()))
            .setParameter("id", id)
            .setParameter("version", input.expectedVersion())
            .executeUpdate();
    if (changed != 1) throw ApiException.conflict();
    em.refresh(claim);
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("previous", previous);
    details.put("current", input.amount());
    saved(claim, actor, "RESERVE_SET", "Outstanding estimate updated.", details);
    return views.view(claim, true);
  }

  public Map<String, Object> settle(Identity actor, UUID id, Commands.Money input) {
    BigDecimal amount = new BigDecimal(input.amount());
    if (amount.signum() <= 0) throw ApiException.invalid();
    Claim claim = editable(actor, id, input.expectedVersion());
    claim.settlement = amount;
    claim.reserve = new BigDecimal("0.00");
    claim.status = "SETTLED";
    claim.closedAt = clock.instant();
    repository.flush();
    saved(
        claim,
        actor,
        "CLAIM_SETTLED",
        "Claim settled for " + claim.currency + " " + input.amount() + ".",
        Map.of("amount", input.amount()));
    return views.view(claim, true);
  }

  public Map<String, Object> reject(Identity actor, UUID id, Commands.Reject input) {
    Claim claim = editable(actor, id, input.expectedVersion());
    claim.status = "REJECTED";
    claim.closedAt = clock.instant();
    if (claim.reserve != null) claim.reserve = new BigDecimal("0.00");
    repository.flush();
    saved(claim, actor, "CLAIM_REJECTED", input.reason(), Map.of("reason", input.reason()));
    return views.view(claim, true);
  }

  private void saved(
      Claim claim, Identity actor, String type, String message, Map<String, Object> fields) {
    UUID eventId = audit.append(claim, actor.id(), type, message, fields, true);
    String reference = claim.claimRef;
    long version = claim.version;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            LoggerFactory.getLogger(DecisionService.class)
                .info(
                    "reserve_saved eventId={} claimRef={} claimVersion={} action={}",
                    eventId,
                    reference,
                    version,
                    type);
          }
        });
  }
}
