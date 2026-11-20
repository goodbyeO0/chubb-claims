package com.chubb.claims.persistence;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="app_user")
public class AppUser {
    @Id
    @Column(name="id")
    public UUID id;
    @Column(name="username", columnDefinition="text")
    public String username;
    @Column(name="display_name", columnDefinition="text")
    public String displayName;
    @Column(name="role", columnDefinition="text")
    public String role;
    @Column(name="password_hash", columnDefinition="text")
    public String passwordHash;
}
