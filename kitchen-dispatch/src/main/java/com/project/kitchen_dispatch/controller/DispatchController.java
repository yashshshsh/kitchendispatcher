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


    /*
     * ============================================================
     * CREATE DISPATCH
     * ============================================================
     */

    @PostMapping
    public ResponseEntity<Dispatch> createDispatch(
            @RequestBody Dispatch dispatch) {

        Dispatch createdDispatch =
                dispatchService.createDispatch(
                        dispatch
                );

        return new ResponseEntity<>(
                createdDispatch,
                HttpStatus.CREATED
        );
    }


    /*
     * ============================================================
     * PICKUP
     * ============================================================
     */

    @PostMapping("/{id}/pickup")
    public ResponseEntity<Dispatch>
    markPickedUp(
            @PathVariable Long id) {

        Dispatch dispatch =
                dispatchService.markPickedUp(
                        id
                );

        return ResponseEntity.ok(
                dispatch
        );
    }


    /*
     * ============================================================
     * DELIVERY
     * ============================================================
     */

    @PostMapping("/{id}/deliver")
    public ResponseEntity<Dispatch>
    markDelivered(
            @PathVariable Long id) {

        Dispatch dispatch =
                dispatchService.markDelivered(
                        id
                );

        return ResponseEntity.ok(
                dispatch
        );
    }


    /*
     * ============================================================
     * EVALUATE RIDERS
     * ============================================================
     */

    @GetMapping("/evaluate/{orderId}")
    public ResponseEntity<Map<String, Object>>
    evaluateRiders(
            @PathVariable Long orderId) {

        Order order =
                orderService.getOrderById(
                        orderId
                );

        Map<String, Object> result =
                riderService.evaluateRiders(
                        order
                );

        return ResponseEntity.ok(
                result
        );
    }


    /*
     * ============================================================
     * ETA ANALYTICS
     * ============================================================
     *
     * IMPORTANT:
     *
     * This endpoint must appear before:
     *
     * GET /{id}
     *
     * because otherwise "analytics" could be interpreted
     * as the dispatch ID.
     */

    @GetMapping("/analytics/eta")
    public ResponseEntity<Map<String, Object>>
    getETAAnalytics() {

        Map<String, Object> analytics =
                dispatchService.getETAAnalytics();

        return ResponseEntity.ok(
                analytics
        );
    }


    /*
     * ============================================================
     * CUSTOMER ETA
     * ============================================================
     */

    @GetMapping("/eta/{orderId}")
    public ResponseEntity<Map<String, Object>>
    calculateETA(
            @PathVariable Long orderId) {

        /*
         * Find order.
         */

        Order order =
                orderService.getOrderById(
                        orderId
                );


        /*
         * Find rider assigned to order.
         */

        Rider rider =
                riderService.findAssignedRiderForOrder(
                        orderId
                );


        /*
         * Calculate ETA.
         */

        Map<String, Object> eta =
                dispatchDecisionService
                        .calculateETA(
                                order,
                                rider
                        );

        return ResponseEntity.ok(
                eta
        );
    }


    /*
     * ============================================================
     * DISPATCH DECISION
     * ============================================================
     */

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

        Map<String, Object> decision =
                dispatchDecisionService
                        .calculateDispatchDecision(
                                order,
                                rider
                        );

        return ResponseEntity.ok(
                decision
        );
    }


    /*
     * ============================================================
     * GET DISPATCH
     * ============================================================
     */

    @GetMapping("/{id}")
    public ResponseEntity<Dispatch>
    getDispatchById(
            @PathVariable Long id) {

        Dispatch dispatch =
                dispatchService.getDispatchById(
                        id
                );

        return ResponseEntity.ok(
                dispatch
        );
    }
}