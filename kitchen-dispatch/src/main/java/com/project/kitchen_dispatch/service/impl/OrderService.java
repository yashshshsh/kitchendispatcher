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

import java.time.LocalDateTime;

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

        if (isBlank(order.getCustomerName())) {
            throw new RuntimeException(
                    "Customer name is required"
            );
        }

        if (isBlank(order.getCustomerPhone())) {
            throw new RuntimeException(
                    "Customer phone is required"
            );
        }

        if (isBlank(order.getDeliveryAddress())) {
            throw new RuntimeException(
                    "Delivery address is required"
            );
        }

        validateCoordinates(
                order.getDeliveryLatitude(),
                order.getDeliveryLongitude(),
                "Delivery"
        );

        if (order.getKitchen() == null ||
                order.getKitchen().getId() == null) {

            throw new RuntimeException(
                    "Kitchen is required for an order"
            );
        }

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

        validateCoordinates(
                kitchen.getLatitude(),
                kitchen.getLongitude(),
                "Kitchen"
        );

        order.setKitchen(kitchen);

        order.setStatus("PLACED");

        if (order.getCreatedAt() == null) {

            order.setCreatedAt(
                    LocalDateTime.now()
            );
        }

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

        Order savedOrder =
                orderRepository.save(order);

        var dispatch =
                dispatchService
                        .automaticallyDispatchOrder(
                                savedOrder
                        );

        if (dispatch != null) {

            savedOrder.setStatus(
                    "ASSIGNED"
            );

        } else {

            savedOrder.setStatus(
                    "PLACED"
            );
        }

        return orderRepository.save(
                savedOrder
        );
    }


    @Override
    @Transactional(readOnly = true)
    public Order getOrderById(
            Long id) {

        if (id == null) {
            throw new RuntimeException(
                    "Order id is required"
            );
        }

        return orderRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Order not found with id: "
                                        + id
                        )
                );
    }


    @Override
    @Transactional
    public Order saveOrder(
            Order order) {

        if (order == null ||
                order.getId() == null) {

            throw new RuntimeException(
                    "Valid order is required"
            );
        }

        return orderRepository.save(
                order
        );
    }


    private boolean isBlank(
            String value) {

        return value == null ||
                value.isBlank();
    }


    private void validateCoordinates(
            Double latitude,
            Double longitude,
            String locationName) {

        if (latitude == null ||
                longitude == null) {

            throw new RuntimeException(
                    locationName +
                            " location is required"
            );
        }

        if (latitude < -90 ||
                latitude > 90) {

            throw new RuntimeException(
                    locationName +
                            " latitude must be between -90 and 90"
            );
        }

        if (longitude < -180 ||
                longitude > 180) {

            throw new RuntimeException(
                    locationName +
                            " longitude must be between -180 and 180"
            );
        }
    }
}