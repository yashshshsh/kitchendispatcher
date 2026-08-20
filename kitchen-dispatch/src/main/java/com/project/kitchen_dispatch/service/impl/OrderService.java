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
public class OrderService
        implements IOrderService {

    private final OrderRepository orderRepository;

    private final IKitchenService kitchenService;

    private final IDispatchService dispatchService;

    private final IPreparationTimeService
            preparationTimeService;

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
         */
        Kitchen kitchen =
                kitchenService.getKitchenById(
                        order.getKitchen().getId()
                );

        if (!Boolean.TRUE.equals(
                kitchen.getActive())) {

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
         * New orders always begin as PLACED.
         */
        order.setStatus("PLACED");

        /*
         * Make sure createdAt exists.
         *
         * Normally @PrePersist on Order will set this,
         * but we set it here as well because the dispatch
         * calculation may need it before the first save.
         */
        if (order.getCreatedAt() == null) {

            order.setCreatedAt(
                    java.time.LocalDateTime.now()
            );
        }

        /*
         * Estimate preparation time.
         */
        Integer preparationTime =
                preparationTimeService
                        .estimatePreparationTime(
                                order
                        );

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
         * Save the order.
         */
        Order savedOrder =
                orderRepository.save(order);

        /*
         * Try to dispatch.
         *
         * The dispatch decision engine may determine
         * that the rider should wait.
         */
        var dispatch =
                dispatchService
                        .automaticallyDispatchOrder(
                                savedOrder
                        );

        /*
         * If rider was actually assigned,
         * update order status.
         */
        if (dispatch != null) {

            savedOrder.setStatus(
                    "ASSIGNED"
            );

        } else {

            /*
             * Rider is not dispatched yet.
             */
            savedOrder.setStatus(
                    "PLACED"
            );
        }

        return orderRepository.save(
                savedOrder
        );
    }

    @Override
    public Order getOrderById(Long id) {

        return orderRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Order not found with id: "
                                        + id
                        )
                );
    }
}