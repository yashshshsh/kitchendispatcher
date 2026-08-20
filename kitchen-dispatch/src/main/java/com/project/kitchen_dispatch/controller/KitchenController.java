package com.project.kitchen_dispatch.controller;

import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.service.interfac.IKitchenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kitchens")
@RequiredArgsConstructor
public class KitchenController {

    private final IKitchenService kitchenService;

    @PostMapping
    public ResponseEntity<Kitchen> createKitchen(@RequestBody Kitchen kitchen) {
        Kitchen createdKitchen = kitchenService.createKitchen(kitchen);
        return new ResponseEntity<>(createdKitchen, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Kitchen> getKitchenById(@PathVariable Long id) {
        Kitchen kitchen = kitchenService.getKitchenById(id);
        return ResponseEntity.ok(kitchen);
    }
}