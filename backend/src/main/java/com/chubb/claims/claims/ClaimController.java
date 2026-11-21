package com.chubb.claims.claims;

import com.chubb.claims.auth.Accounts;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/claims")
class ClaimController {
  private final Accounts accounts;
  private final ClaimService service;

  ClaimController(Accounts accounts, ClaimService service) {
    this.accounts = accounts;
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> submit(Principal principal, @Valid @RequestBody Commands.Submit input) {
    return service.submit(accounts.identity(principal.getName()), input);
  }

  @GetMapping
  List<Map<String, Object>> list(Principal principal) {
    return service.mine(accounts.identity(principal.getName()));
  }

  @GetMapping("/{id}")
  Map<String, Object> detail(Principal principal, @PathVariable UUID id) {
    return service.own(accounts.identity(principal.getName()), id);
  }
}
