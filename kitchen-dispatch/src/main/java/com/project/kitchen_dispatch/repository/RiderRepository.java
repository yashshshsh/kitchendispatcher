package com.project.kitchen_dispatch.repository;

import com.project.kitchen_dispatch.model.Rider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiderRepository extends JpaRepository<Rider, Long> {

    List<Rider> findByAvailableTrueAndActiveTrue();
}