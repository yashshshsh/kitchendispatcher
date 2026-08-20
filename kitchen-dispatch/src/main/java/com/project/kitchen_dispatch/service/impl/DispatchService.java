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
public class DispatchService
        implements IDispatchService {

    private final DispatchRepository dispatchRepository;

    private final IRiderService riderService;

    private final IDispatchDecisionService
            dispatchDecisionService;


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

        Order order =
                dispatch.getOrder();

        Rider rider =
                dispatch.getRider();


        if (!"PLACED".equals(
                order.getStatus())) {

            throw new RuntimeException(
                    "Order cannot be dispatched. Current status: "
                            + order.getStatus()
            );
        }


        if (!Boolean.TRUE.equals(
                rider.getActive())) {

            throw new RuntimeException(
                    "Rider is inactive"
            );
        }


        if (!Boolean.TRUE.equals(
                rider.getAvailable())) {

            throw new RuntimeException(
                    "Rider is unavailable"
            );
        }


        /*
         * Because Dispatch.order is OneToOne,
         * only one dispatch can exist for an order.
         */
        List<Dispatch> existing =
                dispatchRepository
                        .findByOrderId(
                                order.getId()
                        );

        if (!existing.isEmpty()) {

            throw new RuntimeException(
                    "Order already has a dispatch"
            );
        }


        /*
         * Ensure rider isn't already handling
         * another active order.
         */
        boolean riderBusy =
                dispatchRepository
                        .findByRiderId(
                                rider.getId()
                        )
                        .stream()
                        .anyMatch(existingDispatch ->
                                !"DELIVERED".equals(
                                        existingDispatch.getStatus()
                                )
                        );

        if (riderBusy) {

            throw new RuntimeException(
                    "Rider is already assigned to another order"
            );
        }


        LocalDateTime assignedAt =
                LocalDateTime.now();

        dispatch.setStatus(
                "ASSIGNED"
        );

        dispatch.setAssignedAt(
                assignedAt
        );


        /*
         * Calculate and persist ETA.
         */
        Map<String, Object> eta =
                dispatchDecisionService
                        .calculateETA(
                                order,
                                rider
                        );

        Object etaValue =
                eta.get(
                        "estimatedDeliveryTime"
                );

        if (!(etaValue instanceof LocalDateTime)) {

            throw new RuntimeException(
                    "Unable to calculate estimated delivery time"
            );
        }

        LocalDateTime estimatedDeliveryTime =
                (LocalDateTime) etaValue;

        dispatch.setEstimatedDeliveryTime(
                estimatedDeliveryTime
        );

        order.setEstimatedDeliveryTime(
                estimatedDeliveryTime
        );

        order.setStatus(
                "ASSIGNED"
        );


        /*
         * Make rider unavailable before saving
         * the dispatch.
         */
        riderService.markRiderUnavailable(
                rider.getId()
        );


        return dispatchRepository.save(
                dispatch
        );
    }


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

        if (!"PLACED".equals(
                order.getStatus())) {

            return null;
        }


        Rider rider;

        try {

            rider =
                    riderService.findBestRider(
                            order
                    );

        } catch (RuntimeException e) {

            return null;
        }


        LocalDateTime optimalDispatchTime =
                dispatchDecisionService
                        .calculateOptimalDispatchTime(
                                order,
                                rider
                        );

        if (optimalDispatchTime.isAfter(
                LocalDateTime.now()
        )) {

            return null;
        }


        Dispatch dispatch =
                Dispatch.builder()
                        .order(order)
                        .rider(rider)
                        .build();

        return createDispatch(
                dispatch
        );
    }


    @Override
    @Transactional
    public Dispatch markPickedUp(
            Long dispatchId) {

        Dispatch dispatch =
                getDispatchById(
                        dispatchId
                );

        validateStatus(
                dispatch,
                "ASSIGNED"
        );

        LocalDateTime pickupTime =
                LocalDateTime.now();

        dispatch.setStatus(
                "PICKED_UP"
        );

        dispatch.setPickedUpAt(
                pickupTime
        );

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


    @Override
    @Transactional
    public Dispatch markDelivered(
            Long dispatchId) {

        Dispatch dispatch =
                getDispatchById(
                        dispatchId
                );

        validateStatus(
                dispatch,
                "PICKED_UP"
        );

        LocalDateTime actualDeliveryTime =
                LocalDateTime.now();

        dispatch.setStatus(
                "DELIVERED"
        );

        dispatch.setDeliveredAt(
                actualDeliveryTime
        );


        LocalDateTime estimatedDeliveryTime =
                dispatch.getEstimatedDeliveryTime();

        if (estimatedDeliveryTime != null) {

            long error =
                    Duration.between(
                            estimatedDeliveryTime,
                            actualDeliveryTime
                    ).toMinutes();

            dispatch.setEtaErrorMinutes(
                    error
            );
        }


        Order order =
                dispatch.getOrder();

        if (order != null) {

            order.setStatus(
                    "DELIVERED"
            );
        }


        Rider rider =
                dispatch.getRider();

        if (rider != null &&
                rider.getId() != null) {

            riderService.markRiderAvailable(
                    rider.getId()
            );
        }

        return dispatchRepository.save(
                dispatch
        );
    }


    @Override
    @Transactional(readOnly = true)
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


    @Override
    @Transactional(readOnly = true)
    public Map<String, Object>
    getETAAnalytics() {

        List<Dispatch> completed =
                dispatchRepository
                        .findAll()
                        .stream()
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


        int total =
                completed.size();


        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "totalDeliveries",
                total
        );


        if (total == 0) {

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
         * IMPORTANT:
         *
         * These three conditions are mutually exclusive.
         *
         * <= -2   EARLY
         * -1..+1  ON TIME
         * >= +2   LATE
         */

        long early =
                completed.stream()
                        .filter(dispatch ->
                                dispatch
                                        .getEtaErrorMinutes()
                                        < -1
                        )
                        .count();


        long onTime =
                completed.stream()
                        .filter(dispatch ->
                                Math.abs(
                                        dispatch
                                                .getEtaErrorMinutes()
                                ) <= 1
                        )
                        .count();


        long late =
                completed.stream()
                        .filter(dispatch ->
                                dispatch
                                        .getEtaErrorMinutes()
                                        > 1
                        )
                        .count();


        double averageError =
                completed.stream()
                        .mapToLong(
                                Dispatch::getEtaErrorMinutes
                        )
                        .average()
                        .orElse(0);


        double averageAbsoluteError =
                completed.stream()
                        .mapToLong(
                                dispatch ->
                                        Math.abs(
                                                dispatch
                                                        .getEtaErrorMinutes()
                                        )
                        )
                        .average()
                        .orElse(0);


        double onTimePercentage =
                (
                        (double) onTime /
                                total
                ) * 100.0;


        result.put(
                "averageEtaErrorMinutes",
                round(averageError)
        );

        result.put(
                "averageAbsoluteEtaErrorMinutes",
                round(averageAbsoluteError)
        );

        result.put(
                "onTimeDeliveries",
                onTime
        );

        result.put(
                "earlyDeliveries",
                early
        );

        result.put(
                "lateDeliveries",
                late
        );

        result.put(
                "onTimePercentage",
                round(onTimePercentage)
        );

        result.put(
                "interpretation",
                getEtaInterpretation(
                        averageError
                )
        );

        return result;
    }


    private String getEtaInterpretation(
            double averageError) {

        if (averageError > 2) {

            return "ETA tends to underestimate delivery time";

        } else if (averageError < -2) {

            return "ETA tends to overestimate delivery time";

        } else {

            return "ETA is reasonably well calibrated";
        }
    }


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


    private double round(
            double value) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }
}