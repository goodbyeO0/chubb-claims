package com.chubb.claims.claims;

import com.chubb.claims.api.ApiException;
import com.chubb.claims.persistence.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class ClaimRepository {
  private final EntityManager em;

  public ClaimRepository(EntityManager em) {
    this.em = em;
  }

  public Claim get(UUID id) {
    Claim claim = em.find(Claim.class, id);
    if (claim == null) throw ApiException.missing();
    return claim;
  }

  public List<Claim> owned(UUID owner) {
    return em.createQuery(
            "from Claim where claimantId=:owner order by submittedAt desc,id", Claim.class)
        .setParameter("owner", owner)
        .getResultList();
  }

  public List<InformationRequest> requests(UUID id) {
    return em.createQuery(
            "from InformationRequest where claimId=:id order by askedAt,id",
            InformationRequest.class)
        .setParameter("id", id)
        .getResultList();
  }

  public List<ClaimEvent> events(UUID id) {
    return em.createQuery(
            "from ClaimEvent where claimId=:id order by claimVersion", ClaimEvent.class)
        .setParameter("id", id)
        .getResultList();
  }

  public void persist(Object entity) {
    em.persist(entity);
  }

  public void flush() {
    em.flush();
  }
}
