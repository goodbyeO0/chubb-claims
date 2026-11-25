package com.workbench.claims.persistence;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "information_request")
public class InformationRequest {
  @Id
  @Column(name = "id")
  public UUID id;

  @Column(name = "claim_id")
  public UUID claimId;

  @Column(name = "question", columnDefinition = "text")
  public String question;

  @Column(name = "asked_at")
  public Instant askedAt;

  @Column(name = "answer", columnDefinition = "text")
  public String answer;

  @Column(name = "answered_at")
  public Instant answeredAt;
}
