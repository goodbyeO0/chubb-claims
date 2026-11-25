package com.workbench.claims.persistence;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "claim_event")
public class ClaimEvent {
  @Id
  @Column(name = "id")
  public UUID id;

  @Column(name = "claim_id")
  public UUID claimId;

  @Column(name = "claim_version")
  public long claimVersion;

  @Column(name = "actor_id")
  public UUID actorId;

  @Column(name = "event_type", columnDefinition = "text")
  public String eventType;

  @Column(name = "visibility", columnDefinition = "text")
  public String visibility;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "details", columnDefinition = "jsonb")
  public String details;

  @Column(name = "occurred_at")
  public Instant occurredAt;
}
