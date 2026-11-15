package com.ims.api.audit;

import java.time.Instant;

public final class AuditDtos {
    private AuditDtos() {
    }

    public record AuditEventResponse(
            Long id,
            String eventType,
            String actorEmail,
            String targetType,
            String targetId,
            String summary,
            Instant occurredAt
    ) {
        public static AuditEventResponse from(SecurityAuditEvent event) {
            return new AuditEventResponse(
                    event.getId(),
                    event.getEventType(),
                    event.getActorEmail(),
                    event.getTargetType(),
                    event.getTargetId(),
                    event.getSummary(),
                    event.getOccurredAt()
            );
        }
    }
}
