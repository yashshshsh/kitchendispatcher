package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.EtaTrainingData;
import com.project.kitchen_dispatch.model.Order;
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

    private final com.project.kitchen_dispatch.repository.DispatchRepository
            dispatchRepository;


    @Override
    @Transactional(readOnly = true)
    public List<EtaTrainingData> getTrainingData() {

        return dispatchRepository
                .findAll()
                .stream()
                .filter(this::isValidCompletedDispatch)
                .sorted(
                        Comparator.comparing(
                                dispatch -> dispatch.getOrder().getCreatedAt()
                        )
                )
                .map(this::convertToTrainingData)
                .toList();
    }


    private boolean isValidCompletedDispatch(
            Dispatch dispatch) {

        if (dispatch == null) {
            return false;
        }

        if (!"DELIVERED".equals(
                dispatch.getStatus())) {

            return false;
        }

        if (dispatch.getDeliveredAt() == null) {
            return false;
        }

        if (dispatch.getOrder() == null) {
            return false;
        }

        Order order =
                dispatch.getOrder();

        if (order.getCreatedAt() == null) {
            return false;
        }

        /*
         * These three values were captured at
         * dispatch time.
         *
         * They are mandatory for our first ML model.
         */
        if (dispatch.getRiderToKitchenDistanceKm() == null) {
            return false;
        }

        if (dispatch.getKitchenToCustomerDistanceKm() == null) {
            return false;
        }

        if (dispatch.getTotalDistanceKm() == null) {
            return false;
        }

        /*
         * Preparation estimate is also required
         * because it is one of our ML features.
         */
        if (order.getEstimatedPreparationTime() == null) {
            return false;
        }

        return true;
    }


    private EtaTrainingData convertToTrainingData(
            Dispatch dispatch) {

        Order order =
                dispatch.getOrder();

        LocalDateTime createdAt =
                order.getCreatedAt();

        LocalDateTime deliveredAt =
                dispatch.getDeliveredAt();


        long actualDeliveryMinutes =
                Duration.between(
                        createdAt,
                        deliveredAt
                ).toMinutes();


        /*
         * Protect the training dataset from
         * invalid negative durations.
         */
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
                        dispatch.getRiderToKitchenDistanceKm()
                )

                .kitchenToCustomerDistanceKm(
                        dispatch.getKitchenToCustomerDistanceKm()
                )

                .totalDistanceKm(
                        dispatch.getTotalDistanceKm()
                )

                .hourOfDay(
                        createdAt.getHour()
                )

                .dayOfWeek(
                        createdAt.getDayOfWeek().getValue()
                )

                .actualDeliveryMinutes(
                        actualDeliveryMinutes
                )

                .build();
    }
}