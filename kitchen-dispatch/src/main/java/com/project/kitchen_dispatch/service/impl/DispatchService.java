package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.repository.DispatchRepository;
import com.project.kitchen_dispatch.repository.RiderRepository;
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

    private final RiderRepository riderRepository;

    private final IRiderService riderService;

    private final IDispatchDecisionService
            dispatchDecisionService;


    @Override
    @Transactional
    public Dispatch createDispatch(
            Dispatch dispatch) {

        if (dispatch == null) {
            throw new IllegalArgumentException(
                    "Dispatch is required"
            );
        }

        if (dispatch.getOrder() == null ||
                dispatch.getOrder().getId() == null) {

            throw new IllegalArgumentException(
                    "Order is required"
            );
        }

        if (dispatch.getRider() == null ||
                dispatch.getRider().getId() == null) {

            throw new IllegalArgumentException(
                    "Rider is required"
            );
        }

        Order order =
                dispatch.getOrder();

        Long riderId =
                dispatch.getRider().getId();


        if (!"PLACED".equals(
                order.getStatus())) {

            throw new IllegalStateException(
                    "Order cannot be dispatched. Current status: "
                            + order.getStatus()
            );
        }


        /*
         * Lock the rider before checking availability.
         *
         * This prevents two concurrent dispatch requests
         * from assigning the same rider.
         */
        Rider rider =
                riderRepository
                        .findByIdForUpdate(riderId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Rider not found with id: "
                                                + riderId
                                )
                        );


        if (!Boolean.TRUE.equals(
                rider.getActive())) {

            throw new IllegalStateException(
                    "Rider is inactive"
            );
        }


        if (!Boolean.TRUE.equals(
                rider.getAvailable())) {

            throw new IllegalStateException(
                    "Rider is unavailable"
            );
        }


        List<Dispatch> existing =
                dispatchRepository
                        .findByOrderId(
                                order.getId()
                        );

        if (!existing.isEmpty()) {

            throw new IllegalStateException(
                    "Order already has a dispatch"
            );
        }


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

            throw new IllegalStateException(
                    "Rider is already assigned to another order"
            );
        }


        /*
         * ============================================================
         * HISTORICAL DISTANCE SNAPSHOT
         * ============================================================
         *
         * IMPORTANT:
         *
         * These distances are calculated NOW, at dispatch time.
         *
         * We do not calculate them later when the delivery is
         * completed because the rider's current location may have
         * changed.
         *
         * These values will later become ML training features.
         */

        Kitchen kitchen =
                order.getKitchen();

        if (kitchen == null) {

            throw new IllegalStateException(
                    "Kitchen is required for dispatch"
            );
        }


        validateCoordinates(
                rider.getLatitude(),
                rider.getLongitude(),
                "Rider"
        );

        validateCoordinates(
                kitchen.getLatitude(),
                kitchen.getLongitude(),
                "Kitchen"
        );

        validateCoordinates(
                order.getDeliveryLatitude(),
                order.getDeliveryLongitude(),
                "Customer"
        );


        double riderToKitchenDistance =
                calculateDistanceKm(
                        rider.getLatitude(),
                        rider.getLongitude(),
                        kitchen.getLatitude(),
                        kitchen.getLongitude()
                );


        double kitchenToCustomerDistance =
                calculateDistanceKm(
                        kitchen.getLatitude(),
                        kitchen.getLongitude(),
                        order.getDeliveryLatitude(),
                        order.getDeliveryLongitude()
                );


        double totalDistance =
                riderToKitchenDistance
                        + kitchenToCustomerDistance;


        /*
         * Save the historical feature snapshot.
         */
        dispatch.setRiderToKitchenDistanceKm(
                roundDistance(riderToKitchenDistance)
        );

        dispatch.setKitchenToCustomerDistanceKm(
                roundDistance(kitchenToCustomerDistance)
        );

        dispatch.setTotalDistanceKm(
                roundDistance(totalDistance)
        );


        LocalDateTime assignedAt =
                LocalDateTime.now();

        dispatch.setStatus(
                "ASSIGNED"
        );

        dispatch.setAssignedAt(
                assignedAt
        );


        /*
         * Calculate ETA before persisting dispatch.
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

            throw new IllegalStateException(
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
         * Rider is already locked in this transaction.
         * Update it directly instead of performing
         * another database lookup.
         */
        rider.setAvailable(false);


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

            throw new IllegalArgumentException(
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

        } catch (IllegalArgumentException e) {

            /*
             * No rider is currently available.
             * Scheduler can retry later.
             */
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

            throw new IllegalArgumentException(
                    "Dispatch id is required"
            );
        }

        return dispatchRepository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
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


    /*
     * ============================================================
     * HAVERSINE DISTANCE
     * ============================================================
     *
     * Calculates the great-circle distance between two
     * latitude/longitude coordinates.
     *
     * Result is returned in kilometers.
     */
    private double calculateDistanceKm(
            Double latitude1,
            Double longitude1,
            Double latitude2,
            Double longitude2) {

        double earthRadiusKm =
                6371.0;

        double lat1 =
                Math.toRadians(latitude1);

        double lat2 =
                Math.toRadians(latitude2);

        double deltaLatitude =
                Math.toRadians(
                        latitude2 - latitude1
                );

        double deltaLongitude =
                Math.toRadians(
                        longitude2 - longitude1
                );


        double a =
                Math.sin(
                        deltaLatitude / 2
                )
                        * Math.sin(
                        deltaLatitude / 2
                )
                        +
                        Math.cos(lat1)
                                * Math.cos(lat2)
                                * Math.sin(
                                deltaLongitude / 2
                        )
                                * Math.sin(
                                deltaLongitude / 2
                        );


        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );


        return earthRadiusKm * c;
    }


    private void validateCoordinates(
            Double latitude,
            Double longitude,
            String locationName) {

        if (latitude == null ||
                longitude == null) {

            throw new IllegalStateException(
                    locationName
                            + " coordinates are required for dispatch"
            );
        }

        if (latitude < -90 ||
                latitude > 90) {

            throw new IllegalStateException(
                    locationName
                            + " latitude must be between -90 and 90"
            );
        }

        if (longitude < -180 ||
                longitude > 180) {

            throw new IllegalStateException(
                    locationName
                            + " longitude must be between -180 and 180"
            );
        }
    }


    private double roundDistance(
            double value) {

        return Math.round(
                value * 100.0
        ) / 100.0;
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

            throw new IllegalStateException(
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