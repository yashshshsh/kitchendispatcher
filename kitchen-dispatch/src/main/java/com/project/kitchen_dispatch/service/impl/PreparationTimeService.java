package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.service.interfac.IPreparationTimeService;
import org.springframework.stereotype.Service;

@Service
public class PreparationTimeService
        implements IPreparationTimeService {

    @Override
    public Integer estimatePreparationTime(Order order) {

        if (order == null) {
            throw new RuntimeException(
                    "Order is required"
            );
        }

        /*
         * Temporary rule-based estimation.
         *
         * Later this will be replaced with
         * an ML prediction model.
         */

        Integer preparationTime =
                order.getEstimatedPreparationTime();

        if (preparationTime != null &&
                preparationTime > 0) {

            return preparationTime;
        }

        // Default preparation time
        return 20;
    }
}