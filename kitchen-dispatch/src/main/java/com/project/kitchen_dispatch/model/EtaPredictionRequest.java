package com.project.kitchen_dispatch.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EtaPredictionRequest {

    private Integer estimatedPreparationTime;

    private Double riderToKitchenDistanceKm;

    private Double kitchenToCustomerDistanceKm;

    private Double totalDistanceKm;

    private Integer hourOfDay;

    private Integer dayOfWeek;
}