package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Dispatch;
import com.project.kitchen_dispatch.repository.DispatchRepository;
import com.project.kitchen_dispatch.service.interfac.IDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DispatchService implements IDispatchService {

    private final DispatchRepository dispatchRepository;

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
}