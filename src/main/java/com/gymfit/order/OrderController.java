package com.gymfit.order;

import com.gymfit.common.security.SecurityContextService;
import com.gymfit.order.dto.OrderCreateRequest;
import com.gymfit.order.dto.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final SecurityContextService securityContextService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public OrderResponse create(
            @Valid @RequestBody OrderCreateRequest request
    ) {
        return orderService.create(
                securityContextService.principal(),
                request
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.get(
                securityContextService.principal(),
                id
        );
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<OrderResponse> list() {
        return orderService.list(
                securityContextService.principal()
        );
    }
}