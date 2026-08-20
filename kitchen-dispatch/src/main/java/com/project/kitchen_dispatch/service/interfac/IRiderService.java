package com.project.kitchen_dispatch.service.interfac;

import com.project.kitchen_dispatch.model.Rider;

import java.util.List;

public interface IRiderService {

    Rider createRider(Rider rider);

    Rider getRiderById(Long id);

    List<Rider> getAvailableRiders();
}