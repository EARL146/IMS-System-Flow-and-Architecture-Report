package com.ims.api.customer;

import com.ims.api.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/customer")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerController {
    private final CustomerAddressService addresses;
    private final CartService cart;
    private final CustomerOrderService orders;

    public CustomerController(CustomerAddressService addresses, CartService cart, CustomerOrderService orders) {
        this.addresses = addresses;
        this.cart = cart;
        this.orders = orders;
    }

    @GetMapping("/addresses")
    public List<CustomerDtos.AddressResponse> addresses(Authentication authentication) {
        return addresses.list(authentication.getName());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDtos.AddressResponse createAddress(
            @Valid @RequestBody CustomerDtos.AddressRequest request,
            Authentication authentication) {
        return addresses.create(authentication.getName(), request);
    }

    @PutMapping("/addresses/{id}")
    public CustomerDtos.AddressResponse updateAddress(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody CustomerDtos.AddressRequest request,
            Authentication authentication) {
        return addresses.update(id, authentication.getName(), request);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@PathVariable @Min(1) long id, Authentication authentication) {
        addresses.delete(id, authentication.getName());
    }

    @GetMapping("/cart")
    public CustomerDtos.CartResponse getCart(Authentication authentication) {
        return cart.get(authentication.getName());
    }

    @PostMapping("/cart/items")
    public CustomerDtos.CartResponse addCartItem(
            @Valid @RequestBody CustomerDtos.AddCartItemRequest request,
            Authentication authentication) {
        return cart.add(authentication.getName(), request);
    }

    @PutMapping("/cart/items/{id}")
    public CustomerDtos.CartResponse updateCartItem(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody CustomerDtos.UpdateCartItemRequest request,
            Authentication authentication) {
        return cart.setQuantity(id, authentication.getName(), request);
    }

    @DeleteMapping("/cart/items/{id}")
    public CustomerDtos.CartResponse removeCartItem(
            @PathVariable @Min(1) long id,
            Authentication authentication) {
        return cart.remove(id, authentication.getName());
    }

    @DeleteMapping("/cart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(Authentication authentication) {
        cart.clear(authentication.getName());
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDtos.OrderResponse checkout(
            @Valid @RequestBody CustomerDtos.CheckoutRequest request,
            Authentication authentication) {
        return orders.checkout(authentication.getName(), request);
    }

    @GetMapping("/orders")
    public PageResponse<CustomerDtos.OrderResponse> orders(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return orders.list(authentication.getName(), page, size);
    }

    @GetMapping("/orders/{id}")
    public CustomerDtos.OrderResponse getOrder(
            @PathVariable @Min(1) long id,
            Authentication authentication) {
        return orders.get(id, authentication.getName());
    }

    @PostMapping("/orders/{id}/cancel")
    public CustomerDtos.OrderResponse cancelOrder(
            @PathVariable @Min(1) long id,
            Authentication authentication) {
        return orders.cancel(id, authentication.getName());
    }
}
