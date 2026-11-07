package com.ims.api.customer;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.catalog.Product;
import com.ims.api.catalog.ProductRepository;
import com.ims.api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999999999.999");
    private final CartItemRepository items;
    private final AppUserRepository users;
    private final ProductRepository products;

    public CartService(CartItemRepository items, AppUserRepository users, ProductRepository products) {
        this.items = items;
        this.users = users;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public CustomerDtos.CartResponse get(String email) {
        AppUser user = lockUser(email);
        return CustomerDtos.CartResponse.from(items.findAllByUser_IdOrderByIdAsc(user.getId()));
    }

    @Transactional
    public CustomerDtos.CartResponse add(String email, CustomerDtos.AddCartItemRequest request) {
        requireValidQuantity(request.quantity());
        AppUser user = lockUser(email);
        Product product = products.findById(request.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("Active product not found"));
        CartItem item = items.findForUpdateByUserAndProduct(user.getId(), product.getId()).orElse(null);
        if (item == null) {
            items.saveAndFlush(new CartItem(user, product, request.quantity()));
        } else {
            BigDecimal updatedQuantity = item.getQuantity().add(request.quantity());
            requireValidQuantity(updatedQuantity);
            item.setQuantity(updatedQuantity);
            items.saveAndFlush(item);
        }
        return CustomerDtos.CartResponse.from(items.findAllByUser_IdOrderByIdAsc(user.getId()));
    }

    @Transactional
    public CustomerDtos.CartResponse setQuantity(long itemId, String email, CustomerDtos.UpdateCartItemRequest request) {
        requireValidQuantity(request.quantity());
        AppUser user = lockUser(email);
        CartItem item = items.findByIdAndUser_Id(itemId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Cart item not found"));
        item.setQuantity(request.quantity());
        items.saveAndFlush(item);
        return CustomerDtos.CartResponse.from(items.findAllByUser_IdOrderByIdAsc(user.getId()));
    }

    @Transactional
    public CustomerDtos.CartResponse remove(long itemId, String email) {
        AppUser user = lockUser(email);
        CartItem item = items.findByIdAndUser_Id(itemId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Cart item not found"));
        items.delete(item);
        return CustomerDtos.CartResponse.from(items.findAllByUser_IdOrderByIdAsc(user.getId()));
    }

    @Transactional
    public void clear(String email) {
        AppUser user = requireUser(email);
        items.deleteByUser_Id(user.getId());
    }

    private AppUser requireUser(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(ApiException::unauthorized);
    }

    private AppUser lockUser(String email) {
        return users.findByEmailIgnoreCaseForUpdate(email).orElseThrow(ApiException::unauthorized);
    }

    private void requireValidQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(MAX_QUANTITY) > 0
                || quantity.stripTrailingZeros().scale() > 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Cart quantity must be positive, within the supported range, and use at most three decimals");
        }
    }
}
