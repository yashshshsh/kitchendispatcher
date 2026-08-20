package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.repository.DispatchRepository;
import com.project.kitchen_dispatch.service.interfac.IDispatchService;
import com.project.kitchen_dispatch.service.interfac.IRiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DispatchService implements IDispatchService {

    private final DispatchRepository dispatchRepository;
    private final IRiderService riderService;

    @Override
    public Dispatch createDispatch(Dispatch dispatch) {

        if (dispatch.getAssignedAt() == null) {
            dispatch.setAssignedAt(LocalDateTime.now());
        }

        if (dispatch.getStatus() == null) {
            dispatch.setStatus("ASSIGNED");
        }

        return dispatchRepository.save(dispatch);
    }

    @Override
    public Dispatch getDispatchById(Long id) {

        return dispatchRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Dispatch not found with id: " + id
                        ));
    }

    @Override
    @Transactional
    public Dispatch automaticallyDispatchOrder(Order order) {

        Kitchen kitchen = order.getKitchen();

        if (kitchen == null) {
            throw new RuntimeException(
                    "Order does not have a kitchen"
            );
        }

        if (kitchen.getLatitude() == null ||
                kitchen.getLongitude() == null) {

            throw new RuntimeException(
                    "Kitchen location is not available"
            );
        }

        Rider rider = riderService.findNearestRider(
                kitchen.getLatitude(),
                kitchen.getLongitude()
        );

        Dispatch dispatch = Dispatch.builder()
                .order(order)
                .rider(rider)
                .status("ASSIGNED")
                .assignedAt(LocalDateTime.now())
                .build();

        Dispatch savedDispatch =
                dispatchRepository.save(dispatch);

        riderService.markRiderUnavailable(
                rider.getId()
        );

        return savedDispatch;
    }
}