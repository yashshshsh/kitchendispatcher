package com.project.kitchendispatch.repository;

import com.project.kitchendispatch.model.Kitchen;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KitchenRepository extends JpaRepository<Kitchen, Long> {
}