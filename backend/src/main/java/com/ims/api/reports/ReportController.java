package com.ims.api.reports;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','ACCOUNTANT','INVENTORY_MANAGER','PURCHASING_OFFICER')")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/overview")
    public ReportDtos.OverviewResponse overview() {
        return reports.overview();
    }

    @GetMapping("/export/{type}")
    public ResponseEntity<byte[]> exportCsv(@PathVariable String type) {
        String csv = reports.exportCsv(type);
        String filename = "ims-" + type.toLowerCase() + "-report.csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
