package com.project.kitchen_dispatch.repository;

import com.project.kitchen_dispatch.model.Rider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RiderRepository extends JpaRepository<Rider, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM Rider r
            WHERE r.available = true
              AND r.active = true
            """)
    List<Rider> findAvailableRidersForDispatch();
}