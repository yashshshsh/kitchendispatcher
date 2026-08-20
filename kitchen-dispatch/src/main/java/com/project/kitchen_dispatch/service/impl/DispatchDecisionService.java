package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.EtaPredictionRequest;
import com.project.kitchen_dispatch.model.EtaPredictionResponse;
import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.service.interfac.IDispatchDecisionService;
import com.project.kitchen_dispatch.service.interfac.IEtaMlService;
import com.project.kitchen_dispatch.service.interfac.IPreparationTimeService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DispatchDecisionService
        implements IDispatchDecisionService {

    private static final double AVERAGE_RIDER_SPEED_KMH = 30.0;

    private final IPreparationTimeService
            preparationTimeService;

    private final IEtaMlService
            etaMlService;


    // ============================================================
    // DISPATCH DECISION
    // ============================================================

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
                        .plusMinutes(
                                preparationTime
                        );

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


    // ============================================================
    // FOOD READY TIME
    // ============================================================

    @Override
    public LocalDateTime calculateFoodReadyTime(
            Order order) {

        validateOrder(order);

        return order.getCreatedAt()
                .plusMinutes(
                        getPreparationTime(order)
                );
    }


    // ============================================================
    // RIDER -> KITCHEN DISTANCE
    // ============================================================

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


    // ============================================================
    // RIDER -> KITCHEN TRAVEL TIME
    // ============================================================

    @Override
    public int calculateTravelTimeMinutes(
            Order order,
            Rider rider) {

        return calculateTravelTimeMinutes(
                calculateTravelDistance(
                        order,
                        rider
                )
        );
    }


    // ============================================================
    // OPTIMAL DISPATCH TIME
    //
    // This remains deterministic.
    //
    // ML is NOT used for deciding when the rider should leave
    // for the kitchen.
    // ============================================================

    @Override
    public LocalDateTime calculateOptimalDispatchTime(
            Order order,
            Rider rider) {

        validateOrder(order);
        validateRider(rider);

        LocalDateTime foodReadyTime =
                calculateFoodReadyTime(order);

        int travelTime =
                calculateTravelTimeMinutes(
                        order,
                        rider
                );

        LocalDateTime optimal =
                foodReadyTime.minusMinutes(
                        travelTime
                );

        LocalDateTime now =
                LocalDateTime.now();

        return optimal.isBefore(now)
                ? now
                : optimal;
    }


    // ============================================================
    // ETA
    //
    // ML IS USED HERE.
    //
    // Flow:
    //
    // preparation time
    // rider -> kitchen distance
    // kitchen -> customer distance
    // total distance
    // hour
    // day
    //       ↓
    // FastAPI
    //       ↓
    // predicted delivery minutes
    //       ↓
    // estimated delivery time
    // ============================================================

    @Override
    public Map<String, Object> calculateETA(
            Order order,
            Rider rider) {

        validateOrder(order);
        validateRiderForETA(rider);

        LocalDateTime now =
                LocalDateTime.now();


        // ========================================================
        // DELIVERED ORDER
        // ========================================================

        if ("DELIVERED".equals(
                order.getStatus())) {

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


        // ========================================================
        // VALIDATE CUSTOMER LOCATION
        // ========================================================

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


        // ========================================================
        // PREPARATION TIME
        // ========================================================

        Integer preparationTime =
                getPreparationTime(order);


        // ========================================================
        // RIDER -> KITCHEN DISTANCE
        // ========================================================

        double riderToKitchenDistance =
                calculateDistance(
                        rider.getLatitude(),
                        rider.getLongitude(),
                        kitchen.getLatitude(),
                        kitchen.getLongitude()
                );


        // ========================================================
        // KITCHEN -> CUSTOMER DISTANCE
        // ========================================================

        double kitchenToCustomerDistance =
                calculateDistance(
                        kitchen.getLatitude(),
                        kitchen.getLongitude(),
                        order.getDeliveryLatitude(),
                        order.getDeliveryLongitude()
                );


        // ========================================================
        // TOTAL DISTANCE
        // ========================================================

        double totalDistance =
                riderToKitchenDistance
                        + kitchenToCustomerDistance;


        // ========================================================
        // CURRENT TIME FEATURES
        //
        // Java:
        //
        // Monday = 1
        // Tuesday = 2
        // ...
        // Sunday = 7
        //
        // ML model:
        //
        // Monday = 0
        // Tuesday = 1
        // ...
        // Sunday = 6
        //
        // Therefore subtract 1.
        // ========================================================

        int hourOfDay =
                now.getHour();

        DayOfWeek dayOfWeek =
                now.getDayOfWeek();

        int mlDayOfWeek =
                dayOfWeek.getValue() - 1;


        // ========================================================
        // CREATE ML REQUEST
        // ========================================================

        EtaPredictionRequest mlRequest =
                new EtaPredictionRequest();

        mlRequest.setEstimatedPreparationTime(
                preparationTime
        );

        mlRequest.setRiderToKitchenDistanceKm(
                round(riderToKitchenDistance)
        );

        mlRequest.setKitchenToCustomerDistanceKm(
                round(kitchenToCustomerDistance)
        );

        mlRequest.setTotalDistanceKm(
                round(totalDistance)
        );

        mlRequest.setHourOfDay(
                hourOfDay
        );

        mlRequest.setDayOfWeek(
                mlDayOfWeek
        );


        // ========================================================
        // CALL FASTAPI / RANDOM FOREST
        // ========================================================

        EtaPredictionResponse mlResponse =
                etaMlService.predictEta(
                        mlRequest
                );


        if (mlResponse == null ||
                mlResponse.getPredictedDeliveryMinutes() == null) {

            throw new RuntimeException(
                    "ETA ML service returned an invalid prediction"
            );
        }


        double predictedDeliveryMinutes =
                mlResponse
                        .getPredictedDeliveryMinutes();


        if (predictedDeliveryMinutes < 0) {

            throw new RuntimeException(
                    "ETA ML service returned a negative prediction"
            );
        }


        // ========================================================
        // ML PREDICTION -> DELIVERY TIME
        //
        // Example:
        //
        // now = 17:40
        // prediction = 47.23 minutes
        //
        // estimated delivery:
        // approximately 18:27
        // ========================================================

        LocalDateTime estimatedDeliveryTime =
                now.plusSeconds(
                        Math.round(
                                predictedDeliveryMinutes * 60
                        )
                );


        // ========================================================
        // REMAINING MINUTES
        // ========================================================

        long minutesRemaining =
                Duration.between(
                        now,
                        estimatedDeliveryTime
                ).toMinutes();

        minutesRemaining =
                Math.max(
                        0,
                        minutesRemaining
                );


        // ========================================================
        // UPDATE ORDER
        // ========================================================

        order.setEstimatedDeliveryTime(
                estimatedDeliveryTime
        );


        // ========================================================
        // RESPONSE
        // ========================================================

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
                "preparationTimeMinutes",
                preparationTime
        );

        result.put(
                "riderToKitchenDistanceKm",
                round(
                        riderToKitchenDistance
                )
        );

        result.put(
                "kitchenToCustomerDistanceKm",
                round(
                        kitchenToCustomerDistance
                )
        );

        result.put(
                "totalDistanceKm",
                round(
                        totalDistance
                )
        );

        result.put(
                "hourOfDay",
                hourOfDay
        );

        result.put(
                "dayOfWeek",
                mlDayOfWeek
        );

        result.put(
                "predictedDeliveryMinutes",
                round(
                        predictedDeliveryMinutes
                )
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
                "ML_RANDOM_FOREST"
        );

        return result;
    }


    // ============================================================
    // PREPARATION TIME
    // ============================================================

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


    // ============================================================
    // TRAVEL TIME
    // ============================================================

    private int calculateTravelTimeMinutes(
            double distanceKm) {

        if (distanceKm <= 0) {
            return 0;
        }

        double hours =
                distanceKm /
                        AVERAGE_RIDER_SPEED_KMH;

        return Math.max(
                1,
                (int) Math.ceil(
                        hours * 60
                )
        );
    }


    // ============================================================
    // HAVERSINE DISTANCE
    // ============================================================

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
                Math.sin(
                        latDistance / 2
                )
                        *
                        Math.sin(
                                latDistance / 2
                        )
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


    // ============================================================
    // ORDER VALIDATION
    // ============================================================

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


    // ============================================================
    // RIDER VALIDATION
    // ============================================================

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

        validateRiderLocation(
                rider
        );
    }


    // ============================================================
    // ETA RIDER VALIDATION
    //
    // Assigned riders are unavailable, so ETA calculation
    // must NOT require available == true.
    // ============================================================

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

        validateRiderLocation(
                rider
        );
    }


    // ============================================================
    // RIDER LOCATION VALIDATION
    // ============================================================

    private void validateRiderLocation(
            Rider rider) {

        if (rider.getLatitude() == null ||
                rider.getLongitude() == null) {

            throw new RuntimeException(
                    "Rider location is required"
            );
        }
    }


    // ============================================================
    // ROUND
    // ============================================================

    private double round(
            double value) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }
}