package com.project.kitchen_dispatch.controller;

import com.project.kitchen_dispatch.model.EtaPredictionRequest;
import com.project.kitchen_dispatch.model.EtaPredictionResponse;
import com.project.kitchen_dispatch.service.interfac.IEtaMlService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/eta")
@RequiredArgsConstructor
public class EtaMlController {

    private final IEtaMlService etaMlService;


    @PostMapping("/predict")
    public EtaPredictionResponse predictEta(
            @RequestBody EtaPredictionRequest request) {

        return etaMlService.predictEta(request);
    }
}