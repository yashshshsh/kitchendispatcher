package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.service.interfac.IPreparationTimeService;
import org.springframework.stereotype.Service;

@Service
public class PreparationTimeService
        implements IPreparationTimeService {

    private static final int DEFAULT_PREPARATION_TIME = 20;

    @Override
    public Integer estimatePreparationTime(
            Order order) {

        if (order == null) {

            throw new RuntimeException(
                    "Order is required"
            );
        }

        Integer requestedTime =
                order.getEstimatedPreparationTime();

        if (requestedTime == null) {

            return DEFAULT_PREPARATION_TIME;
        }

        if (requestedTime <= 0) {

            throw new RuntimeException(
                    "Preparation time must be greater than zero"
            );
        }

        return requestedTime;
    }
}