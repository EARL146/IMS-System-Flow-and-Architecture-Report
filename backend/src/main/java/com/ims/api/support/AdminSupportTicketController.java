package com.ims.api.support;

import com.ims.api.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/support-tickets")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','CUSTOMER_SUPPORT','WAREHOUSE_STAFF','CASHIER','ACCOUNTANT','INVENTORY_MANAGER')")
public class AdminSupportTicketController {
    private final SupportTicketService tickets;

    public AdminSupportTicketController(SupportTicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping
    public PageResponse<SupportTicketDtos.Response> list(@RequestParam(required = false) SupportTicketStatus status,
                                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return tickets.listAdminTickets(status, page, size);
    }

    @PostMapping("/{id}/messages")
    public SupportTicketDtos.Response addReply(Authentication authentication,
                                               @PathVariable long id,
                                               @Valid @RequestBody SupportTicketDtos.MessageRequest request) {
        return tickets.addAdminReply(authentication.getName(), id, request);
    }

    @PostMapping("/{id}/status")
    public SupportTicketDtos.Response updateStatus(Authentication authentication,
                                                   @PathVariable long id,
                                                   @Valid @RequestBody SupportTicketDtos.UpdateStatusRequest request) {
        return tickets.updateStatus(authentication.getName(), id, request);
    }
}
