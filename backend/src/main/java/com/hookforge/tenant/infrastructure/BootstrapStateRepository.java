package com.hookforge.tenant.infrastructure;

import com.hookforge.tenant.domain.BootstrapState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BootstrapStateRepository
        extends JpaRepository<BootstrapState, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BootstrapState b WHERE b.id = :id")
    Optional<BootstrapState> findByIdForUpdate(@Param("id") Short id);
}