package com.project.kitchen_dispatch.service.interfac;

import com.project.kitchen_dispatch.model.Order;

public interface IPreparationTimeService {

    Integer estimatePreparationTime(Order order);
}