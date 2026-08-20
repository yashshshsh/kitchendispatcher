package com.project.kitchen_dispatch.repository;

import com.project.kitchen_dispatch.model.Dispatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DispatchRepository
        extends JpaRepository<Dispatch, Long> {

    List<Dispatch> findByOrderId(Long orderId);

    List<Dispatch> findByRiderId(Long riderId);
}