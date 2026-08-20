package com.project.kitchen_dispatch.service.interfac;

import com.project.kitchen_dispatch.model.EtaTrainingData;

import java.util.List;

public interface IEtaTrainingDataService {

    List<EtaTrainingData> getTrainingData();

    String getTrainingDataCsv();
}