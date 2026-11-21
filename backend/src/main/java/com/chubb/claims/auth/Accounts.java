package com.chubb.claims.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class Accounts implements UserDetailsService {
  private final JdbcTemplate jdbc;

  public Accounts(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    var users =
        jdbc.query(
            "select username,password_hash,role from app_user where username=?",
            (rs, n) ->
                User.withUsername(rs.getString(1))
                    .password(rs.getString(2))
                    .roles(rs.getString(3))
                    .build(),
            username);
    if (users.isEmpty()) throw new UsernameNotFoundException("Unknown account");
    return users.getFirst();
  }

  public Identity identity(String username) {
    return jdbc.queryForObject(
        "select id,display_name,role from app_user where username=?",
        (rs, n) ->
            new Identity(rs.getObject(1, java.util.UUID.class), rs.getString(2), rs.getString(3)),
        username);
  }
}
