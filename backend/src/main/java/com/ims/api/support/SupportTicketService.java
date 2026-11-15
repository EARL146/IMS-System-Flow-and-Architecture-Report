package com.ims.api.support;

import com.ims.api.audit.SecurityAuditService;
import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.common.ApiException;
import com.ims.api.common.PageResponse;
import com.ims.api.customer.CustomerOrder;
import com.ims.api.customer.CustomerOrderRepository;
import com.ims.api.notifications.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class SupportTicketService {
    private final SupportTicketRepository tickets;
    private final CustomerOrderRepository customerOrders;
    private final AppUserRepository users;
    private final NotificationService notifications;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public SupportTicketService(SupportTicketRepository tickets,
                                CustomerOrderRepository customerOrders,
                                AppUserRepository users,
                                NotificationService notifications,
                                SecurityAuditService auditService,
                                Clock clock) {
        this.tickets = tickets;
        this.customerOrders = customerOrders;
        this.users = users;
        this.notifications = notifications;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketDtos.Response> listCustomerTickets(String customerEmail, int page, int size) {
        AppUser customer = requireUser(customerEmail);
        return PageResponse.from(tickets.findByCustomerId(customer.getId(), PageRequest.of(page, size))
                .map(SupportTicketDtos.Response::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketDtos.Response> listAdminTickets(SupportTicketStatus status, int page, int size) {
        return PageResponse.from(tickets.findRecent(status, PageRequest.of(page, size))
                .map(SupportTicketDtos.Response::from));
    }

    @Transactional
    public SupportTicketDtos.Response createCustomerTicket(String customerEmail, SupportTicketDtos.CreateRequest request) {
        AppUser customer = requireUser(customerEmail);
        CustomerOrder linkedOrder = null;
        if (request.customerOrderId() != null) {
            linkedOrder = customerOrders.findDetailedByIdAndCustomer_Id(request.customerOrderId(), customer.getId())
                    .orElseThrow(() -> ApiException.notFound("Customer order not found"));
        }
        SupportTicket ticket = new SupportTicket(
                nextTicketNumber(),
                customer,
                linkedOrder,
                request.subject().trim(),
                request.category().trim(),
                request.priority()
        );
        ticket.addMessage(new SupportTicketMessage(customer, request.message().trim(), clock.instant()));
        SupportTicket saved = tickets.saveAndFlush(ticket);
        notifications.notifyUser(customer, "SUPPORT_TICKET_CREATED",
                "Support ticket " + saved.getTicketNumber() + " opened",
                "We received your support request: " + saved.getSubject(),
                "SUPPORT_TICKET",
                saved.getTicketNumber());
        return SupportTicketDtos.Response.from(loadDetailed(saved.getId()));
    }

    @Transactional
    public SupportTicketDtos.Response addCustomerReply(String customerEmail, long ticketId, SupportTicketDtos.MessageRequest request) {
        AppUser customer = requireUser(customerEmail);
        SupportTicket ticket = loadDetailed(ticketId);
        if (!ticket.getCustomer().getId().equals(customer.getId())) {
            throw ApiException.notFound("Support ticket not found");
        }
        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            throw ApiException.conflict("Closed support tickets cannot receive new replies");
        }
        ticket.addMessage(new SupportTicketMessage(customer, request.message().trim(), clock.instant()));
        tickets.saveAndFlush(ticket);
        return SupportTicketDtos.Response.from(loadDetailed(ticketId));
    }

    @Transactional
    public SupportTicketDtos.Response addAdminReply(String staffEmail, long ticketId, SupportTicketDtos.MessageRequest request) {
        AppUser staff = requireUser(staffEmail);
        SupportTicket ticket = loadDetailed(ticketId);
        ticket.addMessage(new SupportTicketMessage(staff, request.message().trim(), clock.instant()));
        if (ticket.getStatus() == SupportTicketStatus.OPEN) {
            ticket.updateStatus(SupportTicketStatus.IN_PROGRESS, staff, null, clock.instant());
        }
        tickets.saveAndFlush(ticket);
        notifications.notifyUser(ticket.getCustomer(), "SUPPORT_TICKET_REPLY",
                "New reply on ticket " + ticket.getTicketNumber(),
                request.message().trim(),
                "SUPPORT_TICKET",
                ticket.getTicketNumber());
        return SupportTicketDtos.Response.from(loadDetailed(ticketId));
    }

    @Transactional
    public SupportTicketDtos.Response updateStatus(String staffEmail, long ticketId, SupportTicketDtos.UpdateStatusRequest request) {
        AppUser staff = requireUser(staffEmail);
        SupportTicket ticket = loadDetailed(ticketId);
        ticket.updateStatus(request.status(), staff, request.resolutionNotes(), clock.instant());
        tickets.saveAndFlush(ticket);
        auditService.record("SUPPORT_TICKET_STATUS", staff.getEmail(), "SUPPORT_TICKET",
                ticket.getTicketNumber(), "Updated ticket status to " + request.status());
        notifications.notifyUser(ticket.getCustomer(), "SUPPORT_TICKET_STATUS",
                "Ticket " + ticket.getTicketNumber() + " is now " + request.status(),
                request.resolutionNotes() == null || request.resolutionNotes().isBlank()
                        ? "Status changed to " + request.status()
                        : request.resolutionNotes().trim(),
                "SUPPORT_TICKET",
                ticket.getTicketNumber());
        return SupportTicketDtos.Response.from(loadDetailed(ticketId));
    }

    private SupportTicket loadDetailed(long id) {
        return tickets.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound("Support ticket not found"));
    }

    private AppUser requireUser(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(ApiException::unauthorized);
    }

    private String nextTicketNumber() {
        return "TKT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
