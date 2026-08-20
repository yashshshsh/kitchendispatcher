package com.project.kitchen_dispatch.controller;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.model.Order;
import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.service.interfac.IDispatchDecisionService;
import com.project.kitchen_dispatch.service.interfac.IDispatchService;
import com.project.kitchen_dispatch.service.interfac.IOrderService;
import com.project.kitchen_dispatch.service.interfac.IRiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dispatches")
@RequiredArgsConstructor
public class DispatchController {

    private final IDispatchService dispatchService;

    private final IDispatchDecisionService
            dispatchDecisionService;

    private final IOrderService orderService;

    private final IRiderService riderService;


    @PostMapping
    public ResponseEntity<Dispatch>
    createDispatch(
            @RequestBody Dispatch dispatch) {

        Dispatch created =
                dispatchService.createDispatch(
                        dispatch
                );

        return new ResponseEntity<>(
                created,
                HttpStatus.CREATED
        );
    }


    @PostMapping("/{id}/pickup")
    public ResponseEntity<Dispatch>
    markPickedUp(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                dispatchService.markPickedUp(id)
        );
    }


    @PostMapping("/{id}/deliver")
    public ResponseEntity<Dispatch>
    markDelivered(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                dispatchService.markDelivered(id)
        );
    }


    @GetMapping("/evaluate/{orderId}")
    public ResponseEntity<Map<String, Object>>
    evaluateRiders(
            @PathVariable Long orderId) {

        Order order =
                orderService.getOrderById(
                        orderId
                );

        return ResponseEntity.ok(
                riderService.evaluateRiders(
                        order
                )
        );
    }


    @GetMapping("/analytics/eta")
    public ResponseEntity<Map<String, Object>>
    getETAAnalytics() {

        return ResponseEntity.ok(
                dispatchService.getETAAnalytics()
        );
    }


    @GetMapping("/eta/{orderId}")
    public ResponseEntity<Map<String, Object>>
    calculateETA(
            @PathVariable Long orderId) {

        Order order =
                orderService.getOrderById(
                        orderId
                );

        Rider rider =
                riderService.findAssignedRiderForOrder(
                        orderId
                );

        Map<String, Object> eta =
                dispatchDecisionService.calculateETA(
                        order,
                        rider
                );

        /*
         * Persist the newly calculated ETA.
         */
        orderService.saveOrder(order);

        return ResponseEntity.ok(eta);
    }


    @GetMapping("/decision/{orderId}/{riderId}")
    public ResponseEntity<Map<String, Object>>
    calculateDispatchDecision(
            @PathVariable Long orderId,
            @PathVariable Long riderId) {

        Order order =
                orderService.getOrderById(
                        orderId
                );

        Rider rider =
                riderService.getRiderById(
                        riderId
                );

        return ResponseEntity.ok(
                dispatchDecisionService
                        .calculateDispatchDecision(
                                order,
                                rider
                        )
        );
    }


    @GetMapping("/{id}")
    public ResponseEntity<Dispatch>
    getDispatchById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                dispatchService.getDispatchById(id)
        );
    }
}