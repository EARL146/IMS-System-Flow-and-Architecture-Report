package com.ims.api.catalog;

import com.ims.api.customer.CustomerDtos;
import com.ims.api.warehouse.WarehouseRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/warehouses")
public class PublicWarehouseController {
    private final WarehouseRepository warehouses;

    public PublicWarehouseController(WarehouseRepository warehouses) {
        this.warehouses = warehouses;
    }

    @GetMapping
    public java.util.List<CustomerDtos.PublicWarehouseResponse> list() {
        return warehouses.findAllByActiveTrueOrderByNameAsc().stream()
                .map(CustomerDtos.PublicWarehouseResponse::from).toList();
    }
}
