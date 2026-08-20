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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchScheduler {

    private final OrderRepository orderRepository;

    private final IDispatchDecisionService
            dispatchDecisionService;

    private final IDispatchService dispatchService;

    private final IRiderService riderService;

    /*
     * Run every 30 seconds.
     *
     * This is intentionally small for our first
     * implementation so we can observe the engine
     * working during testing.
     */
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void processPendingOrders() {

        List<Order> pendingOrders =
                orderRepository.findByStatus("PLACED");

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

                log.error(
                        "Failed to process dispatch for order {}: {}",
                        order.getId(),
                        e.getMessage()
                );
            }
        }
    }

    private void processOrder(
            Order order) {

        /*
         * Find the current nearest available rider.
         *
         * We do this every scheduler cycle instead
         * of remembering a rider from order creation.
         *
         * This is important because rider availability
         * can change.
         */
        Rider rider;

        try {

            if (order.getKitchen() == null ||
                    order.getKitchen().getLatitude() == null ||
                    order.getKitchen().getLongitude() == null) {

                log.warn(
                        "Order {} cannot be dispatched: kitchen location missing",
                        order.getId()
                );

                return;
            }

            rider =
                    riderService.findNearestRider(
                            order.getKitchen().getLatitude(),
                            order.getKitchen().getLongitude()
                    );

        } catch (RuntimeException e) {

            log.info(
                    "Order {} waiting for an available rider",
                    order.getId()
            );

            return;
        }

        /*
         * Calculate when this rider should leave.
         */
        LocalDateTime recommendedDispatchTime =
                dispatchDecisionService
                        .calculateOptimalDispatchTime(
                                order,
                                rider
                        );

        LocalDateTime now =
                LocalDateTime.now();

        /*
         * Rider should wait.
         */
        if (recommendedDispatchTime.isAfter(now)) {

            log.info(
                    "Order {} waiting. Recommended dispatch time: {}",
                    order.getId(),
                    recommendedDispatchTime
            );

            return;
        }

        /*
         * The optimal dispatch time has arrived.
         *
         * Assign the rider.
         */
        Dispatch dispatch =
                createDispatch(
                        order,
                        rider
                );

        if (dispatch != null) {

            order.setStatus("ASSIGNED");

            orderRepository.save(order);

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

            /*
             * Reuse the existing dispatch service
             * validation and rider assignment logic.
             */
            Dispatch dispatch =
                    Dispatch.builder()
                            .order(order)
                            .rider(rider)
                            .build();

            return dispatchService.createDispatch(
                    dispatch
            );

        } catch (RuntimeException e) {

            log.warn(
                    "Could not dispatch order {}: {}",
                    order.getId(),
                    e.getMessage()
            );

            return null;
        }
    }
}