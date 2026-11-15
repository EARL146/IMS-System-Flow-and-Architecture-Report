package com.ims.api.audit;

import com.ims.api.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/audit-events")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','ACCOUNTANT')")
public class AdminAuditController {
    private final SecurityAuditService auditService;

    public AdminAuditController(SecurityAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public PageResponse<AuditDtos.AuditEventResponse> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return auditService.list(eventType, page, size);
    }
}
