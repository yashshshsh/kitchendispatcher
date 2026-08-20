package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.service.interfac.IDispatchDecisionService;
import com.project.kitchen_dispatch.service.interfac.IPreparationTimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DispatchDecisionService
        implements IDispatchDecisionService {

    /*
     * Average rider speed used for the current
     * deterministic ETA calculation.
     *
     * Later this can be replaced by an ML model.
     */
    private static final double AVERAGE_RIDER_SPEED_KMH = 30.0;

    private final IPreparationTimeService preparationTimeService;

    /*
     * ============================================================
     * DISPATCH DECISION
     * ============================================================
     */

    @Override
    public Map<String, Object> calculateDispatchDecision(
            Order order,
            Rider rider) {

        validateOrder(order);
        validateRider(rider);

        Integer preparationTime =
                getPreparationTime(order);

        LocalDateTime foodReadyTime =
                order.getCreatedAt()
                        .plusMinutes(preparationTime);

        double riderToKitchenDistance =
                calculateDistance(
                        rider.getLatitude(),
                        rider.getLongitude(),
                        order.getKitchen().getLatitude(),
                        order.getKitchen().getLongitude()
                );

        int riderTravelTime =
                calculateTravelTimeMinutes(
                        riderToKitchenDistance
                );

        LocalDateTime optimalDispatchTime =
                foodReadyTime.minusMinutes(
                        riderTravelTime
                );

        LocalDateTime now =
                LocalDateTime.now();

        boolean dispatchNow =
                !optimalDispatchTime.isAfter(now);

        LocalDateTime recommendedDispatchTime =
                dispatchNow
                        ? now
                        : optimalDispatchTime;

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "orderId",
                order.getId()
        );

        result.put(
                "riderId",
                rider.getId()
        );

        result.put(
                "orderCreatedAt",
                order.getCreatedAt()
        );

        result.put(
                "preparationTimeMinutes",
                preparationTime
        );

        result.put(
                "foodReadyTime",
                foodReadyTime
        );

        result.put(
                "riderToKitchenDistanceKm",
                round(riderToKitchenDistance)
        );

        result.put(
                "riderTravelTimeMinutes",
                riderTravelTime
        );

        result.put(
                "optimalDispatchTime",
                optimalDispatchTime
        );

        result.put(
                "recommendedDispatchTime",
                recommendedDispatchTime
        );

        result.put(
                "dispatchNow",
                dispatchNow
        );

        result.put(
                "decision",
                dispatchNow
                        ? "DISPATCH_NOW"
                        : "WAIT"
        );

        result.put(
                "decisionReason",
                dispatchNow
                        ? "The optimal dispatch time has arrived."
                        : "Wait until the rider should leave to reach the kitchen when the food is ready."
        );

        return result;
    }

    /*
     * ============================================================
     * FOOD READY TIME
     * ============================================================
     */

    @Override
    public LocalDateTime calculateFoodReadyTime(
            Order order) {

        validateOrder(order);

        Integer preparationTime =
                getPreparationTime(order);

        return order.getCreatedAt()
                .plusMinutes(preparationTime);
    }

    /*
     * ============================================================
     * RIDER → KITCHEN DISTANCE
     * ============================================================
     */

    @Override
    public double calculateTravelDistance(
            Order order,
            Rider rider) {

        validateOrder(order);
        validateRider(rider);

        Kitchen kitchen =
                order.getKitchen();

        return calculateDistance(
                rider.getLatitude(),
                rider.getLongitude(),
                kitchen.getLatitude(),
                kitchen.getLongitude()
        );
    }

    /*
     * ============================================================
     * RIDER → KITCHEN TRAVEL TIME
     * ============================================================
     */

    @Override
    public int calculateTravelTimeMinutes(
            Order order,
            Rider rider) {

        double distance =
                calculateTravelDistance(
                        order,
                        rider
                );

        return calculateTravelTimeMinutes(
                distance
        );
    }

    /*
     * ============================================================
     * OPTIMAL DISPATCH TIME
     * ============================================================
     */

    @Override
    public LocalDateTime calculateOptimalDispatchTime(
            Order order,
            Rider rider) {

        validateOrder(order);
        validateRider(rider);

        Integer preparationTime =
                getPreparationTime(order);

        LocalDateTime foodReadyTime =
                order.getCreatedAt()
                        .plusMinutes(preparationTime);

        int travelTime =
                calculateTravelTimeMinutes(
                        order,
                        rider
                );

        LocalDateTime optimalDispatchTime =
                foodReadyTime.minusMinutes(
                        travelTime
                );

        LocalDateTime now =
                LocalDateTime.now();

        if (optimalDispatchTime.isBefore(now)) {
            return now;
        }

        return optimalDispatchTime;
    }

    /*
     * ============================================================
     * CUSTOMER ETA
     * ============================================================
     *
     * Flow:
     *
     * Kitchen
     *    ↓
     * Rider pickup
     *    ↓
     * Customer
     *
     * ETA calculation:
     *
     * Expected Pickup Time
     * +
     * Kitchen → Customer Travel Time
     * =
     * Estimated Delivery Time
     */
    @Override
    public Map<String, Object> calculateETA(
            Order order,
            Rider rider) {

        validateOrder(order);
        validateRiderForETA(rider);

        LocalDateTime now =
                LocalDateTime.now();

        /*
         * --------------------------------------------------------
         * CASE 1: ORDER ALREADY DELIVERED
         * --------------------------------------------------------
         */

        if ("DELIVERED".equals(order.getStatus())) {

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "orderId",
                    order.getId()
            );

            result.put(
                    "riderId",
                    rider.getId()
            );

            result.put(
                    "status",
                    order.getStatus()
            );

            result.put(
                    "estimatedDeliveryTime",
                    order.getEstimatedDeliveryTime()
            );

            result.put(
                    "minutesRemaining",
                    0
            );

            result.put(
                    "etaBasis",
                    "DELIVERED"
            );

            return result;
        }

        /*
         * --------------------------------------------------------
         * CUSTOMER LOCATION VALIDATION
         * --------------------------------------------------------
         */

        if (order.getDeliveryLatitude() == null ||
                order.getDeliveryLongitude() == null) {

            throw new RuntimeException(
                    "Customer delivery location is required"
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

        /*
         * --------------------------------------------------------
         * KITCHEN → CUSTOMER DISTANCE
         * --------------------------------------------------------
         */

        double kitchenToCustomerDistance =
                calculateDistance(
                        kitchen.getLatitude(),
                        kitchen.getLongitude(),
                        order.getDeliveryLatitude(),
                        order.getDeliveryLongitude()
                );

        int kitchenToCustomerTravelTime =
                calculateTravelTimeMinutes(
                        kitchenToCustomerDistance
                );

        /*
         * --------------------------------------------------------
         * EXPECTED PICKUP TIME
         * --------------------------------------------------------
         */

        LocalDateTime expectedPickupTime;

        String etaBasis;

        /*
         * If rider already picked up the food,
         * delivery starts from NOW.
         */
        if ("PICKED_UP".equals(order.getStatus())) {

            expectedPickupTime = now;

            etaBasis = "RIDER_ALREADY_PICKED_UP";

        } else {

            /*
             * Food ready time.
             */
            LocalDateTime foodReadyTime =
                    calculateFoodReadyTime(order);

            /*
             * Rider → Kitchen travel time.
             */
            double riderToKitchenDistance =
                    calculateDistance(
                            rider.getLatitude(),
                            rider.getLongitude(),
                            kitchen.getLatitude(),
                            kitchen.getLongitude()
                    );

            int riderToKitchenTravelTime =
                    calculateTravelTimeMinutes(
                            riderToKitchenDistance
                    );

            /*
             * Time when rider is expected to
             * arrive at the kitchen.
             */
            LocalDateTime riderArrivalAtKitchen =
                    now.plusMinutes(
                            riderToKitchenTravelTime
                    );

            /*
             * Rider can only pick up when BOTH:
             *
             * 1. Food is ready
             * 2. Rider has reached kitchen
             *
             * Therefore:
             *
             * MAX(foodReadyTime, riderArrivalTime)
             */
            expectedPickupTime =
                    foodReadyTime.isAfter(
                            riderArrivalAtKitchen
                    )
                            ? foodReadyTime
                            : riderArrivalAtKitchen;

            etaBasis =
                    "FOOD_READY_AND_RIDER_TRAVEL";
        }

        /*
         * --------------------------------------------------------
         * ESTIMATED DELIVERY TIME
         * --------------------------------------------------------
         */

        LocalDateTime estimatedDeliveryTime =
                expectedPickupTime.plusMinutes(
                        kitchenToCustomerTravelTime
                );

        /*
         * --------------------------------------------------------
         * MINUTES REMAINING
         * --------------------------------------------------------
         */

        long minutesRemaining =
                Duration.between(
                        now,
                        estimatedDeliveryTime
                ).toMinutes();

        /*
         * ETA should never show a negative value.
         */
        minutesRemaining =
                Math.max(
                        0,
                        minutesRemaining
                );

        /*
         * --------------------------------------------------------
         * SAVE ETA TO ORDER
         * --------------------------------------------------------
         */

        order.setEstimatedDeliveryTime(
                estimatedDeliveryTime
        );

        /*
         * --------------------------------------------------------
         * RESPONSE
         * --------------------------------------------------------
         */

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "orderId",
                order.getId()
        );

        result.put(
                "riderId",
                rider.getId()
        );

        result.put(
                "status",
                order.getStatus()
        );

        result.put(
                "foodReadyTime",
                calculateFoodReadyTime(order)
        );

        result.put(
                "riderToKitchenDistanceKm",
                round(
                        calculateDistance(
                                rider.getLatitude(),
                                rider.getLongitude(),
                                kitchen.getLatitude(),
                                kitchen.getLongitude()
                        )
                )
        );

        result.put(
                "kitchenToCustomerDistanceKm",
                round(
                        kitchenToCustomerDistance
                )
        );

        result.put(
                "kitchenToCustomerTravelTimeMinutes",
                kitchenToCustomerTravelTime
        );

        result.put(
                "expectedPickupTime",
                expectedPickupTime
        );

        result.put(
                "estimatedDeliveryTime",
                estimatedDeliveryTime
        );

        result.put(
                "minutesRemaining",
                minutesRemaining
        );

        result.put(
                "etaBasis",
                etaBasis
        );

        return result;
    }

    /*
     * ============================================================
     * PREPARATION TIME
     * ============================================================
     */

    private Integer getPreparationTime(
            Order order) {

        if (order.getEstimatedPreparationTime() != null &&
                order.getEstimatedPreparationTime() > 0) {

            return order.getEstimatedPreparationTime();
        }

        Integer preparationTime =
                preparationTimeService
                        .estimatePreparationTime(order);

        if (preparationTime == null ||
                preparationTime <= 0) {

            throw new RuntimeException(
                    "Invalid preparation time"
            );
        }

        return preparationTime;
    }

    /*
     * ============================================================
     * TRAVEL TIME
     * ============================================================
     */

    private int calculateTravelTimeMinutes(
            double distanceKm) {

        if (distanceKm <= 0) {
            return 0;
        }

        double travelTimeHours =
                distanceKm /
                        AVERAGE_RIDER_SPEED_KMH;

        return Math.max(
                1,
                (int) Math.ceil(
                        travelTimeHours * 60
                )
        );
    }

    /*
     * ============================================================
     * HAVERSINE DISTANCE
     * ============================================================
     */

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

    /*
     * ============================================================
     * ORDER VALIDATION
     * ============================================================
     */

    private void validateOrder(
            Order order) {

        if (order == null) {

            throw new RuntimeException(
                    "Order is required"
            );
        }

        if (order.getId() == null) {

            throw new RuntimeException(
                    "Order id is required"
            );
        }

        if (order.getCreatedAt() == null) {

            throw new RuntimeException(
                    "Order creation time is required"
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
    }

    /*
     * ============================================================
     * RIDER VALIDATION
     * ============================================================
     */

    private void validateRider(
            Rider rider) {

        if (rider == null) {

            throw new RuntimeException(
                    "Rider is required"
            );
        }

        if (rider.getId() == null) {

            throw new RuntimeException(
                    "Rider id is required"
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

        if (rider.getLatitude() == null ||
                rider.getLongitude() == null) {

            throw new RuntimeException(
                    "Rider location is required"
            );
        }
    }

    /*
     * ETA can still be calculated for a rider
     * that has become unavailable after assignment.
     *
     * Therefore ETA validation does NOT require
     * available = true.
     */
    private void validateRiderForETA(
            Rider rider) {

        if (rider == null) {

            throw new RuntimeException(
                    "Rider is required"
            );
        }

        if (rider.getId() == null) {

            throw new RuntimeException(
                    "Rider id is required"
            );
        }

        if (!Boolean.TRUE.equals(
                rider.getActive())) {

            throw new RuntimeException(
                    "Rider is inactive"
            );
        }

        if (rider.getLatitude() == null ||
                rider.getLongitude() == null) {

            throw new RuntimeException(
                    "Rider location is required"
            );
        }
    }

    /*
     * ============================================================
     * ROUNDING
     * ============================================================
     */

    private double round(
            double value) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }
}