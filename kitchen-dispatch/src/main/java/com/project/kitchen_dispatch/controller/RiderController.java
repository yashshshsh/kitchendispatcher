package com.project.kitchen_dispatch.controller;

import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.service.interfac.IRiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/riders")
@RequiredArgsConstructor
public class RiderController {

    private final IRiderService riderService;

    @PostMapping
    public ResponseEntity<Rider> createRider(
            @RequestBody Rider rider) {

        Rider createdRider =
                riderService.createRider(rider);

        return new ResponseEntity<>(
                createdRider,
                HttpStatus.CREATED
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Rider> getRiderById(
            @PathVariable Long id) {

        Rider rider =
                riderService.getRiderById(id);

        return ResponseEntity.ok(rider);
    }

    @GetMapping("/available")
    public ResponseEntity<List<Rider>> getAvailableRiders() {

        return ResponseEntity.ok(
                riderService.getAvailableRiders()
        );
    }

    @GetMapping("/nearest")
    public ResponseEntity<Rider> findNearestRider(
            @RequestParam Double latitude,
            @RequestParam Double longitude) {

        Rider rider =
                riderService.findNearestRider(
                        latitude,
                        longitude
                );

        return ResponseEntity.ok(rider);
    }
}