package com.project.kitchen_dispatch.service.interfac;

import com.project.kitchen_dispatch.model.Kitchen;

public interface IKitchenService {

    Kitchen createKitchen(Kitchen kitchen);

    Kitchen getKitchenById(Long id);
}