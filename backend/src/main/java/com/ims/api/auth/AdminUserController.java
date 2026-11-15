package com.ims.api.auth;

import com.ims.api.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class AdminUserController {
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public PageResponse<AdminUserDtos.AdminUserResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return adminUserService.list(page, size);
    }

    @GetMapping("/roles")
    public List<String> roles() {
        return adminUserService.availableRoles();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDtos.AdminUserResponse create(
            @Valid @RequestBody AdminUserDtos.CreateUserRequest request,
            Authentication authentication
    ) {
        return adminUserService.create(request, authentication.getName());
    }

    @PutMapping("/{id}")
    public AdminUserDtos.AdminUserResponse update(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody AdminUserDtos.UpdateUserRequest request,
            Authentication authentication
    ) {
        return adminUserService.update(id, request, authentication.getName());
    }
}
