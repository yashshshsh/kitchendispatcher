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

    private static final double
            AVERAGE_RIDER_SPEED_KMH = 30.0;

    /*
     * Rider -> Kitchen
     */
    private static final double
            RIDER_TO_KITCHEN_WEIGHT = 0.40;

    /*
     * Kitchen -> Customer
     */
    private static final double
            KITCHEN_TO_CUSTOMER_WEIGHT = 0.40;

    /*
     * Total travel time
     */
    private static final double
            TRAVEL_TIME_WEIGHT = 0.20;

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
                                "No available rider found"
                        )
                );
    }

    @Override
    public Rider findBestRider(Order order) {

        validateOrder(order);

        Kitchen kitchen =
                order.getKitchen();

        List<Rider> riders =
                getValidAvailableRiders();

        if (riders.isEmpty()) {

            throw new RuntimeException(
                    "No available riders found"
            );
        }

        /*
         * Calculate maximum values first.
         *
         * These are required for normalization.
         */
        double maxRiderToKitchenDistance =
                riders.stream()
                        .mapToDouble(rider ->
                                calculateDistance(
                                        rider.getLatitude(),
                                        rider.getLongitude(),
                                        kitchen.getLatitude(),
                                        kitchen.getLongitude()
                                )
                        )
                        .max()
                        .orElse(1.0);

        double kitchenToCustomerDistance =
                calculateDistance(
                        kitchen.getLatitude(),
                        kitchen.getLongitude(),
                        order.getDeliveryLatitude(),
                        order.getDeliveryLongitude()
                );

        /*
         * Same kitchen/customer distance for every rider,
         * because every rider delivers the same order.
         *
         * We still include it in the scoring model because
         * it represents the actual delivery burden of the order.
         */
        double normalizedKitchenToCustomerDistance =
                kitchenToCustomerDistance == 0
                        ? 0
                        : 1;

        /*
         * Calculate maximum total travel time.
         */
        double maxTotalTravelTime =
                riders.stream()
                        .mapToDouble(rider -> {

                            double riderToKitchen =
                                    calculateDistance(
                                            rider.getLatitude(),
                                            rider.getLongitude(),
                                            kitchen.getLatitude(),
                                            kitchen.getLongitude()
                                    );

                            return calculateTravelTime(
                                    riderToKitchen +
                                            kitchenToCustomerDistance
                            );
                        })
                        .max()
                        .orElse(1.0);

        /*
         * Select rider with lowest score.
         */
        return riders.stream()
                .min(
                        Comparator.comparingDouble(
                                rider -> {

                                    double riderToKitchenDistance =
                                            calculateDistance(
                                                    rider.getLatitude(),
                                                    rider.getLongitude(),
                                                    kitchen.getLatitude(),
                                                    kitchen.getLongitude()
                                            );

                                    double totalDistance =
                                            riderToKitchenDistance
                                                    +
                                                    kitchenToCustomerDistance;

                                    double totalTravelTime =
                                            calculateTravelTime(
                                                    totalDistance
                                            );

                                    /*
                                     * Normalize rider -> kitchen.
                                     */
                                    double normalizedRiderToKitchen =
                                            maxRiderToKitchenDistance == 0
                                                    ? 0
                                                    :
                                                    riderToKitchenDistance
                                                            /
                                                            maxRiderToKitchenDistance;

                                    /*
                                     * Normalize delivery distance.
                                     *
                                     * Since all riders have the same
                                     * kitchen -> customer distance,
                                     * this component is equal for all
                                     * riders and therefore doesn't
                                     * influence the ranking.
                                     */
                                    double normalizedKitchenToCustomer =
                                            normalizedKitchenToCustomerDistance;

                                    /*
                                     * Normalize total travel time.
                                     */
                                    double normalizedTravelTime =
                                            maxTotalTravelTime == 0
                                                    ? 0
                                                    :
                                                    totalTravelTime
                                                            /
                                                            maxTotalTravelTime;

                                    /*
                                     * Final score.
                                     *
                                     * Lower = better.
                                     */
                                    return
                                            (RIDER_TO_KITCHEN_WEIGHT
                                                    *
                                                    normalizedRiderToKitchen)

                                                    +

                                                    (KITCHEN_TO_CUSTOMER_WEIGHT
                                                            *
                                                            normalizedKitchenToCustomer)

                                                    +

                                                    (TRAVEL_TIME_WEIGHT
                                                            *
                                                            normalizedTravelTime);
                                }
                        )
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Unable to select best rider"
                        ));
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

    private List<Rider> getValidAvailableRiders() {

        return riderRepository
                .findByAvailableTrueAndActiveTrue()
                .stream()
                .filter(rider ->
                        rider.getLatitude() != null &&
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

    @Override
    public Map<String, Object> evaluateRiders(
            Order order) {

        validateOrder(order);

        Kitchen kitchen =
                order.getKitchen();

        List<Rider> riders =
                getValidAvailableRiders();

        if (riders.isEmpty()) {

            throw new RuntimeException(
                    "No available riders found"
            );
        }

        List<Map<String, Object>> evaluations =
                riders.stream()
                        .map(rider ->
                                evaluateRider(
                                        order,
                                        rider
                                )
                        )
                        .toList();

        Map<String, Object> result =
                new LinkedHashMap<>();

        Rider bestRider =
                findBestRider(order);

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

    private Map<String, Object> evaluateRider(
            Order order,
            Rider rider) {

        Kitchen kitchen =
                order.getKitchen();

        double riderToKitchenDistance =
                calculateDistance(
                        rider.getLatitude(),
                        rider.getLongitude(),
                        kitchen.getLatitude(),
                        kitchen.getLongitude()
                );

        double kitchenToCustomerDistance =
                calculateDistance(
                        kitchen.getLatitude(),
                        kitchen.getLongitude(),
                        order.getDeliveryLatitude(),
                        order.getDeliveryLongitude()
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

        Map<String, Object> evaluation =
                new LinkedHashMap<>();

        evaluation.put(
                "riderId",
                rider.getId()
        );

        evaluation.put(
                "riderName",
                rider.getName()
        );

        evaluation.put(
                "riderToKitchenDistanceKm",
                round(riderToKitchenDistance)
        );

        evaluation.put(
                "kitchenToCustomerDistanceKm",
                round(kitchenToCustomerDistance)
        );

        evaluation.put(
                "totalDistanceKm",
                round(totalDistance)
        );

        evaluation.put(
                "riderToKitchenTimeMinutes",
                Math.ceil(riderToKitchenTime)
        );

        evaluation.put(
                "kitchenToCustomerTimeMinutes",
                Math.ceil(kitchenToCustomerTime)
        );

        evaluation.put(
                "totalTravelTimeMinutes",
                Math.ceil(totalTravelTime)
        );

        return evaluation;
    }

    private double calculateTravelTime(
            double distanceKm) {

        double travelTimeHours =
                distanceKm /
                        AVERAGE_RIDER_SPEED_KMH;

        return travelTimeHours * 60;
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

                                Math.sin(
                                        lonDistance / 2
                                )
                                *
                                Math.sin(
                                        lonDistance / 2
                                );

        double c =
                2 *
                        Math.atan2(
                                Math.sqrt(a),
                                Math.sqrt(1 - a)
                        );

        return EARTH_RADIUS_KM * c;
    }
}