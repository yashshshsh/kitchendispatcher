package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.repository.KitchenRepository;
import com.project.kitchen_dispatch.service.interfac.IKitchenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KitchenService implements IKitchenService {

    private final KitchenRepository kitchenRepository;

    @Override
    public Kitchen createKitchen(Kitchen kitchen) {
        return kitchenRepository.save(kitchen);
    }

    @Override
    public Kitchen getKitchenById(Long id) {
        return kitchenRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kitchen not found with id: " + id));
    }
}