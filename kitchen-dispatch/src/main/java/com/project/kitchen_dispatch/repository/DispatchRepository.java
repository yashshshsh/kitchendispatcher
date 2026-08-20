package com.project.kitchen_dispatch.repository;

import com.project.kitchen_dispatch.model.Dispatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchRepository extends JpaRepository<Dispatch, Long> {
}