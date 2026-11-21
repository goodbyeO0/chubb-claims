package com.chubb.claims.claims;

import com.chubb.claims.auth.Accounts;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff/claims/{id}")
class DecisionController {
  private final Accounts accounts;
  private final DecisionService service;

  DecisionController(Accounts accounts, DecisionService service) {
    this.accounts = accounts;
    this.service = service;
  }

  @PostMapping("/reserve")
  Map<String, Object> reserve(
      Principal p, @PathVariable UUID id, @Valid @RequestBody Commands.Money input) {
    return service.reserve(accounts.identity(p.getName()), id, input);
  }

  @PostMapping("/settle")
  Map<String, Object> settle(
      Principal p, @PathVariable UUID id, @Valid @RequestBody Commands.Money input) {
    return service.settle(accounts.identity(p.getName()), id, input);
  }

  @PostMapping("/reject")
  Map<String, Object> reject(
      Principal p, @PathVariable UUID id, @Valid @RequestBody Commands.Reject input) {
    return service.reject(accounts.identity(p.getName()), id, input);
  }
}
