package com.gymfit.inventory;

import com.gymfit.common.security.SecurityContextService;
import com.gymfit.inventory.dto.InventoryAdjustRequest;
import com.gymfit.inventory.dto.InventoryResponse;
import com.gymfit.inventory.dto.StockMovementResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
public class InventoryController {

    private final InventoryService inventoryService;
    private final SecurityContextService securityContextService;

    @GetMapping
    public List<InventoryResponse> list(
            @RequestParam(required = false) Long branchId
    ) {
        return inventoryService.list(
                securityContextService.principal(),
                branchId
        );
    }

    @GetMapping("/movements")
    public List<StockMovementResponse> movements(
            @RequestParam(required = false) Long branchId
    ) {
        return inventoryService.movements(
                securityContextService.principal(),
                branchId
        );
    }

    @PostMapping("/adjust")
    public InventoryResponse adjust(
            @Valid @RequestBody InventoryAdjustRequest request
    ) {
        return inventoryService.adjust(
                securityContextService.principal(),
                request
        );
    }
}