package com.project.kitchen_dispatch.repository;

import com.project.kitchen_dispatch.model.Kitchen;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KitchenRepository extends JpaRepository<Kitchen, Long> {
}