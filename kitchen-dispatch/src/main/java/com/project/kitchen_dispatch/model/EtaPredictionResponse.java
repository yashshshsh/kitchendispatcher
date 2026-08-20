package com.project.kitchen_dispatch.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EtaPredictionResponse {

    private Double predictedDeliveryMinutes;
}