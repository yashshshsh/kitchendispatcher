package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.service.interfac.IDispatchDecisionService;
import com.project.kitchen_dispatch.service.interfac.IPreparationTimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DispatchDecisionService
        implements IDispatchDecisionService {

    private final IPreparationTimeService preparationTimeService;

    private static final double AVERAGE_RIDER_SPEED_KMH = 30.0;

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

    @Override
    public LocalDateTime calculateFoodReadyTime(
            Order order) {

        validateOrder(order);

        Integer preparationTime =
                getPreparationTime(order);

        return order.getCreatedAt()
                .plusMinutes(preparationTime);
    }

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

    private double round(
            double value) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }
}