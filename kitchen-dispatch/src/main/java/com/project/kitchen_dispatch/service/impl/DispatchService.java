package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.repository.DispatchRepository;
import com.project.kitchen_dispatch.service.interfac.IDispatchDecisionService;
import com.project.kitchen_dispatch.service.interfac.IDispatchService;
import com.project.kitchen_dispatch.service.interfac.IRiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DispatchService
        implements IDispatchService {

    private final DispatchRepository dispatchRepository;

    private final IRiderService riderService;

    private final IDispatchDecisionService
            dispatchDecisionService;

    @Override
    @Transactional
    public Dispatch createDispatch(
            Dispatch dispatch) {

        if (dispatch == null) {
            throw new RuntimeException(
                    "Dispatch is required"
            );
        }

        if (dispatch.getOrder() == null ||
                dispatch.getOrder().getId() == null) {

            throw new RuntimeException(
                    "Order is required for dispatch"
            );
        }

        if (dispatch.getRider() == null ||
                dispatch.getRider().getId() == null) {

            throw new RuntimeException(
                    "Rider is required for dispatch"
            );
        }

        if (dispatchRepository
                .findByOrder(dispatch.getOrder())
                .isPresent()) {

            throw new RuntimeException(
                    "Order already has a dispatch"
            );
        }

        Rider rider =
                riderService.getRiderById(
                        dispatch.getRider().getId()
                );

        if (!Boolean.TRUE.equals(
                rider.getAvailable())) {

            throw new RuntimeException(
                    "Rider is currently unavailable"
            );
        }

        if (!Boolean.TRUE.equals(
                rider.getActive())) {

            throw new RuntimeException(
                    "Rider is inactive"
            );
        }

        dispatch.setRider(rider);

        dispatch.setStatus("ASSIGNED");

        dispatch.setAssignedAt(
                LocalDateTime.now()
        );

        Dispatch savedDispatch =
                dispatchRepository.save(
                        dispatch
                );

        riderService.markRiderUnavailable(
                rider.getId()
        );

        return savedDispatch;
    }

    @Override
    public Dispatch getDispatchById(
            Long id) {

        return dispatchRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Dispatch not found with id: "
                                        + id
                        )
                );
    }

    @Override
    @Transactional
    public Dispatch automaticallyDispatchOrder(
            Order order) {

        if (order == null ||
                order.getId() == null) {

            throw new RuntimeException(
                    "Valid order is required for dispatch"
            );
        }

        Kitchen kitchen =
                order.getKitchen();

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

        /*
         * Prevent duplicate dispatch.
         */
        if (dispatchRepository
                .findByOrder(order)
                .isPresent()) {

            throw new RuntimeException(
                    "Order already has a dispatch"
            );
        }

        /*
         * Find nearest available rider.
         */
        Rider rider =
                riderService.findNearestRider(
                        kitchen.getLatitude(),
                        kitchen.getLongitude()
                );

        if (rider == null) {

            throw new RuntimeException(
                    "No suitable rider found"
            );
        }

        /*
         * Ask the Dispatch Decision Engine
         * when this rider should actually
         * be dispatched.
         */
        LocalDateTime optimalDispatchTime =
                dispatchDecisionService
                        .calculateOptimalDispatchTime(
                                order,
                                rider
                        );

        LocalDateTime now =
                LocalDateTime.now();

        /*
         * If food will be ready later and
         * the rider does not need to leave yet,
         * don't assign the rider.
         */
        if (optimalDispatchTime.isAfter(now)) {

            return null;
        }

        /*
         * Food will be ready soon enough.
         * Assign the rider now.
         */
        Dispatch dispatch =
                Dispatch.builder()
                        .order(order)
                        .rider(rider)
                        .status("ASSIGNED")
                        .assignedAt(
                                LocalDateTime.now()
                        )
                        .build();

        Dispatch savedDispatch =
                dispatchRepository.save(
                        dispatch
                );

        /*
         * Rider is now busy.
         */
        riderService.markRiderUnavailable(
                rider.getId()
        );

        return savedDispatch;
    }
}