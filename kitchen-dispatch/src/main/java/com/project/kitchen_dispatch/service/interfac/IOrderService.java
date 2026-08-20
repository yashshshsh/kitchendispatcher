package com.project.kitchen_dispatch.service.interfac;

import com.project.kitchen_dispatch.model.Order;

public interface IOrderService {

    Order createOrder(Order order);

    Order getOrderById(Long id);

    Order saveOrder(Order order);
}