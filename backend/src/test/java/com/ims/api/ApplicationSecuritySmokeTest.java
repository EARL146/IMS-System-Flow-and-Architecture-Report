package com.ims.api;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationSecuritySmokeTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Test
    void publicCatalogCanBeRead() throws Exception {
        mvc.perform(get("/api/v1/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void catalogRejectsPageSizesAboveTheLimit() throws Exception {
        mvc.perform(get("/api/v1/catalog/products").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managementRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCartRequiresCustomerRole() throws Exception {
        mvc.perform(get("/api/v1/customer/cart"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/customer/cart").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("staff@example.test").roles("OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "portal-customer@example.test", roles = "CUSTOMER")
    void customerCanReadTheirOwnCart() throws Exception {
        if (!users.existsByEmailIgnoreCase("portal-customer@example.test")) {
            users.saveAndFlush(new AppUser("portal-customer@example.test", "encoded-test-password", "Portal Customer"));
        }
        mvc.perform(get("/api/v1/customer/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void cookieRefreshRejectsAnUntrustedOrigin() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cookieRefreshPassesOriginGuardButRequiresAValidSession() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PURCHASING_OFFICER")
    void purchasingStaffCanReadOrderReferenceData() throws Exception {
        mvc.perform(get("/api/v1/admin/products").param("page", "0").param("size", "10"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/warehouses"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/suppliers").param("page", "0").param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER_SUPPORT")
    void customerSupportCanReadTheStaffOrderQueue() throws Exception {
        mvc.perform(get("/api/v1/admin/customer-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void unrelatedStaffRoleCannotReadTheCustomerOrderQueue() throws Exception {
        mvc.perform(get("/api/v1/admin/customer-orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCannotChangeFulfillmentStatus() throws Exception {
        mvc.perform(post("/api/v1/admin/customer-orders/1/status")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void warehouseStaffCannotRecordPayments() throws Exception {
        mvc.perform(post("/api/v1/admin/customer-orders/1/payments/verify")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"4bb9d55d-1cc6-4892-a2d8-9735d22cedba\",\"amount\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void cashierCanReadPosSalesHistory() throws Exception {
        mvc.perform(get("/api/v1/admin/pos/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanReadSalesButCannotCreatePosTransactions() throws Exception {
        mvc.perform(get("/api/v1/admin/pos/sales"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/pos/sales")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"4bb9d55d-1cc6-4892-a2d8-9735d22cedba\",\"warehouseId\":1,\"items\":[{\"productId\":1,\"quantity\":1}],\"discountAmount\":0,\"paymentMethod\":\"CASH\",\"amountTendered\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void inventoryManagerDoesNotGetPosAccessByDefault() throws Exception {
        mvc.perform(get("/api/v1/admin/pos/sales"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void warehouseStaffCanReadSupplierReturnsButCannotCreateThem() throws Exception {
        mvc.perform(get("/api/v1/admin/supplier-returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mvc.perform(post("/api/v1/admin/supplier-returns")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"4bb9d55d-1cc6-4892-a2d8-9735d22cedba\",\"purchaseOrderId\":1,\"reason\":\"Damaged shipment\",\"items\":[{\"purchaseOrderItemId\":1,\"quantity\":1,\"damagedQuantity\":1}]}"))
                .andExpect(status().isForbidden());
    }
}
