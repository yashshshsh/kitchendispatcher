package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.repository.RiderRepository;
import com.project.kitchen_dispatch.service.interfac.IRiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RiderService implements IRiderService {

    private final RiderRepository riderRepository;

    @Override
    public Rider createRider(Rider rider) {

        if (rider == null) {
            throw new RuntimeException(
                    "Rider is required"
            );
        }

        if (rider.getAvailable() == null) {
            rider.setAvailable(true);
        }

        if (rider.getActive() == null) {
            rider.setActive(true);
        }

        return riderRepository.save(rider);
    }

    @Override
    public Rider getRiderById(Long id) {

        return riderRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Rider not found with id: "
                                        + id
                        ));
    }

    @Override
    public List<Rider> getAvailableRiders() {

        return riderRepository
                .findByAvailableTrueAndActiveTrue();
    }

    @Override
    public Rider findNearestRider(
            Double kitchenLatitude,
            Double kitchenLongitude) {

        if (kitchenLatitude == null ||
                kitchenLongitude == null) {

            throw new RuntimeException(
                    "Kitchen location is required"
            );
        }

        List<Rider> riders =
                riderRepository
                        .findAvailableRidersForDispatch();

        List<Rider> validRiders =
                riders.stream()
                        .filter(rider ->
                                rider.getLatitude() != null &&
                                        rider.getLongitude() != null
                        )
                        .toList();

        if (validRiders.isEmpty()) {

            throw new RuntimeException(
                    "No available riders with valid location"
            );
        }

        return validRiders.stream()
                .min(
                        Comparator.comparingDouble(
                                rider ->
                                        calculateDistance(
                                                kitchenLatitude,
                                                kitchenLongitude,
                                                rider.getLatitude(),
                                                rider.getLongitude()
                                        )
                        )
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Unable to find nearest rider"
                        ));
    }

    @Override
    @Transactional
    public Rider markRiderUnavailable(
            Long riderId) {

        Rider rider =
                getRiderById(riderId);

        if (!Boolean.TRUE.equals(
                rider.getAvailable())) {

            throw new RuntimeException(
                    "Rider is already unavailable"
            );
        }

        rider.setAvailable(false);

        return riderRepository.save(rider);
    }

    @Override
    @Transactional
    public Rider markRiderAvailable(
            Long riderId) {

        Rider rider =
                getRiderById(riderId);

        if (!Boolean.TRUE.equals(
                rider.getActive())) {

            throw new RuntimeException(
                    "Cannot make inactive rider available"
            );
        }

        rider.setAvailable(true);

        return riderRepository.save(rider);
    }

    private double calculateDistance(
            double lat1,
            double lon1,
            double lat2,
            double lon2) {

        final double EARTH_RADIUS_KM = 6371.0;

        double latDistance =
                Math.toRadians(
                        lat2 - lat1
                );

        double lonDistance =
                Math.toRadians(
                        lon2 - lon1
                );

        double a =
                Math.sin(latDistance / 2)
                        * Math.sin(latDistance / 2)
                        +
                        Math.cos(
                                Math.toRadians(lat1)
                        )
                                *
                                Math.cos(
                                        Math.toRadians(lat2)
                                )
                                *
                                Math.sin(
                                        lonDistance / 2
                                )
                                *
                                Math.sin(
                                        lonDistance / 2
                                );

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return EARTH_RADIUS_KM * c;
    }
}