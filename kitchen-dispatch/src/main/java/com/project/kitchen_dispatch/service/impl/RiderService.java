package com.project.kitchen_dispatch.service.impl;

import com.project.kitchen_dispatch.model.Rider;
import com.project.kitchen_dispatch.repository.RiderRepository;
import com.project.kitchen_dispatch.service.interfac.IRiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}