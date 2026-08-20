package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.repository.OrderRepository;
import com.project.kitchen_dispatch.service.interfac.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService implements IOrderService {

    private final OrderRepository orderRepository;

    @Override
    public Order createOrder(Order order) {
        return orderRepository.save(order);
    }

    @Override
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Order not found with id: " + id));
    }
}