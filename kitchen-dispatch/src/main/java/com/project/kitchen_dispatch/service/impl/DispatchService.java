package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.repository.DispatchRepository;
import com.project.kitchen_dispatch.service.interfac.IDispatchDecisionService;
import com.project.kitchen_dispatch.service.interfac.IDispatchService;
import com.project.kitchen_dispatch.service.interfac.IRiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DispatchService implements IDispatchService {

    private final DispatchRepository dispatchRepository;

    private final IRiderService riderService;

    private final IDispatchDecisionService dispatchDecisionService;


    /*
     * ============================================================
     * CREATE DISPATCH
     * ============================================================
     */

    @Override
    @Transactional
    public Dispatch createDispatch(
            Dispatch dispatch) {

        if (dispatch == null) {
            throw new RuntimeException(
                    "Dispatch is required"
            );
        }

        if (dispatch.getOrder() == null ||
                dispatch.getOrder().getId() == null) {

            throw new RuntimeException(
                    "Order is required"
            );
        }

        if (dispatch.getRider() == null ||
                dispatch.getRider().getId() == null) {

            throw new RuntimeException(
                    "Rider is required"
            );
        }

        Order order = dispatch.getOrder();

        Rider rider = dispatch.getRider();


        /*
         * Only PLACED orders can be dispatched.
         */

        if (!"PLACED".equals(order.getStatus())) {

            throw new RuntimeException(
                    "Order cannot be dispatched. Current status: "
                            + order.getStatus()
            );
        }


        /*
         * Rider must be active.
         */

        if (!Boolean.TRUE.equals(
                rider.getActive())) {

            throw new RuntimeException(
                    "Rider is inactive"
            );
        }


        /*
         * Rider must be available.
         */

        if (!Boolean.TRUE.equals(
                rider.getAvailable())) {

            throw new RuntimeException(
                    "Rider is unavailable"
            );
        }


        /*
         * Check whether this order already has
         * an active dispatch.
         */

        List<Dispatch> existingDispatches =
                dispatchRepository.findByOrderId(
                        order.getId()
                );

        boolean activeDispatchExists =
                existingDispatches.stream()
                        .anyMatch(existing ->
                                !"DELIVERED".equals(
                                        existing.getStatus()
                                )
                        );

        if (activeDispatchExists) {

            throw new RuntimeException(
                    "Order already has an active dispatch"
            );
        }


        /*
         * Check whether rider is already handling
         * another active order.
         */

        List<Dispatch> riderDispatches =
                dispatchRepository.findByRiderId(
                        rider.getId()
                );

        boolean riderBusy =
                riderDispatches.stream()
                        .anyMatch(existing ->
                                !"DELIVERED".equals(
                                        existing.getStatus()
                                )
                        );

        if (riderBusy) {

            throw new RuntimeException(
                    "Rider is already assigned to another order"
            );
        }


        /*
         * Set dispatch state.
         */

        dispatch.setStatus("ASSIGNED");

        dispatch.setAssignedAt(
                LocalDateTime.now()
        );


        /*
         * ========================================================
         * CALCULATE CUSTOMER ETA
         * ========================================================
         *
         * ETA is calculated at assignment time.
         *
         * This prediction is stored and later compared
         * with the actual delivery time.
         */

        Map<String, Object> eta =
                dispatchDecisionService.calculateETA(
                        order,
                        rider
                );

        Object etaObject =
                eta.get("estimatedDeliveryTime");

        if (etaObject instanceof LocalDateTime) {

            dispatch.setEstimatedDeliveryTime(
                    (LocalDateTime) etaObject
            );
        }


        /*
         * Update order state.
         */

        order.setStatus("ASSIGNED");


        /*
         * Make rider unavailable.
         */

        riderService.markRiderUnavailable(
                rider.getId()
        );


        /*
         * Save dispatch.
         */

        return dispatchRepository.save(
                dispatch
        );
    }


    /*
     * ============================================================
     * AUTOMATIC DISPATCH
     * ============================================================
     */

    @Override
    @Transactional
    public Dispatch automaticallyDispatchOrder(
            Order order) {

        if (order == null ||
                order.getId() == null) {

            throw new RuntimeException(
                    "Order is required"
            );
        }


        /*
         * Only PLACED orders should enter
         * automatic dispatch.
         */

        if (!"PLACED".equals(
                order.getStatus())) {

            return null;
        }


        /*
         * Find best available rider.
         */

        Rider rider;

        try {

            rider =
                    riderService.findBestRider(
                            order
                    );

        } catch (RuntimeException e) {

            /*
             * No rider currently available.
             *
             * Scheduler can retry later.
             */

            return null;
        }


        /*
         * Calculate optimal dispatch time.
         */

        LocalDateTime optimalDispatchTime =
                dispatchDecisionService
                        .calculateOptimalDispatchTime(
                                order,
                                rider
                        );


        /*
         * Rider should wait if food is
         * not ready soon enough.
         */

        if (optimalDispatchTime.isAfter(
                LocalDateTime.now())) {

            return null;
        }


        /*
         * Create dispatch.
         */

        Dispatch dispatch =
                Dispatch.builder()
                        .order(order)
                        .rider(rider)
                        .status("ASSIGNED")
                        .assignedAt(
                                LocalDateTime.now()
                        )
                        .build();

        return createDispatch(
                dispatch
        );
    }


    /*
     * ============================================================
     * PICKUP
     * ============================================================
     */

    @Override
    @Transactional
    public Dispatch markPickedUp(
            Long dispatchId) {

        Dispatch dispatch =
                getDispatchById(
                        dispatchId
                );


        /*
         * Pickup is allowed only from ASSIGNED.
         */

        validateStatus(
                dispatch,
                "ASSIGNED"
        );


        /*
         * Update dispatch.
         */

        dispatch.setStatus(
                "PICKED_UP"
        );

        dispatch.setPickedUpAt(
                LocalDateTime.now()
        );


        /*
         * Update order.
         */

        Order order =
                dispatch.getOrder();

        if (order != null) {

            order.setStatus(
                    "PICKED_UP"
            );
        }


        return dispatchRepository.save(
                dispatch
        );
    }


    /*
     * ============================================================
     * DELIVERY
     * ============================================================
     */

    @Override
    @Transactional
    public Dispatch markDelivered(
            Long dispatchId) {

        Dispatch dispatch =
                getDispatchById(
                        dispatchId
                );


        /*
         * Delivery is allowed only after pickup.
         */

        validateStatus(
                dispatch,
                "PICKED_UP"
        );


        /*
         * Actual delivery time.
         */

        LocalDateTime actualDeliveryTime =
                LocalDateTime.now();

        dispatch.setStatus(
                "DELIVERED"
        );

        dispatch.setDeliveredAt(
                actualDeliveryTime
        );


        /*
         * ========================================================
         * CALCULATE ETA ERROR
         * ========================================================
         *
         * Formula:
         *
         * actual delivery time
         * -
         * estimated delivery time
         *
         * Positive = late
         * Negative = early
         * Zero     = exact
         */

        LocalDateTime estimatedDeliveryTime =
                dispatch.getEstimatedDeliveryTime();

        if (estimatedDeliveryTime != null) {

            long etaErrorMinutes =
                    Duration.between(
                            estimatedDeliveryTime,
                            actualDeliveryTime
                    ).toMinutes();

            dispatch.setEtaErrorMinutes(
                    etaErrorMinutes
            );
        }


        /*
         * Update order status.
         */

        Order order =
                dispatch.getOrder();

        if (order != null) {

            order.setStatus(
                    "DELIVERED"
            );
        }


        /*
         * Rider becomes available again.
         */

        Rider rider =
                dispatch.getRider();

        if (rider != null &&
                rider.getId() != null) {

            riderService.markRiderAvailable(
                    rider.getId()
            );
        }


        /*
         * Save final dispatch.
         */

        return dispatchRepository.save(
                dispatch
        );
    }


    /*
     * ============================================================
     * GET DISPATCH
     * ============================================================
     */

    @Override
    public Dispatch getDispatchById(
            Long id) {

        if (id == null) {

            throw new RuntimeException(
                    "Dispatch id is required"
            );
        }

        return dispatchRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Dispatch not found with id: "
                                        + id
                        )
                );
    }


    /*
     * ============================================================
     * ETA ANALYTICS
     * ============================================================
     *
     * Calculates ETA accuracy using completed deliveries.
     */

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getETAAnalytics() {

        List<Dispatch> dispatches =
                dispatchRepository.findAll();


        /*
         * Only delivered dispatches with an ETA
         * error can be used for analytics.
         */

        List<Dispatch> completedDispatches =
                dispatches.stream()
                        .filter(dispatch ->
                                "DELIVERED".equals(
                                        dispatch.getStatus()
                                )
                        )
                        .filter(dispatch ->
                                dispatch.getEtaErrorMinutes()
                                        != null
                        )
                        .toList();

        int totalDeliveries =
                completedDispatches.size();


        /*
         * No historical data yet.
         */

        if (totalDeliveries == 0) {

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "totalDeliveries",
                    0
            );

            result.put(
                    "averageEtaErrorMinutes",
                    0
            );

            result.put(
                    "averageAbsoluteEtaErrorMinutes",
                    0
            );

            result.put(
                    "onTimeDeliveries",
                    0
            );

            result.put(
                    "earlyDeliveries",
                    0
            );

            result.put(
                    "lateDeliveries",
                    0
            );

            result.put(
                    "onTimePercentage",
                    0
            );

            result.put(
                    "message",
                    "No completed delivery data available yet"
            );

            return result;
        }


        /*
         * ========================================================
         * LATE DELIVERIES
         * ========================================================
         */

        long lateDeliveries =
                completedDispatches.stream()
                        .filter(dispatch ->
                                dispatch
                                        .getEtaErrorMinutes()
                                        > 0
                        )
                        .count();


        /*
         * ========================================================
         * EARLY DELIVERIES
         * ========================================================
         */

        long earlyDeliveries =
                completedDispatches.stream()
                        .filter(dispatch ->
                                dispatch
                                        .getEtaErrorMinutes()
                                        < 0
                        )
                        .count();


        /*
         * ========================================================
         * ON-TIME DELIVERIES
         * ========================================================
         *
         * Within ±1 minute is considered on time.
         */

        long onTimeDeliveries =
                completedDispatches.stream()
                        .filter(dispatch ->
                                Math.abs(
                                        dispatch
                                                .getEtaErrorMinutes()
                                ) <= 1
                        )
                        .count();


        /*
         * ========================================================
         * AVERAGE SIGNED ERROR
         * ========================================================
         *
         * Positive:
         * ETA tends to underestimate delivery time.
         *
         * Negative:
         * ETA tends to overestimate delivery time.
         */

        double averageEtaError =
                completedDispatches.stream()
                        .mapToLong(
                                Dispatch::getEtaErrorMinutes
                        )
                        .average()
                        .orElse(0);


        /*
         * ========================================================
         * AVERAGE ABSOLUTE ERROR
         * ========================================================
         *
         * This is the most useful basic accuracy metric.
         */

        double averageAbsoluteEtaError =
                completedDispatches.stream()
                        .mapToLong(
                                dispatch ->
                                        Math.abs(
                                                dispatch
                                                        .getEtaErrorMinutes()
                                        )
                        )
                        .average()
                        .orElse(0);


        /*
         * ========================================================
         * ON-TIME PERCENTAGE
         * ========================================================
         */

        double onTimePercentage =
                (
                        (double) onTimeDeliveries
                                /
                                totalDeliveries
                )
                        * 100.0;


        /*
         * ========================================================
         * BUILD RESPONSE
         * ========================================================
         */

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "totalDeliveries",
                totalDeliveries
        );

        result.put(
                "averageEtaErrorMinutes",
                round(
                        averageEtaError
                )
        );

        result.put(
                "averageAbsoluteEtaErrorMinutes",
                round(
                        averageAbsoluteEtaError
                )
        );

        result.put(
                "onTimeDeliveries",
                onTimeDeliveries
        );

        result.put(
                "earlyDeliveries",
                earlyDeliveries
        );

        result.put(
                "lateDeliveries",
                lateDeliveries
        );

        result.put(
                "onTimePercentage",
                round(
                        onTimePercentage
                )
        );

        result.put(
                "interpretation",
                getEtaInterpretation(
                        averageEtaError
                )
        );

        return result;
    }


    /*
     * ============================================================
     * ETA INTERPRETATION
     * ============================================================
     */

    private String getEtaInterpretation(
            double averageEtaError) {

        if (averageEtaError > 2) {

            return "ETA tends to underestimate delivery time";

        } else if (averageEtaError < -2) {

            return "ETA tends to overestimate delivery time";

        } else {

            return "ETA is reasonably well calibrated";
        }
    }


    /*
     * ============================================================
     * VALIDATE STATUS
     * ============================================================
     */

    private void validateStatus(
            Dispatch dispatch,
            String expectedStatus) {

        if (!expectedStatus.equals(
                dispatch.getStatus()
        )) {

            throw new RuntimeException(
                    "Invalid dispatch state. Expected "
                            + expectedStatus
                            + " but found "
                            + dispatch.getStatus()
            );
        }
    }


    /*
     * ============================================================
     * ROUND
     * ============================================================
     */

    private double round(
            double value) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }
}