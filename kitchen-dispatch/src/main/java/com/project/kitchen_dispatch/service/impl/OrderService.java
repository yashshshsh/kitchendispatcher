package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.repository.OrderRepository;
import com.project.kitchen_dispatch.service.interfac.IDispatchService;
import com.project.kitchen_dispatch.service.interfac.IKitchenService;
import com.project.kitchen_dispatch.service.interfac.IOrderService;
import com.project.kitchen_dispatch.service.interfac.IPreparationTimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService implements IOrderService {

    private final OrderRepository orderRepository;
    private final IKitchenService kitchenService;
    private final IDispatchService dispatchService;
    private final IPreparationTimeService preparationTimeService;

    @Override
    @Transactional
    public Order createOrder(Order order) {

        if (order == null) {
            throw new RuntimeException(
                    "Order is required"
            );
        }

        if (order.getKitchen() == null ||
                order.getKitchen().getId() == null) {

            throw new RuntimeException(
                    "Kitchen is required for an order"
            );
        }

        /*
         * Load the actual Kitchen from the database.
         *
         * Do not directly use the Kitchen object sent
         * inside the JSON request.
         */
        Kitchen kitchen =
                kitchenService.getKitchenById(
                        order.getKitchen().getId()
                );

        if (!Boolean.TRUE.equals(kitchen.getActive())) {

            throw new RuntimeException(
                    "Kitchen is inactive"
            );
        }

        if (kitchen.getLatitude() == null ||
                kitchen.getLongitude() == null) {

            throw new RuntimeException(
                    "Kitchen location is not available"
            );
        }

        /*
         * Attach the managed Kitchen entity.
         */
        order.setKitchen(kitchen);

        /*
         * New orders always start as PLACED.
         */
        order.setStatus("PLACED");

        /*
         * Estimate preparation time.
         */
        Integer preparationTime =
                preparationTimeService
                        .estimatePreparationTime(order);

        if (preparationTime == null ||
                preparationTime <= 0) {

            throw new RuntimeException(
                    "Invalid preparation time"
            );
        }

        order.setEstimatedPreparationTime(
                preparationTime
        );

        /*
         * Save the order first.
         */
        Order savedOrder =
                orderRepository.save(order);

        /*
         * Automatically find and assign a rider.
         */
        dispatchService.automaticallyDispatchOrder(
                savedOrder
        );

        /*
         * Dispatch was successful.
         */
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