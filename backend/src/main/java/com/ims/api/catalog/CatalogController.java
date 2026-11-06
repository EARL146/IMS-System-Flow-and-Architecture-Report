package com.ims.api.catalog;

import com.ims.api.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final ProductService productService;
    private final CategoryRepository categories;

    public CatalogController(ProductService productService, CategoryRepository categories) {
        this.productService = productService;
        this.categories = categories;
    }

    @GetMapping("/products")
    public PageResponse<ProductDtos.CatalogProductResponse> products(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return PageResponse.from(productService.searchCatalog(q, PageRequest.of(page, size)));
    }

    @GetMapping("/categories")
    public java.util.List<ProductDtos.CategoryResponse> categories() {
        return categories.findAllByActiveTrueOrderByNameAsc().stream()
                .map(ProductDtos.CategoryResponse::from)
                .toList();
    }
}
