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
     * 30 km/h is a reasonable initial value for
     * an urban delivery simulation.
     *
     * Later this can be replaced by a real
     * maps/routing API.
     */
    private static final double AVERAGE_RIDER_SPEED_KMH = 30.0;

    private final IPreparationTimeService preparationTimeService;

    @Override
    public Map<String, Object> calculateDispatchDecision(
            Order order,
            Rider rider) {

        validateOrder(order);
        validateRider(rider);

        Integer preparationTime =
                preparationTimeService
                        .estimatePreparationTime(order);

        LocalDateTime orderCreatedAt =
                LocalDateTime.now();

        LocalDateTime foodReadyTime =
                orderCreatedAt.plusMinutes(
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

        /*
         * If the calculated dispatch time is already
         * in the past, the rider should be dispatched now.
         */
        LocalDateTime actualDispatchTime =
                optimalDispatchTime.isBefore(orderCreatedAt)
                        ? orderCreatedAt
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
                actualDispatchTime
        );

        result.put(
                "dispatchImmediately",
                actualDispatchTime.equals(
                        orderCreatedAt
                )
        );

        result.put(
                "decision",
                buildDecisionMessage(
                        actualDispatchTime,
                        orderCreatedAt,
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
                preparationTimeService
                        .estimatePreparationTime(order);

        return LocalDateTime.now()
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
         * Always keep a minimum of one minute
         * for a non-zero journey.
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
                preparationTimeService
                        .estimatePreparationTime(order);

        LocalDateTime foodReadyTime =
                LocalDateTime.now()
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
         * Never return a dispatch time in the past.
         */
        LocalDateTime now =
                LocalDateTime.now();

        if (optimalDispatchTime.isBefore(now)) {
            return now;
        }

        return optimalDispatchTime;
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

    /*
     * Haversine distance.
     *
     * Returns distance in kilometers.
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
                        * Math.sin(latDistance / 2)
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
            LocalDateTime recommendedDispatchTime,
            LocalDateTime now,
            int travelTimeMinutes) {

        if (recommendedDispatchTime.equals(now)) {

            return
                    "Dispatch rider immediately because " +
                            "the rider travel time is greater than " +
                            "or equal to the remaining preparation time.";
        }

        return
                "Dispatch rider approximately " +
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