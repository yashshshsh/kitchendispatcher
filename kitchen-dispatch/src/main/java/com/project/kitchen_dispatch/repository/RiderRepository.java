package com.project.kitchen_dispatch.repository;

import com.project.kitchen_dispatch.model.Rider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RiderRepository
        extends JpaRepository<Rider, Long> {

    /*
     * Normal read-only query.
     *
     * Used by:
     * GET /api/riders/available
     */
    List<Rider> findByAvailableTrueAndActiveTrue();


    /*
     * Locked query used during actual dispatch selection.
     *
     * Pessimistic locking prevents two dispatch operations
     * from selecting the same available rider simultaneously.
     *
     * IMPORTANT:
     * This method MUST be executed inside an active transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM Rider r
            WHERE r.available = true
              AND r.active = true
              AND r.latitude IS NOT NULL
              AND r.longitude IS NOT NULL
            """)
    List<Rider> findAvailableRidersForDispatch();


    /*
     * Locks one rider before creating a dispatch.
     *
     * This closes the race-condition window between:
     *
     * 1. selecting the rider
     * 2. creating the dispatch
     *
     * It guarantees that the availability check and update
     * happen against a locked database row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM Rider r
            WHERE r.id = :id
            """)
    Optional<Rider> findByIdForUpdate(Long id);
}