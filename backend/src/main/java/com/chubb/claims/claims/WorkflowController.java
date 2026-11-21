package com.chubb.claims.claims;

import com.chubb.claims.auth.Accounts;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
class WorkflowController {
  private final Accounts accounts;
  private final WorkflowService service;

  WorkflowController(Accounts accounts, WorkflowService service) {
    this.accounts = accounts;
    this.service = service;
  }

  @GetMapping("/api/staff/claims")
  List<Map<String, Object>> queue(
      Principal p, @RequestParam(defaultValue = "ALL") Commands.Scope scope) {
    return service.queue(accounts.identity(p.getName()), scope);
  }

  @GetMapping("/api/staff/claims/{id}")
  Map<String, Object> detail(@PathVariable UUID id) {
    return service.detail(id);
  }

  @PostMapping("/api/staff/claims/{id}/assign")
  Map<String, Object> assign(
      Principal p, @PathVariable UUID id, @Valid @RequestBody Commands.Version input) {
    return service.assign(accounts.identity(p.getName()), id, input.expectedVersion());
  }

  @PostMapping("/api/staff/claims/{id}/requests")
  Map<String, Object> ask(
      Principal p, @PathVariable UUID id, @Valid @RequestBody Commands.Ask input) {
    return service.ask(accounts.identity(p.getName()), id, input);
  }

  @PostMapping("/api/claims/{id}/requests/{requestId}/answer")
  Map<String, Object> answer(
      Principal p,
      @PathVariable UUID id,
      @PathVariable UUID requestId,
      @Valid @RequestBody Commands.Answer input) {
    return service.answer(accounts.identity(p.getName()), id, requestId, input);
  }
}
