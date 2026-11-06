package com.ims.api.warehouse;

import com.ims.api.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/admin/warehouses")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class WarehouseController {
    private final WarehouseRepository warehouses;

    public WarehouseController(WarehouseRepository warehouses) {
        this.warehouses = warehouses;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','PURCHASING_OFFICER')")
    public List<WarehouseResponse> list() {
        return warehouses.findAllByActiveTrueOrderByNameAsc().stream().map(WarehouseResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse create(@Valid @RequestBody WarehouseRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (warehouses.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Warehouse code is already in use");
        }
        return WarehouseResponse.from(warehouses.save(new Warehouse(code, request.name().trim())));
    }

    public record WarehouseRequest(@NotBlank @Size(max = 48) String code, @NotBlank @Size(max = 160) String name) {
    }

    public record WarehouseResponse(Long id, String code, String name, boolean active) {
        static WarehouseResponse from(Warehouse warehouse) {
            return new WarehouseResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName(), warehouse.isActive());
        }
    }
}
