package com.ims.api.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where lower(user.email) = lower(:email)")
    Optional<AppUser> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select distinct user from AppUser user join user.roles role
            where role.code = 'CUSTOMER' and user.enabled = true
              and (:query is null or :query = ''
                   or lower(user.email) like lower(concat('%', :query, '%'))
                   or lower(user.fullName) like lower(concat('%', :query, '%')))
            order by user.fullName, user.email
            """)
    Page<AppUser> searchCustomers(@Param("query") String query, Pageable pageable);
}
