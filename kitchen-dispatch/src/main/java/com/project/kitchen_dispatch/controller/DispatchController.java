package com.project.kitchen_dispatch.controller;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.service.interfac.IDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dispatches")
@RequiredArgsConstructor
public class DispatchController {

    private final IDispatchService dispatchService;

    @PostMapping
    public ResponseEntity<Dispatch> createDispatch(
            @RequestBody Dispatch dispatch) {

        Dispatch createdDispatch =
                dispatchService.createDispatch(dispatch);

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
}