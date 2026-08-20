package com.project.kitchen_dispatch.service.interfac;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Order;

import java.util.Map;

public interface IDispatchService {

    Dispatch createDispatch(Dispatch dispatch);

    Dispatch automaticallyDispatchOrder(Order order);

    Dispatch markPickedUp(Long dispatchId);

    Dispatch markDelivered(Long dispatchId);

    Dispatch getDispatchById(Long id);

    Map<String, Object> getETAAnalytics();
}