package com.ims.api.audit;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SecurityAuditService {
    private final SecurityAuditEventRepository events;
    private final AppUserRepository users;

    public SecurityAuditService(SecurityAuditEventRepository events, AppUserRepository users) {
        this.events = events;
        this.users = users;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(String eventType, String actorEmail, String targetType, String targetId, String summary) {
        AppUser actor = actorEmail == null ? null : users.findByEmailIgnoreCase(actorEmail).orElse(null);
        events.saveAndFlush(new SecurityAuditEvent(eventType, actor, actorEmail, targetType, targetId, summary));
    }

    @Transactional
    public void record(String eventType, String actorEmail, String targetType, String targetId, String summary) {
        AppUser actor = actorEmail == null ? null : users.findByEmailIgnoreCase(actorEmail).orElse(null);
        events.save(new SecurityAuditEvent(eventType, actor, actorEmail, targetType, targetId, summary));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditDtos.AuditEventResponse> list(String eventType, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = (eventType == null || eventType.isBlank())
                ? events.findAllByOrderByOccurredAtDescIdDesc(pageable)
                : events.findByEventTypeOrderByOccurredAtDescIdDesc(eventType.trim(), pageable);
        return PageResponse.from(result.map(AuditDtos.AuditEventResponse::from));
    }

    @Transactional(readOnly = true)
    public List<AuditDtos.AuditEventResponse> recentForExport(int limit) {
        return events.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, limit))
                .map(AuditDtos.AuditEventResponse::from)
                .getContent();
    }
}
