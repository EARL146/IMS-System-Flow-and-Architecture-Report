package com.ims.api.inventory;

import com.ims.api.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/inventory")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','INVENTORY_MANAGER','WAREHOUSE_STAFF')")
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/summary")
    public InventoryDtos.StockSummary summary() {
        return inventory.summary();
    }

    @GetMapping("/warehouses/{warehouseId}/stock")
    public PageResponse<InventoryDtos.StockResponse> stock(
            @PathVariable @Min(1) long warehouseId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return PageResponse.from(inventory.stock(warehouseId, PageRequest.of(page, size)));
    }

    @GetMapping("/warehouses/{warehouseId}/movements")
    public PageResponse<InventoryDtos.MovementResponse> movements(
            @PathVariable @Min(1) long warehouseId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return PageResponse.from(inventory.recentMovements(warehouseId, PageRequest.of(page, size)));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','INVENTORY_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryDtos.MovementResponse adjust(
            @Valid @RequestBody InventoryDtos.AdjustmentRequest request,
            Authentication authentication
    ) {
        return inventory.adjust(request, authentication.getName());
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryDtos.TransferResponse transfer(
            @Valid @RequestBody InventoryDtos.TransferRequest request,
            Authentication authentication
    ) {
        return inventory.transfer(request, authentication.getName());
    }
}
