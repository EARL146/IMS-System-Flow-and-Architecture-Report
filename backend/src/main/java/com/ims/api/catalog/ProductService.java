package com.ims.api.catalog;

import com.ims.api.common.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class ProductService {
    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public Page<ProductDtos.CatalogProductResponse> searchCatalog(String query, Pageable pageable) {
        String term = query == null || query.isBlank() ? null : query.trim();
        return products.searchCatalog(term, pageable).map(ProductDtos.CatalogProductResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductDtos.AdminProductResponse> listAdmin(Pageable pageable) {
        return products.findAll(pageable).map(ProductDtos.AdminProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductDtos.AdminProductResponse getAdmin(long id) {
        return ProductDtos.AdminProductResponse.from(findProduct(id));
    }

    @Transactional
    public ProductDtos.AdminProductResponse create(ProductDtos.ProductRequest request) {
        String sku = normalizeCode(request.sku());
        if (products.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict("SKU is already in use");
        }

        String barcode = normalizeOptionalCode(request.barcode());
        if (barcode != null && products.existsByBarcodeIgnoreCase(barcode)) {
            throw ApiException.conflict("Barcode is already in use");
        }

        Product product = new Product(
                sku, barcode, request.name().trim(), cleanDescription(request.description()), findCategory(request.categoryId()),
                request.unitOfMeasure().trim(), request.trackingType(), request.costPrice(), request.sellingPrice(),
                request.reorderLevel());
        return ProductDtos.AdminProductResponse.from(products.save(product));
    }

    @Transactional
    public ProductDtos.AdminProductResponse update(long id, ProductDtos.ProductRequest request) {
        Product product = findProduct(id);
        String sku = normalizeCode(request.sku());
        if (!product.getSku().equalsIgnoreCase(sku) && products.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict("SKU is already in use");
        }

        String barcode = normalizeOptionalCode(request.barcode());
        if (barcode != null && !barcode.equalsIgnoreCase(product.getBarcode() == null ? "" : product.getBarcode())
                && products.existsByBarcodeIgnoreCase(barcode)) {
            throw ApiException.conflict("Barcode is already in use");
        }

        product.update(
                sku, barcode, request.name().trim(), cleanDescription(request.description()), findCategory(request.categoryId()),
                request.unitOfMeasure().trim(), request.trackingType(), request.costPrice(), request.sellingPrice(),
                request.reorderLevel());
        return ProductDtos.AdminProductResponse.from(products.save(product));
    }

    @Transactional
    public void archive(long id) {
        Product product = findProduct(id);
        product.archive();
        products.save(product);
    }

    private Product findProduct(long id) {
        return products.findWithCategoryById(id).orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private Category findCategory(Long id) {
        if (id == null) {
            return null;
        }
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Category not found"));
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalCode(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String cleanDescription(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
