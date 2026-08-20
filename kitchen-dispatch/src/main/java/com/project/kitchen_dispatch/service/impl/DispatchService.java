package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.repository.DispatchRepository;
import com.project.kitchen_dispatch.service.interfac.IDispatchService;
import com.project.kitchen_dispatch.service.interfac.IRiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DispatchService implements IDispatchService {

    private final DispatchRepository dispatchRepository;

    private final IRiderService riderService;

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
                    "Order is required"
            );
        }

        if (dispatch.getRider() == null ||
                dispatch.getRider().getId() == null) {

            throw new RuntimeException(
                    "Rider is required"
            );
        }

        Order order =
                dispatch.getOrder();

        Rider rider =
                dispatch.getRider();

        /*
         * Only PLACED orders can be dispatched.
         */
        if (!"PLACED".equals(
                order.getStatus())) {

            throw new RuntimeException(
                    "Order cannot be dispatched. Current status: "
                            + order.getStatus()
            );
        }

        /*
         * Rider must be active.
         */
        if (!Boolean.TRUE.equals(
                rider.getActive())) {

            throw new RuntimeException(
                    "Rider is inactive"
            );
        }

        /*
         * Rider must be available.
         */
        if (!Boolean.TRUE.equals(
                rider.getAvailable())) {

            throw new RuntimeException(
                    "Rider is unavailable"
            );
        }

        /*
         * Check whether this order already has
         * an active dispatch.
         */
        List<Dispatch> existingDispatches =
                dispatchRepository
                        .findByOrderId(
                                order.getId()
                        );

        boolean activeDispatchExists =
                existingDispatches.stream()
                        .anyMatch(existing ->
                                !"DELIVERED".equals(
                                        existing.getStatus()
                                )
                        );

        if (activeDispatchExists) {

            throw new RuntimeException(
                    "Order already has an active dispatch"
            );
        }

        /*
         * Check whether rider is already assigned
         * to another active delivery.
         */
        List<Dispatch> riderDispatches =
                dispatchRepository
                        .findByRiderId(
                                rider.getId()
                        );

        boolean riderBusy =
                riderDispatches.stream()
                        .anyMatch(existing ->
                                !"DELIVERED".equals(
                                        existing.getStatus()
                                )
                        );

        if (riderBusy) {

            throw new RuntimeException(
                    "Rider is already assigned to another order"
            );
        }

        /*
         * Set dispatch state.
         */
        dispatch.setStatus("ASSIGNED");

        dispatch.setAssignedAt(
                LocalDateTime.now()
        );

        /*
         * Update order state.
         */
        order.setStatus("ASSIGNED");

        /*
         * Rider becomes unavailable immediately
         * after assignment.
         */
        rider.setAvailable(false);

        riderService
                .markRiderUnavailable(
                        rider.getId()
                );

        /*
         * Save dispatch.
         */
        return dispatchRepository.save(
                dispatch
        );
    }

    @Override
    @Transactional
    public Dispatch markPickedUp(
            Long dispatchId) {

        Dispatch dispatch =
                getDispatchById(
                        dispatchId
                );

        validateStatus(
                dispatch,
                "ASSIGNED"
        );

        dispatch.setStatus(
                "PICKED_UP"
        );

        dispatch.setPickedUpAt(
                LocalDateTime.now()
        );

        /*
         * Order follows the dispatch lifecycle.
         */
        Order order =
                dispatch.getOrder();

        if (order != null) {

            order.setStatus(
                    "PICKED_UP"
            );
        }

        return dispatchRepository.save(
                dispatch
        );
    }

    @Override
    @Transactional
    public Dispatch markDelivered(
            Long dispatchId) {

        Dispatch dispatch =
                getDispatchById(
                        dispatchId
                );

        /*
         * Delivery is only valid after pickup.
         */
        validateStatus(
                dispatch,
                "PICKED_UP"
        );

        dispatch.setStatus(
                "DELIVERED"
        );

        dispatch.setDeliveredAt(
                LocalDateTime.now()
        );

        /*
         * Update order.
         */
        Order order =
                dispatch.getOrder();

        if (order != null) {

            order.setStatus(
                    "DELIVERED"
            );
        }

        /*
         * Rider becomes available again.
         */
        Rider rider =
                dispatch.getRider();

        if (rider != null &&
                rider.getId() != null) {

            riderService
                    .markRiderAvailable(
                            rider.getId()
                    );
        }

        return dispatchRepository.save(
                dispatch
        );
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
    public List<Dispatch> getAllDispatches() {

        return dispatchRepository
                .findAll();
    }

    private void validateStatus(
            Dispatch dispatch,
            String expectedStatus) {

        if (!expectedStatus.equals(
                dispatch.getStatus())) {

            throw new RuntimeException(
                    "Invalid dispatch state. Expected "
                            + expectedStatus
                            + " but found "
                            + dispatch.getStatus()
            );
        }
    }
}