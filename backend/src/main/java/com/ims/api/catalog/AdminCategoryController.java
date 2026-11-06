package com.ims.api.catalog;

import com.ims.api.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class AdminCategoryController {
    private final CategoryRepository categories;

    public AdminCategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<ProductDtos.CategoryResponse> list() {
        return categories.findAll().stream().map(ProductDtos.CategoryResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDtos.CategoryResponse create(@Valid @RequestBody ProductDtos.CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Category already exists");
        }
        return ProductDtos.CategoryResponse.from(categories.save(new Category(name)));
    }
}
