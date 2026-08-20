package com.project.kitchen_dispatch.service.interfac;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Order;

public interface IDispatchService {

    Dispatch createDispatch(Dispatch dispatch);

    Dispatch getDispatchById(Long id);

    Dispatch automaticallyDispatchOrder(Order order);
}