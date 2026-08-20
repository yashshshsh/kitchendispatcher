package com.project.kitchen_dispatch.config;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.repository.DispatchRepository;
import com.project.kitchen_dispatch.repository.KitchenRepository;
import com.project.kitchen_dispatch.repository.OrderRepository;
import com.project.kitchen_dispatch.repository.RiderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Profile("synthetic-data")
@RequiredArgsConstructor
public class SyntheticEtaDataSeeder implements CommandLineRunner {

    private final OrderRepository orderRepository;

    private final DispatchRepository dispatchRepository;

    private final KitchenRepository kitchenRepository;

    private final RiderRepository riderRepository;

    private static final int NUMBER_OF_RECORDS = 500;

    private final Random random = new Random(42);


    @Override
    @Transactional
    public void run(String... args) {

        System.out.println(
                "================================================"
        );

        System.out.println(
                "Starting synthetic ETA data generation..."
        );

        System.out.println(
                "================================================"
        );


        List<Kitchen> kitchens =
                kitchenRepository.findAll();

        List<Rider> riders =
                riderRepository.findAll();


        if (kitchens.isEmpty()) {

            System.out.println(
                    "No kitchens found. "
                            + "Seeder stopped."
            );

            return;
        }


        if (riders.isEmpty()) {

            System.out.println(
                    "No riders found. "
                            + "Seeder stopped."
            );

            return;
        }


        /*
         * Every rider used by the synthetic dataset
         * must have coordinates.
         */
        for (Rider rider : riders) {

            if (rider.getLatitude() == null
                    || rider.getLongitude() == null) {

                System.out.println(
                        "Rider " + rider.getId()
                                + " has no coordinates."
                );

                System.out.println(
                        "Please add latitude and longitude "
                                + "to all riders first."
                );

                return;
            }
        }


        List<Order> orders =
                new ArrayList<>();

        List<Dispatch> dispatches =
                new ArrayList<>();


        LocalDateTime baseTime =
                LocalDateTime.now()
                        .minusDays(30);


        /*
         * ========================================================
         * GENERATE ORDERS
         * ========================================================
         */
        for (int i = 0;
             i < NUMBER_OF_RECORDS;
             i++) {

            Kitchen kitchen =
                    kitchens.get(
                            random.nextInt(
                                    kitchens.size()
                            )
                    );


            Rider rider =
                    riders.get(
                            random.nextInt(
                                    riders.size()
                            )
                    );


            LocalDateTime createdAt =
                    generateCreatedAt(
                            baseTime
                    );


            /*
             * Generate realistic customer coordinates
             * around the kitchen.
             */
            double[] customerLocation =
                    generateLocationAroundKitchen(
                            kitchen
                    );


            double customerLatitude =
                    customerLocation[0];

            double customerLongitude =
                    customerLocation[1];


            /*
             * Preparation estimate.
             */
            int estimatedPreparationTime =
                    randomBetween(
                            12,
                            35
                    );


            /*
             * Rider -> Kitchen.
             */
            double riderToKitchenDistance =
                    calculateDistanceKm(
                            rider.getLatitude(),
                            rider.getLongitude(),
                            kitchen.getLatitude(),
                            kitchen.getLongitude()
                    );


            /*
             * Kitchen -> Customer.
             */
            double kitchenToCustomerDistance =
                    calculateDistanceKm(
                            kitchen.getLatitude(),
                            kitchen.getLongitude(),
                            customerLatitude,
                            customerLongitude
                    );


            /*
             * Total route distance.
             */
            double totalDistance =
                    riderToKitchenDistance
                            + kitchenToCustomerDistance;


            /*
             * Calculate realistic actual delivery
             * duration.
             */
            long actualDeliveryMinutes =
                    calculateActualDeliveryMinutes(
                            estimatedPreparationTime,
                            totalDistance,
                            createdAt.getHour()
                    );


            LocalDateTime deliveredAt =
                    createdAt.plusMinutes(
                            actualDeliveryMinutes
                    );


            /*
             * Current deterministic ETA baseline:
             *
             * 30 km/h assumed speed.
             */
            long estimatedTravelMinutes =
                    Math.round(
                            (
                                    totalDistance
                                            / 30.0
                            ) * 60.0
                    );


            long estimatedDeliveryMinutes =
                    estimatedPreparationTime
                            + estimatedTravelMinutes;


            LocalDateTime estimatedDeliveryTime =
                    createdAt.plusMinutes(
                            estimatedDeliveryMinutes
                    );


            /*
             * ====================================================
             * ORDER
             * ====================================================
             */
            Order order =
                    Order.builder()

                            .customerName(
                                    "Synthetic Customer "
                                            + (i + 1)
                            )

                            .customerPhone(
                                    "900000"
                                            + String.format(
                                            "%04d",
                                            i + 1
                                    )
                            )

                            .deliveryAddress(
                                    "Synthetic Delivery Area "
                                            + (i + 1)
                            )

                            .deliveryLatitude(
                                    customerLatitude
                            )

                            .deliveryLongitude(
                                    customerLongitude
                            )

                            .status(
                                    "DELIVERED"
                            )

                            .kitchen(
                                    kitchen
                            )

                            .estimatedPreparationTime(
                                    estimatedPreparationTime
                            )

                            .actualPreparationTime(
                                    generateActualPreparationTime(
                                            estimatedPreparationTime
                                    )
                            )

                            .createdAt(
                                    createdAt
                            )

                            .estimatedDeliveryTime(
                                    estimatedDeliveryTime
                            )

                            .build();


            /*
             * Store rider reference temporarily by using
             * the same index relationship later.
             */
            orders.add(order);


            /*
             * ====================================================
             * DISPATCH
             * ====================================================
             */

            LocalDateTime assignedAt =
                    createdAt.plusMinutes(
                            randomBetween(
                                    1,
                                    8
                            )
                    );


            LocalDateTime pickedUpAt =
                    createdAt.plusMinutes(
                            estimatedPreparationTime
                                    + randomBetween(
                                    0,
                                    5
                            )
                    );


            long etaErrorMinutes =
                    actualDeliveryMinutes
                            - estimatedDeliveryMinutes;


            Dispatch dispatch =
                    Dispatch.builder()

                            .order(
                                    order
                            )

                            .rider(
                                    rider
                            )

                            .status(
                                    "DELIVERED"
                            )

                            .assignedAt(
                                    assignedAt
                            )

                            .pickedUpAt(
                                    pickedUpAt
                            )

                            .deliveredAt(
                                    deliveredAt
                            )

                            .estimatedDeliveryTime(
                                    estimatedDeliveryTime
                            )

                            .etaErrorMinutes(
                                    etaErrorMinutes
                            )

                            .riderToKitchenDistanceKm(
                                    round(
                                            riderToKitchenDistance
                                    )
                            )

                            .kitchenToCustomerDistanceKm(
                                    round(
                                            kitchenToCustomerDistance
                                    )
                            )

                            .totalDistanceKm(
                                    round(
                                            totalDistance
                                    )
                            )

                            .build();


            dispatches.add(
                    dispatch
            );
        }


        /*
         * ========================================================
         * SAVE
         * ========================================================
         *
         * Orders must be persisted first because Dispatch has
         * a mandatory relationship with Order.
         */
        List<Order> savedOrders =
                orderRepository.saveAll(
                        orders
                );


        /*
         * Flush so the generated order IDs definitely exist
         * before dispatches are persisted.
         */
        orderRepository.flush();


        dispatchRepository.saveAll(
                dispatches
        );


        /*
         * ========================================================
         * RESULT
         * ========================================================
         */
        System.out.println(
                "================================================"
        );

        System.out.println(
                "Synthetic ETA data generation completed."
        );

        System.out.println(
                "Orders generated: "
                        + savedOrders.size()
        );

        System.out.println(
                "Dispatches generated: "
                        + dispatches.size()
        );

        System.out.println(
                "================================================"
        );
    }


    /*
     * ============================================================
     * LOCATION GENERATION
     * ============================================================
     */

    private double[] generateLocationAroundKitchen(
            Kitchen kitchen) {

        /*
         * Customer will be between 0.5 km and 8 km
         * from the kitchen.
         */
        double distanceKm =
                randomBetweenDouble(
                        0.5,
                        8.0
                );


        double angle =
                randomBetweenDouble(
                        0,
                        Math.PI * 2
                );


        /*
         * Approximately 111 km per degree of latitude.
         */
        double latitudeOffset =
                (
                        distanceKm
                                * Math.cos(angle)
                ) / 111.0;


        /*
         * Longitude distance depends on latitude.
         */
        double longitudeOffset =
                (
                        distanceKm
                                * Math.sin(angle)
                )
                        /
                        (
                                111.0
                                        * Math.cos(
                                        Math.toRadians(
                                                kitchen.getLatitude()
                                        )
                                )
                        );


        return new double[]{
                kitchen.getLatitude()
                        + latitudeOffset,

                kitchen.getLongitude()
                        + longitudeOffset
        };
    }


    /*
     * ============================================================
     * HAVERSINE DISTANCE
     * ============================================================
     *
     * This was the missing method in the previous version.
     *
     * Returns straight-line geographical distance in kilometres.
     */
    private double calculateDistanceKm(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2) {

        final double EARTH_RADIUS_KM =
                6371.0;


        double lat1 =
                Math.toRadians(
                        latitude1
                );

        double lat2 =
                Math.toRadians(
                        latitude2
                );


        double deltaLatitude =
                Math.toRadians(
                        latitude2
                                - latitude1
                );


        double deltaLongitude =
                Math.toRadians(
                        longitude2
                                - longitude1
                );


        double a =
                Math.sin(
                        deltaLatitude / 2
                )
                        *
                        Math.sin(
                                deltaLatitude / 2
                        )

                        +

                        Math.cos(lat1)
                                *
                                Math.cos(lat2)
                                *
                                Math.sin(
                                        deltaLongitude / 2
                                )
                                *
                                Math.sin(
                                        deltaLongitude / 2
                                );


        double c =
                2
                        *
                        Math.atan2(
                                Math.sqrt(a),
                                Math.sqrt(1 - a)
                        );


        return EARTH_RADIUS_KM * c;
    }


    /*
     * ============================================================
     * ACTUAL DELIVERY TIME
     * ============================================================
     */

    private long calculateActualDeliveryMinutes(
            int preparationTime,
            double totalDistance,
            int hour) {

        /*
         * Simulated real-world rider speed.
         */
        double averageSpeed =
                getAverageSpeed(
                        hour
                );


        /*
         * Distance / speed = hours.
         *
         * Multiply by 60 to get minutes.
         */
        double travelMinutes =
                (
                        totalDistance
                                / averageSpeed
                ) * 60.0;


        /*
         * Simulated traffic delay.
         */
        double trafficDelay =
                getTrafficDelay(
                        hour
                );


        /*
         * Small random operational variation.
         */
        double randomDelay =
                randomBetweenDouble(
                        -3.0,
                        5.0
                );


        return Math.max(
                10,
                Math.round(
                        preparationTime
                                + travelMinutes
                                + trafficDelay
                                + randomDelay
                )
        );
    }


    private double getAverageSpeed(
            int hour) {

        /*
         * Evening peak.
         */
        if (hour >= 18
                && hour <= 20) {

            return randomBetweenDouble(
                    18,
                    24
            );
        }


        /*
         * Lunch peak.
         */
        if (hour >= 12
                && hour <= 14) {

            return randomBetweenDouble(
                    22,
                    27
            );
        }


        /*
         * Normal traffic.
         */
        return randomBetweenDouble(
                25,
                32
        );
    }


    private double getTrafficDelay(
            int hour) {

        if (hour >= 18
                && hour <= 20) {

            return randomBetweenDouble(
                    4,
                    10
            );
        }


        if (hour >= 12
                && hour <= 14) {

            return randomBetweenDouble(
                    2,
                    6
            );
        }


        return randomBetweenDouble(
                0,
                3
        );
    }


    /*
     * ============================================================
     * PREPARATION TIME
     * ============================================================
     */

    private int generateActualPreparationTime(
            int estimatedPreparationTime) {

        int variation =
                randomBetween(
                        -4,
                        6
                );


        return Math.max(
                5,
                estimatedPreparationTime
                        + variation
        );
    }


    /*
     * ============================================================
     * DATE GENERATION
     * ============================================================
     */

    private LocalDateTime generateCreatedAt(
            LocalDateTime baseTime) {

        int daysAgo =
                randomBetween(
                        0,
                        29
                );


        int hour =
                randomBetween(
                        11,
                        21
                );


        int minute =
                randomBetween(
                        0,
                        59
                );


        return baseTime
                .plusDays(
                        daysAgo
                )
                .withHour(
                        hour
                )
                .withMinute(
                        minute
                )
                .withSecond(0)
                .withNano(0);
    }


    /*
     * ============================================================
     * RANDOM HELPERS
     * ============================================================
     */

    private int randomBetween(
            int min,
            int max) {

        return random.nextInt(
                max - min + 1
        ) + min;
    }


    private double randomBetweenDouble(
            double min,
            double max) {

        return min
                + (
                max - min
        ) * random.nextDouble();
    }


    private double round(
            double value) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }
}