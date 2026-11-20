package com.chubb.claims.persistence;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="information_request")
public class InformationRequest {
    @Id
    @Column(name="id")
    public UUID id;
    @Column(name="claim_id")
    public UUID claimId;
    @Column(name="question", columnDefinition="text")
    public String question;
    @Column(name="asked_at")
    public Instant askedAt;
    @Column(name="answer", columnDefinition="text")
    public String answer;
    @Column(name="answered_at")
    public Instant answeredAt;
}
