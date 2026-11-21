package com.chubb.claims.auth;

import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("local")
class LocalAccounts {
  @Bean
  ApplicationRunner seedAccounts(JdbcTemplate jdbc, PasswordEncoder encoder) {
    return args -> {
      String[] names = {"aina", "daniel", "farah", "arjun"};
      String[] display = {"Aina", "Daniel", "Farah", "Arjun"};
      int[] ids = {1, 2, 10, 11};
      for (int i = 0; i < names.length; i++) {
        jdbc.update(
            "insert into app_user(id,username,display_name,role,password_hash) values (?,?,?,?,?)"
                + " on conflict (username) do nothing",
            UUID.fromString("00000000-0000-4000-8000-%012d".formatted(ids[i])),
            names[i],
            display[i],
            i < 2 ? "CLAIMANT" : "OFFICER",
            encoder.encode("local-demo-only"));
      }
    };
  }
}
