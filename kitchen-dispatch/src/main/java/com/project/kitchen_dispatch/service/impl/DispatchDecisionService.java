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

    /*
     * Assumed average rider speed.
     *
     * Later this can be replaced by a real
     * maps/routing service.
     */
    private static final double
            AVERAGE_RIDER_SPEED_KMH = 30.0;

    private final IPreparationTimeService
            preparationTimeService;

    @Override
    public Map<String, Object> calculateDispatchDecision(
            Order order,
            Rider rider) {

        validateOrder(order);
        validateRider(rider);

        /*
         * Use the preparation time already calculated
         * for this order.
         *
         * If it is missing, calculate it once.
         */
        Integer preparationTime =
                getPreparationTime(order);

        /*
         * IMPORTANT:
         *
         * Food ready time is based on the ORIGINAL
         * order creation time.
         *
         * It must NOT use LocalDateTime.now().
         */
        LocalDateTime foodReadyTime =
                order.getCreatedAt()
                        .plusMinutes(
                                preparationTime
                        );

        double travelDistance =
                calculateTravelDistance(
                        order,
                        rider
                );

        int travelTimeMinutes =
                calculateTravelTimeMinutes(
                        order,
                        rider
                );

        LocalDateTime optimalDispatchTime =
                foodReadyTime.minusMinutes(
                        travelTimeMinutes
                );

        LocalDateTime now =
                LocalDateTime.now();

        /*
         * Never return a dispatch time in the past.
         */
        LocalDateTime recommendedDispatchTime =
                optimalDispatchTime.isBefore(now)
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
                "riderTravelDistanceKm",
                round(travelDistance)
        );

        result.put(
                "riderTravelTimeMinutes",
                travelTimeMinutes
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
                "dispatchImmediately",
                !optimalDispatchTime.isAfter(now)
        );

        result.put(
                "decision",
                buildDecisionMessage(
                        optimalDispatchTime,
                        now,
                        travelTimeMinutes
                )
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
                .plusMinutes(
                        preparationTime
                );
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
                kitchen.getLatitude(),
                kitchen.getLongitude(),
                rider.getLatitude(),
                rider.getLongitude()
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

        /*
         * time = distance / speed
         *
         * Convert hours to minutes.
         */
        double travelTimeHours =
                distance /
                        AVERAGE_RIDER_SPEED_KMH;

        double travelTimeMinutes =
                travelTimeHours * 60;

        /*
         * At least one minute for a non-zero trip.
         */
        return Math.max(
                1,
                (int) Math.ceil(
                        travelTimeMinutes
                )
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

        /*
         * IMPORTANT:
         *
         * Use order.getCreatedAt(), NOT now().
         */
        LocalDateTime foodReadyTime =
                order.getCreatedAt()
                        .plusMinutes(
                                preparationTime
                        );

        int travelTimeMinutes =
                calculateTravelTimeMinutes(
                        order,
                        rider
                );

        LocalDateTime optimalDispatchTime =
                foodReadyTime.minusMinutes(
                        travelTimeMinutes
                );

        /*
         * If dispatch time has already arrived,
         * return the current time.
         */
        LocalDateTime now =
                LocalDateTime.now();

        if (optimalDispatchTime.isBefore(now)) {

            return now;
        }

        return optimalDispatchTime;
    }

    private Integer getPreparationTime(
            Order order) {

        if (order.getEstimatedPreparationTime()
                != null &&
                order.getEstimatedPreparationTime()
                        > 0) {

            return order
                    .getEstimatedPreparationTime();
        }

        Integer preparationTime =
                preparationTimeService
                        .estimatePreparationTime(
                                order
                        );

        if (preparationTime == null ||
                preparationTime <= 0) {

            throw new RuntimeException(
                    "Invalid preparation time"
            );
        }

        return preparationTime;
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

        Kitchen kitchen =
                order.getKitchen();

        if (kitchen.getLatitude() == null ||
                kitchen.getLongitude() == null) {

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

    private String buildDecisionMessage(
            LocalDateTime optimalDispatchTime,
            LocalDateTime now,
            int travelTimeMinutes) {

        if (!optimalDispatchTime.isAfter(now)) {

            return
                    "Dispatch rider now because " +
                            "the optimal dispatch time has arrived.";
        }

        return
                "Wait until the optimal dispatch time, " +
                        "which is approximately " +
                        travelTimeMinutes +
                        " minutes before food is ready.";
    }

    private double round(
            double value) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }
}