package com.project.kitchen_dispatch.repository;

import com.project.kitchen_dispatch.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}