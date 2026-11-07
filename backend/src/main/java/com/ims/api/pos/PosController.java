package com.ims.api.pos;

import com.ims.api.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/admin/pos")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','CASHIER','ACCOUNTANT')")
public class PosController {
    private final PosService pos;

    public PosController(PosService pos) {
        this.pos = pos;
    }

    @GetMapping("/config")
    public PosDtos.ConfigResponse config() {
        return pos.config();
    }

    @GetMapping("/products")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','CASHIER')")
    public PageResponse<PosDtos.ProductOptionResponse> products(
            @RequestParam @Min(1) long warehouseId,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        return pos.searchProducts(warehouseId, query, page, size);
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','CASHIER')")
    public PageResponse<PosDtos.CustomerOptionResponse> customers(
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return pos.searchCustomers(query, page, size);
    }

    @GetMapping("/sales")
    public PageResponse<PosDtos.SaleResponse> sales(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return pos.listSales(page, size);
    }

    @GetMapping("/sales/{id}")
    public PosDtos.SaleResponse sale(@PathVariable @Min(1) long id) {
        return pos.getSale(id);
    }

    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','CASHIER')")
    public PosDtos.SaleResponse createSale(
            @Valid @RequestBody PosDtos.CreateSaleRequest request,
            Authentication authentication) {
        return pos.createSale(request, authentication.getName());
    }
}
