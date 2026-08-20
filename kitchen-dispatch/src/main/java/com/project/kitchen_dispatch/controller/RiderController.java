package com.project.kitchen_dispatch.controller;

import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.service.interfac.IRiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/riders")
@RequiredArgsConstructor
public class RiderController {

    private final IRiderService riderService;

    @PostMapping
    public ResponseEntity<Rider> createRider(@RequestBody Rider rider) {
        return ResponseEntity.ok(riderService.createRider(rider));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Rider> getRiderById(@PathVariable Long id) {
        return ResponseEntity.ok(riderService.getRiderById(id));
    }
}