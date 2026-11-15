package com.ims.api.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    @EntityGraph(attributePaths = {"customer", "customerOrder", "assignedTo", "messages", "messages.author"})
    @Query("select t from SupportTicket t where t.customer.id = :customerId order by t.createdAt desc, t.id desc")
    Page<SupportTicket> findByCustomerId(@Param("customerId") long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "customerOrder", "assignedTo", "messages", "messages.author"})
    @Query("""
            select t from SupportTicket t
            where (:status is null or t.status = :status)
            order by t.createdAt desc, t.id desc
            """)
    Page<SupportTicket> findRecent(@Param("status") SupportTicketStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "customerOrder", "assignedTo", "messages", "messages.author"})
    @Query("select t from SupportTicket t where t.id = :id")
    Optional<SupportTicket> findDetailedById(@Param("id") long id);
}
