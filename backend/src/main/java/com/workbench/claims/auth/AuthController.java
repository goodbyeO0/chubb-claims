package com.workbench.claims.auth;

import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
class AuthController {
  private final Accounts accounts;

  AuthController(Accounts accounts) {
    this.accounts = accounts;
  }

  @GetMapping("/csrf")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void csrf(CsrfToken token) {
    token.getToken();
  }

  @GetMapping("/me")
  Identity me(Principal principal) {
    return accounts.identity(principal.getName());
  }
}
