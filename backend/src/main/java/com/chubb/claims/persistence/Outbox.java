package com.chubb.claims.persistence;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="outbox")
public class Outbox {
    @Id
    @Column(name="event_id")
    public UUID eventId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="payload", columnDefinition="jsonb")
    public String payload;
    @Column(name="published_at")
    public Instant publishedAt;
}
