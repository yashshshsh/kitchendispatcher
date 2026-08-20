package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.repository.RiderRepository;
import com.project.kitchen_dispatch.service.interfac.IRiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RiderService implements IRiderService {

    private final RiderRepository riderRepository;

    @Override
    public Rider createRider(Rider rider) {
        return riderRepository.save(rider);
    }

    @Override
    public Rider getRiderById(Long id) {
        return riderRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Rider not found with id: " + id
                        ));
    }

    @Override
    public List<Rider> getAvailableRiders() {
        return riderRepository.findByAvailableTrueAndActiveTrue();
    }

    @Override
    public Rider findNearestRider(
            Double kitchenLatitude,
            Double kitchenLongitude) {

        List<Rider> availableRiders =
                riderRepository.findByAvailableTrueAndActiveTrue();

        if (availableRiders.isEmpty()) {
            throw new RuntimeException(
                    "No available riders found"
            );
        }

        return availableRiders.stream()
                .filter(rider ->
                        rider.getLatitude() != null &&
                                rider.getLongitude() != null
                )
                .min(Comparator.comparingDouble(rider ->
                        calculateDistance(
                                kitchenLatitude,
                                kitchenLongitude,
                                rider.getLatitude(),
                                rider.getLongitude()
                        )
                ))
                .orElseThrow(() ->
                        new RuntimeException(
                                "No available riders with valid location"
                        ));
    }

    private double calculateDistance(
            double lat1,
            double lon1,
            double lat2,
            double lon2) {

        final double EARTH_RADIUS_KM = 6371.0;

        double latDistance =
                Math.toRadians(lat2 - lat1);

        double lonDistance =
                Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(latDistance / 2)
                        * Math.sin(latDistance / 2)
                        +
                        Math.cos(Math.toRadians(lat1))
                                * Math.cos(Math.toRadians(lat2))
                                * Math.sin(lonDistance / 2)
                                * Math.sin(lonDistance / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return EARTH_RADIUS_KM * c;
    }
}