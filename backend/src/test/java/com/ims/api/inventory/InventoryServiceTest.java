package com.ims.api.inventory;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.catalog.Product;
import com.ims.api.catalog.ProductRepository;
import com.ims.api.catalog.TrackingType;
import com.ims.api.common.ApiException;
import com.ims.api.warehouse.Warehouse;
import com.ims.api.warehouse.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock InventoryBalanceRepository balances;
    @Mock InventoryMovementRepository movements;
    @Mock InventoryIdempotencyRepository idempotencyKeys;
    @Mock ProductRepository products;
    @Mock WarehouseRepository warehouses;
    @Mock AppUserRepository users;

    private InventoryService service;
    private Product product;
    private Warehouse warehouse;
    private AppUser actor;

    @BeforeEach
    void setUp() {
        service = new InventoryService(balances, movements, idempotencyKeys, products, warehouses, users);
        product = new Product("SKU-1", null, "Sample product", null, null, "each", TrackingType.QUANTITY,
                new BigDecimal("5.00"), new BigDecimal("10.00"), BigDecimal.ZERO);
        warehouse = new Warehouse("MAIN", "Main warehouse");
        actor = new AppUser("owner@example.test", "encoded", "Owner");
        ReflectionTestUtils.setField(product, "id", 1L);
        ReflectionTestUtils.setField(warehouse, "id", 2L);
    }

    @Test
    void adjustmentUpdatesBalanceAndWritesMovement() {
        InventoryBalance balance = new InventoryBalance(product, warehouse);
        when(idempotencyKeys.reserveIfAbsent(any())).thenReturn(1);
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(warehouses.findById(2L)).thenReturn(Optional.of(warehouse));
        when(balances.findForUpdate(1L, 2L)).thenReturn(Optional.of(balance));
        when(users.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.of(actor));
        when(balances.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(movements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryDtos.AdjustmentRequest request = new InventoryDtos.AdjustmentRequest(
                1L, 2L, new BigDecimal("4.000"), UUID.randomUUID(), "Initial count");

        InventoryDtos.MovementResponse result = service.adjust(request, "owner@example.test");

        assertEquals(0, new BigDecimal("4.000").compareTo(result.quantityAfter()));
        assertEquals(0, new BigDecimal("4.000").compareTo(balance.getOnHandQuantity()));
        ArgumentCaptor<InventoryMovement> movement = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movements).save(movement.capture());
        assertEquals(0, new BigDecimal("4.000").compareTo(movement.getValue().getQuantityDelta()));
    }

    @Test
    void adjustmentCannotMakeStockNegative() {
        InventoryBalance balance = new InventoryBalance(product, warehouse);
        when(idempotencyKeys.reserveIfAbsent(any())).thenReturn(1);
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(warehouses.findById(2L)).thenReturn(Optional.of(warehouse));
        when(balances.findForUpdate(1L, 2L)).thenReturn(Optional.of(balance));

        InventoryDtos.AdjustmentRequest request = new InventoryDtos.AdjustmentRequest(
                1L, 2L, new BigDecimal("-1.000"), UUID.randomUUID(), "Count correction");

        assertThrows(ApiException.class, () -> service.adjust(request, "owner@example.test"));
        verify(balances, never()).save(any());
        verify(movements, never()).save(any());
    }

    @Test
    void repeatedAdjustmentReturnsTheOriginalMovementWithoutChangingStock() {
        UUID key = UUID.randomUUID();
        InventoryMovement original = new InventoryMovement(product, warehouse, null,
                new BigDecimal("4.000"), BigDecimal.ZERO, new BigDecimal("4.000"), key.toString(), "Initial count");
        when(idempotencyKeys.reserveIfAbsent(key.toString())).thenReturn(0);
        when(movements.findByIdempotencyKey(key.toString())).thenReturn(Optional.of(original));

        InventoryDtos.MovementResponse result = service.adjust(
                new InventoryDtos.AdjustmentRequest(1L, 2L, new BigDecimal("4.000"), key, "Initial count"),
                "owner@example.test");

        assertEquals(0, new BigDecimal("4.000").compareTo(result.quantityAfter()));
        verify(balances, never()).save(any());
        verify(movements, never()).save(any());
    }

    @Test
    void reusingAdjustmentKeyForDifferentPayloadReturnsConflict() {
        UUID key = UUID.randomUUID();
        InventoryMovement original = new InventoryMovement(product, warehouse, null,
                new BigDecimal("4.000"), BigDecimal.ZERO, new BigDecimal("4.000"), key.toString(), "Initial count");
        when(idempotencyKeys.reserveIfAbsent(key.toString())).thenReturn(0);
        when(movements.findByIdempotencyKey(key.toString())).thenReturn(Optional.of(original));

        ApiException error = assertThrows(ApiException.class, () -> service.adjust(
                new InventoryDtos.AdjustmentRequest(1L, 2L, new BigDecimal("3.000"), key, "Initial count"),
                "owner@example.test"));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.getStatus());
        verify(balances, never()).save(any());
        verify(movements, never()).save(any());
    }
}
