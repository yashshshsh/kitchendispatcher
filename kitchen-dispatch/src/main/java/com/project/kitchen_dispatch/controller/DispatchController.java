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

    @GetMapping("/{id}")
    public ResponseEntity<Dispatch> getDispatchById(
            @PathVariable Long id) {

        Dispatch dispatch =
                dispatchService.getDispatchById(id);

        return ResponseEntity.ok(dispatch);
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
}