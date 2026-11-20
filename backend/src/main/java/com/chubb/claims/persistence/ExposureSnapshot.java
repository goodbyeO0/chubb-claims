package com.chubb.claims.persistence;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="exposure_snapshot")
public class ExposureSnapshot {
    @Id
    @Column(name="claim_id")
    public UUID claimId;
    @Column(name="claim_version")
    public long claimVersion;
    @Column(name="currency", columnDefinition="varchar(3)")
    public String currency;
    @Column(name="is_open")
    public boolean isOpen;
    @Column(name="reserve", precision=18, scale=2)
    public BigDecimal reserve;
    @Column(name="applied_at")
    public Instant appliedAt;
}
