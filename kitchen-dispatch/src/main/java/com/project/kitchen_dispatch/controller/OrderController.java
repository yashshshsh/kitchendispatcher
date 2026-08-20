package com.project.kitchen_dispatch.controller;

import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.service.interfac.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final IOrderService orderService;

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {

        Order createdOrder = orderService.createOrder(order);

        return new ResponseEntity<>(
                createdOrder,
                HttpStatus.CREATED
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {

        Order order = orderService.getOrderById(id);

        return ResponseEntity.ok(order);
    }
}