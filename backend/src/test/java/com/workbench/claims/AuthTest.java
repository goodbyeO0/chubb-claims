package com.workbench.claims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class AuthTest extends ApiTest {
  @Test
  void realFormSessionAndLogout() throws Exception {
    mvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    var aina = login("aina");
    var me = call(aina, get("/api/auth/me"), 200);
    assertThat(me.size()).isEqualTo(3);
    assertThat(me.path("role").asText()).isEqualTo("CLAIMANT");
    mvc.perform(post("/api/auth/logout").session(aina.session()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    call(aina, post("/api/auth/logout"), 204);
    assertThat(aina.session().isInvalid()).isTrue();
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    assertThat(call(login("farah"), get("/api/auth/me"), 200).path("role").asText())
        .isEqualTo("OFFICER");
  }

  @Test
  void badCredentialsAreIndistinguishable() throws Exception {
    String previous = null;
    for (String username : new String[] {"aina", "unknown"}) {
      var token =
          mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
      var result =
          mvc.perform(
                  post("/api/auth/login")
                      .cookie(token)
                      .header("X-XSRF-TOKEN", token.getValue())
                      .param("username", username)
                      .param("password", "wrong"))
              .andExpect(status().isUnauthorized())
              .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
              .andReturn();
      assertThat(result.getRequest().getSession(false)).isNull();
      if (previous != null)
        assertThat(result.getResponse().getContentAsString()).isEqualTo(previous);
      previous = result.getResponse().getContentAsString();
    }
  }

  @Test
  void csrfAndRoleFailuresAreStructured() throws Exception {
    mvc.perform(post("/api/auth/login"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    var aina = login("aina");
    call(aina, get("/api/staff/claims"), 403);
    mvc.perform(
            post("/api/claims")
                .session(aina.session())
                .cookie(aina.csrf())
                .header("X-XSRF-TOKEN", "wrong"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    assertThat(jdbc.queryForObject("select count(*) from claim", Integer.class)).isZero();
    assertThat(
            jdbc.queryForObject(
                "select password_hash from app_user where username='aina'", String.class))
        .startsWith("$2");
  }
}
