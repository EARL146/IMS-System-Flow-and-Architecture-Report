package com.ims.api.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {
    Page<SecurityAuditEvent> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

    Page<SecurityAuditEvent> findByEventTypeOrderByOccurredAtDescIdDesc(String eventType, Pageable pageable);
}
