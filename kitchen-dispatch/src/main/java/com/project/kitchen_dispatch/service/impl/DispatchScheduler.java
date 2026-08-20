package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.repository.OrderRepository;
import com.project.kitchen_dispatch.service.interfac.IDispatchDecisionService;
import com.project.kitchen_dispatch.service.interfac.IDispatchService;
import com.project.kitchen_dispatch.service.interfac.IRiderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchScheduler {

    private final OrderRepository orderRepository;

    private final IDispatchDecisionService
            dispatchDecisionService;

    private final IDispatchService
            dispatchService;

    private final IRiderService
            riderService;


    @Scheduled(fixedDelay = 30000)
    public void processPendingOrders() {

        List<Order> pendingOrders =
                orderRepository.findByStatus(
                        "PLACED"
                );

        if (pendingOrders.isEmpty()) {
            return;
        }

        log.info(
                "Dispatch scheduler found {} pending orders",
                pendingOrders.size()
        );

        for (Order order : pendingOrders) {

            try {

                processOrder(order);

            } catch (Exception e) {

                /*
                 * One failed order must not stop
                 * the remaining orders.
                 */
                log.error(
                        "Failed to process dispatch for order {}",
                        order.getId(),
                        e
                );
            }
        }
    }


    private void processOrder(
            Order order) {

        if (order == null ||
                order.getId() == null) {

            return;
        }


        if (!"PLACED".equals(
                order.getStatus())) {

            return;
        }


        if (order.getKitchen() == null) {

            log.warn(
                    "Order {} cannot be dispatched: kitchen missing",
                    order.getId()
            );

            return;
        }


        if (order.getKitchen().getLatitude() == null ||
                order.getKitchen().getLongitude() == null) {

            log.warn(
                    "Order {} cannot be dispatched: kitchen location missing",
                    order.getId()
            );

            return;
        }


        if (order.getDeliveryLatitude() == null ||
                order.getDeliveryLongitude() == null) {

            log.warn(
                    "Order {} cannot be dispatched: delivery location missing",
                    order.getId()
            );

            return;
        }


        Rider rider;

        try {

            rider =
                    riderService.findBestRider(
                            order
                    );

        } catch (RuntimeException e) {

            log.info(
                    "Order {} waiting for available rider: {}",
                    order.getId(),
                    e.getMessage()
            );

            return;
        }


        LocalDateTime recommendedDispatchTime =
                dispatchDecisionService
                        .calculateOptimalDispatchTime(
                                order,
                                rider
                        );

        LocalDateTime now =
                LocalDateTime.now();


        if (recommendedDispatchTime.isAfter(
                now
        )) {

            log.info(
                    "Order {} waiting until {}. Selected rider: {}",
                    order.getId(),
                    recommendedDispatchTime,
                    rider.getId()
            );

            return;
        }


        Dispatch dispatch =
                createDispatch(
                        order,
                        rider
                );

        if (dispatch != null) {

            log.info(
                    "Order {} dispatched to rider {} at {}",
                    order.getId(),
                    rider.getId(),
                    LocalDateTime.now()
            );
        }
    }


    private Dispatch createDispatch(
            Order order,
            Rider rider) {

        try {

            Dispatch dispatch =
                    Dispatch.builder()
                            .order(order)
                            .rider(rider)
                            .build();

            return dispatchService
                    .createDispatch(
                            dispatch
                    );

        } catch (RuntimeException e) {

            log.warn(
                    "Could not dispatch order {} to rider {}: {}",
                    order.getId(),
                    rider.getId(),
                    e.getMessage()
            );

            return null;
        }
    }
}