package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.repository.DispatchRepository;
import com.project.kitchen_dispatch.repository.OrderRepository;
import com.project.kitchen_dispatch.service.interfac.IDispatchDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DispatchScheduler {

    private final OrderRepository orderRepository;

    private final DispatchRepository dispatchRepository;

    private final RiderService riderService;

    private final DispatchService dispatchService;

    private final IDispatchDecisionService dispatchDecisionService;


    /*
     * Runs every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
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

            } catch (Exception exception) {

                log.warn(
                        "Could not process order {}: {}",
                        order.getId(),
                        exception.getMessage()
                );
            }
        }
    }


    /*
     * Processes one pending order.
     */
    protected void processOrder(Order order) {

        if (order == null ||
                order.getId() == null) {

            log.warn(
                    "Skipping invalid order"
            );

            return;
        }


        /*
         * Only PLACED orders should be processed.
         */
        if (!"PLACED".equals(
                order.getStatus()
        )) {

            log.info(
                    "Skipping order {} because its status is {}",
                    order.getId(),
                    order.getStatus()
            );

            return;
        }


        /*
         * IMPORTANT:
         *
         * Prevent the scheduler from attempting to
         * create a second dispatch for the same order.
         */
        if (dispatchRepository.existsByOrderId(
                order.getId()
        )) {

            log.info(
                    "Skipping order {} because it already has a dispatch",
                    order.getId()
            );

            return;
        }


        /*
         * Find the best available rider.
         */
        Rider rider;

        try {

            rider =
                    riderService.findBestRider(
                            order
                    );

        } catch (IllegalArgumentException exception) {

            log.info(
                    "No available rider for order {}. Will retry later.",
                    order.getId()
            );

            return;
        }


        /*
         * Calculate the optimal dispatch time
         * using DispatchDecisionService.
         */
        LocalDateTime optimalDispatchTime =
                dispatchDecisionService
                        .calculateOptimalDispatchTime(
                                order,
                                rider
                        );


        LocalDateTime now =
                LocalDateTime.now();


        /*
         * Preparation is not ready yet.
         * Scheduler will check again later.
         */
        if (optimalDispatchTime.isAfter(now)) {

            log.info(
                    "Order {} waiting until {}. Selected rider: {}",
                    order.getId(),
                    optimalDispatchTime,
                    rider.getId()
            );

            return;
        }


        /*
         * Final duplicate-dispatch check.
         *
         * This protects against another request/scheduler
         * creating the dispatch between our first check
         * and this point.
         */
        if (dispatchRepository.existsByOrderId(
                order.getId()
        )) {

            log.info(
                    "Skipping order {} because a dispatch was created while processing",
                    order.getId()
            );

            return;
        }


        /*
         * Create dispatch.
         */
        Dispatch dispatch =
                Dispatch.builder()
                        .order(order)
                        .rider(rider)
                        .build();


        Dispatch createdDispatch =
                dispatchService.createDispatch(
                        dispatch
                );


        log.info(
                "Successfully dispatched order {} to rider {}. Dispatch ID: {}",
                order.getId(),
                rider.getId(),
                createdDispatch.getId()
        );
    }
}