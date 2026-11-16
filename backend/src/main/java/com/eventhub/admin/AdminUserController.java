package com.eventhub.admin;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.common.response.PageResponse;
import com.eventhub.security.CurrentUser;
import com.eventhub.user.Role;
import com.eventhub.user.UserStatus;
import com.eventhub.user.dto.UserResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    public record StatusRequest(@NotNull UserStatus status) {
    }

    /** Newest first. Optional filters: q (name or email), role, status. */
    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(q, role, status, page, size);
    }

    @PatchMapping("/{userId}/status")
    public UserResponse setStatus(CurrentUser admin, @PathVariable UUID userId,
            @Valid @RequestBody StatusRequest request) {
        return service.setStatus(admin.id(), userId, request.status());
    }
}
