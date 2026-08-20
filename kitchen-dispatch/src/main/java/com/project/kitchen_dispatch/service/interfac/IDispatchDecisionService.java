package com.project.kitchen_dispatch.service.interfac;

import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;

import java.time.LocalDateTime;
import java.util.Map;

public interface IDispatchDecisionService {

    Map<String, Object> calculateDispatchDecision(
            Order order,
            Rider rider
    );

    LocalDateTime calculateFoodReadyTime(
            Order order
    );

    double calculateTravelDistance(
            Order order,
            Rider rider
    );

    int calculateTravelTimeMinutes(
            Order order,
            Rider rider
    );

    LocalDateTime calculateOptimalDispatchTime(
            Order order,
            Rider rider
    );

    /*
     * Calculate the complete customer ETA.
     */
    Map<String, Object> calculateETA(
            Order order,
            Rider rider
    );
}