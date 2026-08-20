package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.repository.DispatchRepository;
import com.project.kitchen_dispatch.repository.RiderRepository;
import com.project.kitchen_dispatch.service.interfac.IRiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RiderService implements IRiderService {

    private static final double AVERAGE_RIDER_SPEED_KMH = 30.0;

    private static final double RIDER_TO_KITCHEN_WEIGHT = 0.70;

    private static final double TOTAL_TRAVEL_TIME_WEIGHT = 0.30;

    private final RiderRepository riderRepository;

    private final DispatchRepository dispatchRepository;


    @Override
    public Rider createRider(Rider rider) {

        if (rider == null) {
            throw new RuntimeException(
                    "Rider is required"
            );
        }

        if (rider.getName() == null ||
                rider.getName().isBlank()) {

            throw new RuntimeException(
                    "Rider name is required"
            );
        }

        if (rider.getPhone() == null ||
                rider.getPhone().isBlank()) {

            throw new RuntimeException(
                    "Rider phone is required"
            );
        }

        validateCoordinates(
                rider.getLatitude(),
                rider.getLongitude(),
                "Rider"
        );

        if (rider.getAvailable() == null) {
            rider.setAvailable(true);
        }

        if (rider.getActive() == null) {
            rider.setActive(true);
        }

        return riderRepository.save(rider);
    }


    @Override
    public Rider getRiderById(Long id) {

        if (id == null) {
            throw new RuntimeException(
                    "Rider id is required"
            );
        }

        return riderRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Rider not found with id: " + id
                        )
                );
    }


    @Override
    public List<Rider> getAvailableRiders() {

        return riderRepository
                .findByAvailableTrueAndActiveTrue()
                .stream()
                .filter(rider ->
                        rider.getLatitude() != null &&
                                rider.getLongitude() != null
                )
                .toList();
    }


    @Override
    public Rider findNearestRider(
            Double kitchenLatitude,
            Double kitchenLongitude) {

        validateCoordinates(
                kitchenLatitude,
                kitchenLongitude,
                "Kitchen"
        );

        List<Rider> riders =
                getAvailableRiders();

        if (riders.isEmpty()) {
            throw new RuntimeException(
                    "No available riders found"
            );
        }

        return riders.stream()
                .min(
                        Comparator.comparingDouble(
                                rider ->
                                        calculateDistance(
                                                kitchenLatitude,
                                                kitchenLongitude,
                                                rider.getLatitude(),
                                                rider.getLongitude()
                                        )
                        )
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Unable to find nearest rider"
                        )
                );
    }


    @Override
    @Transactional
    public Rider findBestRider(Order order) {

        validateOrder(order);

        /*
         * IMPORTANT:
         *
         * Actual dispatch selection uses the
         * pessimistic-lock query.
         */
        List<Rider> riders =
                riderRepository
                        .findAvailableRidersForDispatch();

        if (riders.isEmpty()) {
            throw new RuntimeException(
                    "No available riders found"
            );
        }

        /*
         * Extra validation.
         */
        riders = riders.stream()
                .filter(rider ->
                        rider.getLatitude() != null &&
                                rider.getLongitude() != null
                )
                .toList();

        if (riders.isEmpty()) {
            throw new RuntimeException(
                    "No available riders with valid location found"
            );
        }

        double maxDistance =
                riders.stream()
                        .mapToDouble(
                                rider ->
                                        calculateRiderToKitchenDistance(
                                                order,
                                                rider
                                        )
                        )
                        .max()
                        .orElse(1.0);

        double maxTravelTime =
                riders.stream()
                        .mapToDouble(
                                rider ->
                                        calculateTotalTravelTime(
                                                order,
                                                rider
                                        )
                        )
                        .max()
                        .orElse(1.0);

        return riders.stream()
                .min(
                        Comparator.comparingDouble(
                                rider ->
                                        calculateScore(
                                                order,
                                                rider,
                                                maxDistance,
                                                maxTravelTime
                                        )
                        )
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Unable to select best rider"
                        )
                );
    }


    @Override
    public Map<String, Object> evaluateRiders(
            Order order) {

        validateOrder(order);

        List<Rider> riders =
                getAvailableRiders();

        if (riders.isEmpty()) {
            throw new RuntimeException(
                    "No available riders found"
            );
        }

        double maxDistance =
                riders.stream()
                        .mapToDouble(
                                rider ->
                                        calculateRiderToKitchenDistance(
                                                order,
                                                rider
                                        )
                        )
                        .max()
                        .orElse(1.0);

        double maxTravelTime =
                riders.stream()
                        .mapToDouble(
                                rider ->
                                        calculateTotalTravelTime(
                                                order,
                                                rider
                                        )
                        )
                        .max()
                        .orElse(1.0);

        List<Map<String, Object>> evaluations =
                riders.stream()
                        .map(rider ->
                                evaluateRider(
                                        order,
                                        rider,
                                        maxDistance,
                                        maxTravelTime
                                )
                        )
                        .sorted(
                                Comparator.comparingDouble(
                                        item ->
                                                ((Number)
                                                        item.get("score")
                                                ).doubleValue()
                                )
                        )
                        .toList();

        Rider bestRider =
                findBestRider(order);

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "orderId",
                order.getId()
        );

        result.put(
                "availableRiderCount",
                riders.size()
        );

        result.put(
                "riders",
                evaluations
        );

        result.put(
                "selectedRiderId",
                bestRider.getId()
        );

        result.put(
                "selectionReason",
                "Rider with the lowest dispatch score was selected"
        );

        return result;
    }


    @Override
    @Transactional
    public Rider markRiderUnavailable(
            Long riderId) {

        Rider rider =
                getRiderById(riderId);

        if (!Boolean.TRUE.equals(
                rider.getAvailable())) {

            throw new RuntimeException(
                    "Rider is already unavailable"
            );
        }

        rider.setAvailable(false);

        return riderRepository.save(rider);
    }


    @Override
    @Transactional
    public Rider markRiderAvailable(
            Long riderId) {

        Rider rider =
                getRiderById(riderId);

        if (!Boolean.TRUE.equals(
                rider.getActive())) {

            throw new RuntimeException(
                    "Cannot make inactive rider available"
            );
        }

        rider.setAvailable(true);

        return riderRepository.save(rider);
    }


    @Override
    @Transactional(readOnly = true)
    public Rider findAssignedRiderForOrder(
            Long orderId) {

        if (orderId == null) {
            throw new RuntimeException(
                    "Order id is required"
            );
        }

        List<Dispatch> dispatches =
                dispatchRepository.findByOrderId(
                        orderId
                );

        if (dispatches.isEmpty()) {
            throw new RuntimeException(
                    "No dispatch found for order: "
                            + orderId
            );
        }

        Dispatch dispatch =
                dispatches.stream()
                        .filter(d ->
                                d.getRider() != null
                        )
                        .max(
                                Comparator.comparing(
                                        Dispatch::getId
                                )
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "No rider assigned to order: "
                                                + orderId
                                )
                        );

        return dispatch.getRider();
    }


    private Map<String, Object> evaluateRider(
            Order order,
            Rider rider,
            double maxDistance,
            double maxTravelTime) {

        double riderToKitchenDistance =
                calculateRiderToKitchenDistance(
                        order,
                        rider
                );

        double kitchenToCustomerDistance =
                calculateKitchenToCustomerDistance(
                        order
                );

        double totalDistance =
                riderToKitchenDistance
                        +
                        kitchenToCustomerDistance;

        double riderToKitchenTime =
                calculateTravelTime(
                        riderToKitchenDistance
                );

        double kitchenToCustomerTime =
                calculateTravelTime(
                        kitchenToCustomerDistance
                );

        double totalTravelTime =
                riderToKitchenTime
                        +
                        kitchenToCustomerTime;

        double score =
                calculateScore(
                        riderToKitchenDistance,
                        totalTravelTime,
                        maxDistance,
                        maxTravelTime
                );

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "riderId",
                rider.getId()
        );

        result.put(
                "riderName",
                rider.getName()
        );

        result.put(
                "riderToKitchenDistanceKm",
                round(riderToKitchenDistance)
        );

        result.put(
                "kitchenToCustomerDistanceKm",
                round(kitchenToCustomerDistance)
        );

        result.put(
                "totalDistanceKm",
                round(totalDistance)
        );

        result.put(
                "riderToKitchenTimeMinutes",
                round(riderToKitchenTime)
        );

        result.put(
                "kitchenToCustomerTimeMinutes",
                round(kitchenToCustomerTime)
        );

        result.put(
                "totalTravelTimeMinutes",
                round(totalTravelTime)
        );

        result.put(
                "score",
                round(score)
        );

        return result;
    }


    private double calculateScore(
            Order order,
            Rider rider,
            double maxDistance,
            double maxTravelTime) {

        return calculateScore(
                calculateRiderToKitchenDistance(
                        order,
                        rider
                ),
                calculateTotalTravelTime(
                        order,
                        rider
                ),
                maxDistance,
                maxTravelTime
        );
    }


    private double calculateScore(
            double riderToKitchenDistance,
            double totalTravelTime,
            double maxDistance,
            double maxTravelTime) {

        double normalizedDistance =
                maxDistance == 0
                        ? 0
                        : riderToKitchenDistance /
                        maxDistance;

        double normalizedTravelTime =
                maxTravelTime == 0
                        ? 0
                        : totalTravelTime /
                        maxTravelTime;

        return
                RIDER_TO_KITCHEN_WEIGHT
                        *
                        normalizedDistance
                        +
                        TOTAL_TRAVEL_TIME_WEIGHT
                                *
                                normalizedTravelTime;
    }


    private double calculateRiderToKitchenDistance(
            Order order,
            Rider rider) {

        Kitchen kitchen =
                order.getKitchen();

        return calculateDistance(
                rider.getLatitude(),
                rider.getLongitude(),
                kitchen.getLatitude(),
                kitchen.getLongitude()
        );
    }


    private double calculateKitchenToCustomerDistance(
            Order order) {

        Kitchen kitchen =
                order.getKitchen();

        return calculateDistance(
                kitchen.getLatitude(),
                kitchen.getLongitude(),
                order.getDeliveryLatitude(),
                order.getDeliveryLongitude()
        );
    }


    private double calculateTotalTravelTime(
            Order order,
            Rider rider) {

        double riderToKitchenDistance =
                calculateRiderToKitchenDistance(
                        order,
                        rider
                );

        double kitchenToCustomerDistance =
                calculateKitchenToCustomerDistance(
                        order
                );

        return calculateTravelTime(
                riderToKitchenDistance
                        +
                        kitchenToCustomerDistance
        );
    }


    private double calculateTravelTime(
            double distanceKm) {

        if (distanceKm <= 0) {
            return 0;
        }

        return (
                distanceKm /
                        AVERAGE_RIDER_SPEED_KMH
        ) * 60;
    }


    private void validateOrder(Order order) {

        if (order == null) {
            throw new RuntimeException(
                    "Order is required"
            );
        }

        if (order.getKitchen() == null) {
            throw new RuntimeException(
                    "Order kitchen is required"
            );
        }

        Kitchen kitchen =
                order.getKitchen();

        if (kitchen.getLatitude() == null ||
                kitchen.getLongitude() == null) {

            throw new RuntimeException(
                    "Kitchen location is required"
            );
        }

        if (order.getDeliveryLatitude() == null ||
                order.getDeliveryLongitude() == null) {

            throw new RuntimeException(
                    "Order delivery location is required"
            );
        }
    }


    private void validateCoordinates(
            Double latitude,
            Double longitude,
            String entity) {

        if (latitude == null ||
                longitude == null) {

            throw new RuntimeException(
                    entity + " location is required"
            );
        }

        if (latitude < -90 ||
                latitude > 90) {

            throw new RuntimeException(
                    entity + " latitude must be between -90 and 90"
            );
        }

        if (longitude < -180 ||
                longitude > 180) {

            throw new RuntimeException(
                    entity + " longitude must be between -180 and 180"
            );
        }
    }


    private double calculateDistance(
            double lat1,
            double lon1,
            double lat2,
            double lon2) {

        final double EARTH_RADIUS_KM =
                6371.0;

        double latDistance =
                Math.toRadians(
                        lat2 - lat1
                );

        double lonDistance =
                Math.toRadians(
                        lon2 - lon1
                );

        double a =
                Math.sin(latDistance / 2)
                        *
                        Math.sin(latDistance / 2)
                        +
                        Math.cos(
                                Math.toRadians(lat1)
                        )
                                *
                                Math.cos(
                                        Math.toRadians(lat2)
                                )
                                *
                                Math.sin(lonDistance / 2)
                                *
                                Math.sin(lonDistance / 2);

        double c =
                2 *
                        Math.atan2(
                                Math.sqrt(a),
                                Math.sqrt(1 - a)
                        );

        return EARTH_RADIUS_KM * c;
    }


    private double round(double value) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }
}