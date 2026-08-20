package com.project.kitchen_dispatch.repository;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DispatchRepository
        extends JpaRepository<Dispatch, Long> {

    Optional<Dispatch> findByOrder(Order order);
}