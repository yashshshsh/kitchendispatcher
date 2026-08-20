package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.repository.OrderRepository;
import com.project.kitchen_dispatch.service.interfac.IDispatchService;
import com.project.kitchen_dispatch.service.interfac.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService implements IOrderService {

    private final OrderRepository orderRepository;
    private final IDispatchService dispatchService;

    @Override
    @Transactional
    public Order createOrder(Order order) {

        if (order.getKitchen() == null ||
                order.getKitchen().getId() == null) {

            throw new RuntimeException(
                    "Kitchen is required for an order"
            );
        }

        order.setStatus("PLACED");

        Order savedOrder =
                orderRepository.save(order);

        dispatchService.automaticallyDispatchOrder(
                savedOrder
        );

        savedOrder.setStatus("ASSIGNED");

        return orderRepository.save(savedOrder);
    }

    @Override
    public Order getOrderById(Long id) {

        return orderRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Order not found with id: " + id
                        ));
    }
}