package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Kitchen;
import com.project.kitchen_dispatch.repository.KitchenRepository;
import com.project.kitchen_dispatch.service.interfac.IKitchenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KitchenService
        implements IKitchenService {

    private final KitchenRepository kitchenRepository;


    @Override
    public Kitchen createKitchen(
            Kitchen kitchen) {

        if (kitchen == null) {

            throw new RuntimeException(
                    "Kitchen is required"
            );
        }

        if (kitchen.getName() == null ||
                kitchen.getName().isBlank()) {

            throw new RuntimeException(
                    "Kitchen name is required"
            );
        }

        if (kitchen.getAddress() == null ||
                kitchen.getAddress().isBlank()) {

            throw new RuntimeException(
                    "Kitchen address is required"
            );
        }

        validateCoordinates(
                kitchen.getLatitude(),
                kitchen.getLongitude()
        );

        if (kitchen.getActive() == null) {
            kitchen.setActive(true);
        }

        return kitchenRepository.save(
                kitchen
        );
    }


    @Override
    public Kitchen getKitchenById(
            Long id) {

        if (id == null) {

            throw new RuntimeException(
                    "Kitchen id is required"
            );
        }

        return kitchenRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Kitchen not found with id: "
                                        + id
                        )
                );
    }


    private void validateCoordinates(
            Double latitude,
            Double longitude) {

        if (latitude == null ||
                longitude == null) {

            throw new RuntimeException(
                    "Kitchen location is required"
            );
        }

        if (latitude < -90 ||
                latitude > 90) {

            throw new RuntimeException(
                    "Kitchen latitude must be between -90 and 90"
            );
        }

        if (longitude < -180 ||
                longitude > 180) {

            throw new RuntimeException(
                    "Kitchen longitude must be between -180 and 180"
            );
        }
    }
}