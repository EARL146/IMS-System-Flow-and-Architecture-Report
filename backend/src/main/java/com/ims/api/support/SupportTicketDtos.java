package com.ims.api.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SupportTicketDtos {
    private SupportTicketDtos() {
    }

    public record CreateRequest(
            Long customerOrderId,
            @NotBlank(message = "subject is required") @Size(max = 200) String subject,
            @NotBlank(message = "category is required") @Size(max = 60) String category,
            SupportTicketPriority priority,
            @NotBlank(message = "message is required") @Size(max = 2000) String message
    ) {
    }

    public record MessageRequest(
            @NotBlank(message = "message is required") @Size(max = 2000) String message
    ) {
    }

    public record UpdateStatusRequest(
            @NotNull(message = "status is required") SupportTicketStatus status,
            @Size(max = 1000) String resolutionNotes
    ) {
    }

    public record MessageResponse(
            long id,
            String authorEmail,
            String authorName,
            String authorRole,
            String body,
            Instant createdAt
    ) {
        static MessageResponse from(SupportTicketMessage message) {
            return new MessageResponse(
                    message.getId(),
                    message.getAuthor().getEmail(),
                    message.getAuthor().getFullName(),
                    message.getAuthorRole(),
                    message.getBody(),
                    message.getCreatedAt()
            );
        }
    }

    public record Response(
            long id,
            String ticketNumber,
            long customerUserId,
            String customerEmail,
            String customerName,
            Long customerOrderId,
            String customerOrderNumber,
            String subject,
            String category,
            SupportTicketPriority priority,
            SupportTicketStatus status,
            String assignedToEmail,
            String resolutionNotes,
            Instant resolvedAt,
            Instant createdAt,
            List<MessageResponse> messages
    ) {
        static Response from(SupportTicket ticket) {
            return new Response(
                    ticket.getId(),
                    ticket.getTicketNumber(),
                    ticket.getCustomer().getId(),
                    ticket.getCustomer().getEmail(),
                    ticket.getCustomer().getFullName(),
                    ticket.getCustomerOrder() == null ? null : ticket.getCustomerOrder().getId(),
                    ticket.getCustomerOrder() == null ? null : ticket.getCustomerOrder().getOrderNumber(),
                    ticket.getSubject(),
                    ticket.getCategory(),
                    ticket.getPriority(),
                    ticket.getStatus(),
                    ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getEmail(),
                    ticket.getResolutionNotes(),
                    ticket.getResolvedAt(),
                    ticket.getCreatedAt(),
                    ticket.getMessages().stream().map(MessageResponse::from).toList()
            );
        }
    }
}
