package com.project.kitchen_dispatch.service.interfac;

import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;

import java.util.List;
import java.util.Map;

public interface IRiderService {

    Rider createRider(Rider rider);

    Rider getRiderById(Long id);

    List<Rider> getAvailableRiders();

    Rider findNearestRider(
            Double kitchenLatitude,
            Double kitchenLongitude
    );

    Rider findBestRider(Order order);

    Map<String, Object> evaluateRiders(Order order);

    Rider markRiderUnavailable(Long id);

    Rider markRiderAvailable(Long id);

    /*
     * Find the rider currently assigned to an order.
     */
    Rider findAssignedRiderForOrder(Long orderId);
}