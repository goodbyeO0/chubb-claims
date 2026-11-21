package com.chubb.claims.reporting;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff/reports")
class ReportController {
  private final Reports reports;

  ReportController(Reports reports) {
    this.reports = reports;
  }

  @GetMapping("/workload")
  Map<String, Object> workload() {
    return reports.workload();
  }

  @GetMapping("/exposure")
  Map<String, Object> exposure() {
    return reports.exposure();
  }
}
