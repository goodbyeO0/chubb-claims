package com.workbench.claims.claims;

import com.workbench.claims.api.ApiException;
import com.workbench.claims.auth.Identity;
import com.workbench.claims.persistence.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkflowService {
  private final ClaimRepository repository;
  private final ClaimViews views;
  private final ClaimAudit audit;
  private final EntityManager em;
  private final Clock clock;

  WorkflowService(
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

  @Transactional(readOnly = true)
  public List<Map<String, Object>> queue(Identity actor, Commands.Scope scope) {
    String where =
        switch (scope) {
          case ALL -> "";
          case MINE -> " where officerId=:officer";
          case UNASSIGNED -> " where officerId is null";
        };
    var query = em.createQuery("from Claim" + where + " order by submittedAt,id", Claim.class);
    if (scope == Commands.Scope.MINE) query.setParameter("officer", actor.id());
    return query.getResultList().stream().map(c -> views.view(c, true)).toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> detail(UUID id) {
    return views.view(repository.get(id), true);
  }

  static void version(Claim claim, long expected) {
    if (claim.version != expected) throw ApiException.conflict();
  }

  static void state(Claim claim, String expected) {
    if (!claim.status.equals(expected)) throw new ApiException(409, "INVALID_STATE");
  }

  static void assigned(Claim claim, Identity actor) {
    if (!actor.id().equals(claim.officerId)) throw new ApiException(403, "FORBIDDEN");
  }

  public Map<String, Object> assign(Identity actor, UUID id, long expected) {
    Claim claim = repository.get(id);
    version(claim, expected);
    state(claim, "SUBMITTED");
    claim.officerId = actor.id();
    claim.status = "UNDER_REVIEW";
    repository.flush();
    audit.append(
        claim,
        actor.id(),
        "CLAIM_ASSIGNED",
        "An officer is reviewing your claim.",
        Map.of(),
        false);
    return views.view(claim, true);
  }

  public Map<String, Object> ask(Identity actor, UUID id, Commands.Ask input) {
    Claim claim = repository.get(id);
    assigned(claim, actor);
    version(claim, input.expectedVersion());
    state(claim, "UNDER_REVIEW");
    InformationRequest request = new InformationRequest();
    request.id = UUID.randomUUID();
    request.claimId = id;
    request.question = input.question();
    request.askedAt = clock.instant();
    repository.persist(request);
    claim.status = "AWAITING_INFO";
    repository.flush();
    audit.append(
        claim,
        actor.id(),
        "INFO_REQUESTED",
        input.question(),
        Map.of("requestId", request.id),
        false);
    return views.view(claim, true);
  }

  public Map<String, Object> answer(
      Identity actor, UUID id, UUID requestId, Commands.Answer input) {
    Claim claim = repository.get(id);
    if (!claim.claimantId.equals(actor.id())) throw ApiException.missing();
    InformationRequest request = em.find(InformationRequest.class, requestId);
    if (request == null || !request.claimId.equals(id)) throw ApiException.missing();
    version(claim, input.expectedVersion());
    state(claim, "AWAITING_INFO");
    if (request.answeredAt != null) throw new ApiException(409, "INVALID_STATE");
    request.answer = input.answer();
    request.answeredAt = clock.instant();
    claim.status = "UNDER_REVIEW";
    repository.flush();
    audit.append(
        claim, actor.id(), "INFO_PROVIDED", input.answer(), Map.of("requestId", requestId), false);
    return views.view(claim, false);
  }
}
