package com.chubb.claims;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;

@SpringBootTest(properties = "claims.reporting.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(ApiTest.FixedTime.class)
abstract class ApiTest {
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.12");

  static {
    postgres.start();
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @TestConfiguration
  static class FixedTime {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(Instant.parse("2026-11-20T02:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void cleanClaims() {
    jdbc.execute(
        "truncate claim,information_request,claim_event,outbox,exposure_snapshot restart identity"
            + " cascade");
  }

  record Browser(MockHttpSession session, Cookie csrf) {}

  Browser login(String username) throws Exception {
    Cookie csrf =
        mvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse()
            .getCookie("XSRF-TOKEN");
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .cookie(csrf)
                    .header("X-XSRF-TOKEN", csrf.getValue())
                    .contentType("application/x-www-form-urlencoded")
                    .param("username", username)
                    .param("password", "local-demo-only"))
            .andExpect(status().isNoContent())
            .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession(false);
    Cookie fresh =
        mvc.perform(get("/api/auth/csrf").session(session))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse()
            .getCookie("XSRF-TOKEN");
    return new Browser(session, fresh);
  }

  JsonNode call(Browser browser, MockHttpServletRequestBuilder request, int expected)
      throws Exception {
    var response =
        mvc.perform(
                request
                    .session(browser.session())
                    .cookie(browser.csrf())
                    .header("X-XSRF-TOKEN", browser.csrf().getValue()))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse();
    return response.getContentAsByteArray().length == 0
        ? null
        : json.readTree(response.getContentAsByteArray());
  }

  MockHttpServletRequestBuilder command(String path, String body) {
    return post(path).contentType("application/json").content(body);
  }
}
