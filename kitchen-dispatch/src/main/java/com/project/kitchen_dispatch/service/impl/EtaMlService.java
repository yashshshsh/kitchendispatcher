package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.EtaPredictionRequest;
import com.project.kitchen_dispatch.model.EtaPredictionResponse;
import com.project.kitchen_dispatch.service.interfac.IEtaMlService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class EtaMlService
        implements IEtaMlService {

    @Value("${eta.ml.url}")
    private String etaMlUrl;

    private final RestClient.Builder restClientBuilder;


    @Override
    public EtaPredictionResponse predictEta(
            EtaPredictionRequest request) {

        if (request == null) {
            throw new RuntimeException(
                    "ETA prediction request is required"
            );
        }

        if (request.getEstimatedPreparationTime() == null ||
                request.getEstimatedPreparationTime() <= 0) {

            throw new RuntimeException(
                    "Estimated preparation time must be greater than zero"
            );
        }

        if (request.getRiderToKitchenDistanceKm() == null ||
                request.getRiderToKitchenDistanceKm() < 0) {

            throw new RuntimeException(
                    "Rider to kitchen distance is required"
            );
        }

        if (request.getKitchenToCustomerDistanceKm() == null ||
                request.getKitchenToCustomerDistanceKm() < 0) {

            throw new RuntimeException(
                    "Kitchen to customer distance is required"
            );
        }

        if (request.getTotalDistanceKm() == null ||
                request.getTotalDistanceKm() < 0) {

            throw new RuntimeException(
                    "Total distance is required"
            );
        }

        if (request.getHourOfDay() == null ||
                request.getHourOfDay() < 0 ||
                request.getHourOfDay() > 23) {

            throw new RuntimeException(
                    "Hour of day must be between 0 and 23"
            );
        }

        if (request.getDayOfWeek() == null ||
                request.getDayOfWeek() < 0 ||
                request.getDayOfWeek() > 6) {

            throw new RuntimeException(
                    "Day of week must be between 0 and 6"
            );
        }


        try {

            RestClient restClient =
                    restClientBuilder
                            .baseUrl(etaMlUrl)
                            .build();


            EtaPredictionResponse response =
                    restClient
                            .post()
                            .uri("/predict")
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(request)
                            .retrieve()
                            .body(
                                    EtaPredictionResponse.class
                            );


            if (response == null ||
                    response.getPredictedDeliveryMinutes() == null) {

                throw new RuntimeException(
                        "ETA ML service returned an empty prediction"
                );
            }


            return response;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to communicate with ETA ML service: "
                            + e.getMessage(),
                    e
            );
        }
    }
}