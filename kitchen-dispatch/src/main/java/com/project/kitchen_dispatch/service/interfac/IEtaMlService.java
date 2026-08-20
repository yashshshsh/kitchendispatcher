package com.project.kitchen_dispatch.service.interfac;

import com.project.kitchen_dispatch.model.EtaPredictionRequest;
import com.project.kitchen_dispatch.model.EtaPredictionResponse;

public interface IEtaMlService {

    EtaPredictionResponse predictEta(
            EtaPredictionRequest request
    );
}