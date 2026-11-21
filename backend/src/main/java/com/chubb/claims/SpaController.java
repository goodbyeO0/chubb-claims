package com.chubb.claims;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class SpaController {
  // Only actual UI routes fall back; unknown API paths and assets retain their errors.
  @GetMapping({
    "/",
    "/login",
    "/my-claims",
    "/my-claims/new",
    "/my-claims/{id:[0-9a-fA-F-]{36}}",
    "/staff/queue",
    "/staff/reports",
    "/staff/claims/{id:[0-9a-fA-F-]{36}}"
  })
  String page() {
    return "forward:/index.html";
  }
}
