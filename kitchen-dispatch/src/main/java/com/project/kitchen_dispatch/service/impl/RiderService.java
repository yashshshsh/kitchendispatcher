package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
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

    /*
     * Weight used for rider selection.
     *
     * Rider -> Kitchen is the only part of the route
     * that differs between riders for the same order.
     */
    private static final double RIDER_TO_KITCHEN_WEIGHT = 0.70;

    /*
     * Total travel time is also considered.
     */
    private static final double TOTAL_TRAVEL_TIME_WEIGHT = 0.30;

    private final RiderRepository riderRepository;

    @Override
    public Rider createRider(Rider rider) {

        if (rider == null) {
            throw new RuntimeException(
                    "Rider is required"
            );
        }

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
                                "Rider not found with id: "
                                        + id
                        )
                );
    }

    @Override
    public List<Rider> getAvailableRiders() {

        return riderRepository
                .findByAvailableTrueAndActiveTrue();
    }

    @Override
    public Rider findNearestRider(
            Double kitchenLatitude,
            Double kitchenLongitude) {

        if (kitchenLatitude == null ||
                kitchenLongitude == null) {

            throw new RuntimeException(
                    "Kitchen location is required"
            );
        }

        List<Rider> riders =
                getValidAvailableRiders();

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
    public Rider findBestRider(Order order) {

        validateOrder(order);

        List<Rider> riders =
                getValidAvailableRiders();

        if (riders.isEmpty()) {
            throw new RuntimeException(
                    "No available riders found"
            );
        }

        /*
         * Calculate the maximum values for normalization.
         */
        double maxDistance =
                riders.stream()
                        .mapToDouble(rider ->
                                calculateRiderToKitchenDistance(
                                        order,
                                        rider
                                )
                        )
                        .max()
                        .orElse(1.0);

        double maxTravelTime =
                riders.stream()
                        .mapToDouble(rider ->
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
                getValidAvailableRiders();

        if (riders.isEmpty()) {
            throw new RuntimeException(
                    "No available riders found"
            );
        }

        double maxDistance =
                riders.stream()
                        .mapToDouble(rider ->
                                calculateRiderToKitchenDistance(
                                        order,
                                        rider
                                )
                        )
                        .max()
                        .orElse(1.0);

        double maxTravelTime =
                riders.stream()
                        .mapToDouble(rider ->
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
                                                ((Number) item.get(
                                                        "score"
                                                )).doubleValue()
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

        double riderToKitchenDistance =
                calculateRiderToKitchenDistance(
                        order,
                        rider
                );

        double totalTravelTime =
                calculateTotalTravelTime(
                        order,
                        rider
                );

        return calculateScore(
                riderToKitchenDistance,
                totalTravelTime,
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
                        : riderToKitchenDistance
                        / maxDistance;

        double normalizedTravelTime =
                maxTravelTime == 0
                        ? 0
                        : totalTravelTime
                        / maxTravelTime;

        /*
         * Lower score = better rider.
         */
        return
                (RIDER_TO_KITCHEN_WEIGHT
                        * normalizedDistance)

                        +

                        (TOTAL_TRAVEL_TIME_WEIGHT
                                * normalizedTravelTime);
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

        double travelTimeHours =
                distanceKm /
                        AVERAGE_RIDER_SPEED_KMH;

        return travelTimeHours * 60;
    }

    private List<Rider> getValidAvailableRiders() {

        return riderRepository
                .findByAvailableTrueAndActiveTrue()
                .stream()
                .filter(rider ->
                        rider.getLatitude() != null
                                &&
                                rider.getLongitude() != null
                )
                .toList();
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

        if (order.getKitchen().getLatitude() == null ||
                order.getKitchen().getLongitude() == null) {

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