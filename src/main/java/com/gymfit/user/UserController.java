package com.gymfit.user;

import com.gymfit.common.security.SecurityContextService;
import com.gymfit.user.dto.UserCreateRequest;
import com.gymfit.user.dto.UserResponse;
import com.gymfit.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;
    private final SecurityContextService securityContextService;

    @GetMapping
    public List<UserResponse> list(
            @RequestParam(required = false) RoleCode role
    ) {
        return userService.list(role);
    }

    @GetMapping("/{id}")
    public UserResponse get(
            @PathVariable Long id
    ) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(
            @Valid @RequestBody UserCreateRequest request
    ) {
        return userService.create(
                securityContextService
                        .principal()
                        .getUserId(),
                request
        );
    }

    @PutMapping("/{id}")
    public UserResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return userService.update(
                securityContextService
                        .principal()
                        .getUserId(),
                id,
                request
        );
    }
}