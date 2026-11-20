package com.chubb.claims.persistence;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="claim")
public class Claim {
    @Id
    @Column(name="id")
    public UUID id;
    @Column(name="claim_ref", columnDefinition="text")
    public String claimRef;
    @Column(name="claimant_id")
    public UUID claimantId;
    @Column(name="officer_id")
    public UUID officerId;
    @Column(name="kind", columnDefinition="text")
    public String kind;
    @Column(name="market", columnDefinition="varchar(2)")
    public String market;
    @Column(name="currency", columnDefinition="varchar(3)")
    public String currency;
    @Column(name="incident_on")
    public LocalDate incidentOn;
    @Column(name="description", columnDefinition="text")
    public String description;
    @Column(name="status", columnDefinition="text")
    public String status;
    @Column(name="reserve", precision=18, scale=2)
    public BigDecimal reserve;
    @Column(name="settlement", precision=18, scale=2)
    public BigDecimal settlement;
    @Version
    @Column(name="version")
    public long version;
    @Column(name="submitted_at")
    public Instant submittedAt;
    @Column(name="closed_at")
    public Instant closedAt;
}
