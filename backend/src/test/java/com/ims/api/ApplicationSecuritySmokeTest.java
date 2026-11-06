package com.ims.api;

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
}
