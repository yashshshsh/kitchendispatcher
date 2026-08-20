package com.project.kitchen_dispatch.service.interfac;

import com.project.kitchen_dispatch.model.Dispatch;

import java.util.Map;

public interface IDispatchService {

    Dispatch createDispatch(Dispatch dispatch);

    Dispatch automaticallyDispatchOrder(
            com.project.kitchen_dispatch.model.Order order
    );

    Dispatch markPickedUp(Long dispatchId);

    Dispatch markDelivered(Long dispatchId);

    Dispatch getDispatchById(Long id);

    Map<String, Object> getETAAnalytics();
}