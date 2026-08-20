package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.EtaTrainingData;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.repository.DispatchRepository;
import com.project.kitchen_dispatch.service.interfac.IEtaTrainingDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EtaTrainingDataService
        implements IEtaTrainingDataService {

    private final DispatchRepository dispatchRepository;


    @Override
    @Transactional(readOnly = true)
    public List<EtaTrainingData> getTrainingData() {

        return dispatchRepository
                .findAll()
                .stream()

                /*
                 * Only completed deliveries can become
                 * historical training examples.
                 */
                .filter(this::isValidCompletedDispatch)

                /*
                 * Keep records chronologically ordered.
                 *
                 * This becomes important later when we
                 * perform a time-based train/test split.
                 */
                .sorted(
                        Comparator.comparing(
                                dispatch ->
                                        dispatch.getOrder()
                                                .getCreatedAt()
                        )
                )

                .map(this::convertToTrainingData)

                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public String getTrainingDataCsv() {

        List<EtaTrainingData> data =
                getTrainingData();


        StringBuilder csv =
                new StringBuilder();


        /*
         * CSV header.
         *
         * These names will become the ML feature
         * and target names in Python.
         */
        csv.append(
                "estimated_preparation_time,"
                        + "rider_to_kitchen_km,"
                        + "kitchen_to_customer_km,"
                        + "total_distance_km,"
                        + "hour_of_day,"
                        + "day_of_week,"
                        + "actual_delivery_minutes"
        );

        csv.append("\n");


        for (EtaTrainingData record : data) {

            csv.append(
                    record.getEstimatedPreparationTime()
            );

            csv.append(",");

            csv.append(
                    record.getRiderToKitchenDistanceKm()
            );

            csv.append(",");

            csv.append(
                    record.getKitchenToCustomerDistanceKm()
            );

            csv.append(",");

            csv.append(
                    record.getTotalDistanceKm()
            );

            csv.append(",");

            csv.append(
                    record.getHourOfDay()
            );

            csv.append(",");

            csv.append(
                    record.getDayOfWeek()
            );

            csv.append(",");

            csv.append(
                    record.getActualDeliveryMinutes()
            );

            csv.append("\n");
        }


        return csv.toString();
    }


    private boolean isValidCompletedDispatch(
            Dispatch dispatch) {

        if (dispatch == null) {
            return false;
        }


        /*
         * We only want completed deliveries.
         */
        if (!"DELIVERED".equals(
                dispatch.getStatus())) {

            return false;
        }


        if (dispatch.getDeliveredAt() == null) {
            return false;
        }


        Order order =
                dispatch.getOrder();

        if (order == null) {
            return false;
        }


        if (order.getCreatedAt() == null) {
            return false;
        }


        /*
         * These values must have been captured
         * at dispatch time.
         */
        if (dispatch
                .getRiderToKitchenDistanceKm() == null) {

            return false;
        }


        if (dispatch
                .getKitchenToCustomerDistanceKm() == null) {

            return false;
        }


        if (dispatch
                .getTotalDistanceKm() == null) {

            return false;
        }


        /*
         * Required ML feature.
         */
        if (order
                .getEstimatedPreparationTime() == null) {

            return false;
        }


        /*
         * Delivery duration must be positive.
         */
        long deliveryMinutes =
                Duration.between(
                        order.getCreatedAt(),
                        dispatch.getDeliveredAt()
                ).toMinutes();


        return deliveryMinutes >= 0;
    }


    private EtaTrainingData convertToTrainingData(
            Dispatch dispatch) {

        Order order =
                dispatch.getOrder();


        LocalDateTime createdAt =
                order.getCreatedAt();


        LocalDateTime deliveredAt =
                dispatch.getDeliveredAt();


        /*
         * TARGET VARIABLE
         *
         * Total actual time from order creation
         * until delivery.
         */
        long actualDeliveryMinutes =
                Duration.between(
                        createdAt,
                        deliveredAt
                ).toMinutes();


        if (actualDeliveryMinutes < 0) {

            throw new IllegalStateException(
                    "Invalid delivery duration for dispatch id: "
                            + dispatch.getId()
            );
        }


        return EtaTrainingData.builder()

                .estimatedPreparationTime(
                        order.getEstimatedPreparationTime()
                )

                .riderToKitchenDistanceKm(
                        dispatch
                                .getRiderToKitchenDistanceKm()
                )

                .kitchenToCustomerDistanceKm(
                        dispatch
                                .getKitchenToCustomerDistanceKm()
                )

                .totalDistanceKm(
                        dispatch.getTotalDistanceKm()
                )

                .hourOfDay(
                        createdAt.getHour()
                )

                .dayOfWeek(
                        createdAt
                                .getDayOfWeek()
                                .getValue()
                )

                .actualDeliveryMinutes(
                        actualDeliveryMinutes
                )

                .build();
    }
}