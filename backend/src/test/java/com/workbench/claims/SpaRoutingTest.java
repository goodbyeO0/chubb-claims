package com.workbench.claims;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class SpaRoutingTest extends ApiTest {
  @Test
  void onlyKnownFrontendRoutesForward() throws Exception {
    for (String path :
        new String[] {
          "/",
          "/login",
          "/my-claims",
          "/my-claims/new",
          "/staff/queue",
          "/staff/reports",
          "/my-claims/00000000-0000-4000-8000-000000000101",
          "/staff/claims/00000000-0000-4000-8000-000000000101"
        }) mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    mvc.perform(get("/missing.js"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(get("/unknown-page")).andExpect(status().isNotFound());
  }
}
