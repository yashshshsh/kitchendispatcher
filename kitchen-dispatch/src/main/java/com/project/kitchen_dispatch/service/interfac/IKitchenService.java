package com.project.kitchen_dispatch.service.interfac;

import com.project.kitchen_dispatch.model.Kitchen;

public class IKitchenService {

    Kitchen createKitchen(Kitchen kitchen);

    Kitchen getKitchenById(Long id);
}
