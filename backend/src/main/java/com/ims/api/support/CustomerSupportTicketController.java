package com.ims.api.support;

import com.ims.api.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/customer/support-tickets")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerSupportTicketController {
    private final SupportTicketService tickets;

    public CustomerSupportTicketController(SupportTicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping
    public PageResponse<SupportTicketDtos.Response> list(Authentication authentication,
                                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return tickets.listCustomerTickets(authentication.getName(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupportTicketDtos.Response create(Authentication authentication,
                                             @Valid @RequestBody SupportTicketDtos.CreateRequest request) {
        return tickets.createCustomerTicket(authentication.getName(), request);
    }

    @PostMapping("/{id}/messages")
    public SupportTicketDtos.Response addReply(Authentication authentication,
                                               @PathVariable long id,
                                               @Valid @RequestBody SupportTicketDtos.MessageRequest request) {
        return tickets.addCustomerReply(authentication.getName(), id, request);
    }
}
