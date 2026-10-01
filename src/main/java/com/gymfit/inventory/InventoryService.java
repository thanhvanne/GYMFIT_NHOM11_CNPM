package com.gymfit.inventory;

import com.gymfit.branch.BranchRepository;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.inventory.dto.InventoryAdjustRequest;
import com.gymfit.inventory.dto.InventoryResponse;
import com.gymfit.inventory.dto.StockMovementResponse;
import com.gymfit.product.Product;
import com.gymfit.product.ProductRepository;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final BranchScopeGuard branchScopeGuard;

    @Transactional(readOnly = true)
    public List<InventoryResponse> list(
            AppPrincipal principal,
            Long branchId
    ) {
        Long effectiveBranchId = resolveBranch(principal, branchId);

        List<StockLevel> levels = effectiveBranchId == null
                ? stockLevelRepository.findAll()
                : stockLevelRepository
                .findAllByBranchIdOrderByProductIdAsc(effectiveBranchId);

        return levels.stream()
                .map(this::toInventoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> movements(
            AppPrincipal principal,
            Long branchId
    ) {
        Long effectiveBranchId = resolveBranch(principal, branchId);

        List<StockMovement> movements = effectiveBranchId == null
                ? stockMovementRepository
                .findAllByOrderByCreatedAtUtcDesc()
                : stockMovementRepository
                .findAllByBranchIdOrderByCreatedAtUtcDesc(
                        effectiveBranchId
                );

        return movements.stream()
                .map(this::toMovementResponse)
                .toList();
    }

    @Transactional
    public InventoryResponse adjust(
            AppPrincipal principal,
            InventoryAdjustRequest request
    ) {
        if (request.quantityDelta() == 0) {
            throw new ConflictException(
                    "inventory_delta_zero",
                    "Số lượng điều chỉnh phải khác 0"
            );
        }

        if (principal.getRole() != RoleCode.ADMIN) {
            branchScopeGuard.requireBranch(
                    principal,
                    request.branchId()
            );
        }

        if (!branchRepository.existsById(request.branchId())) {
            throw new NotFoundException(
                    "branch_not_found",
                    "Không tìm thấy chi nhánh"
            );
        }

        Product product = productRepository
                .findById(request.productId())
                .orElseThrow(() -> new NotFoundException(
                        "product_not_found",
                        "Không tìm thấy sản phẩm"
                ));

        StockLevel stock = stockLevelRepository
                .findForUpdate(
                        request.branchId(),
                        request.productId()
                )
                .orElseThrow(() -> new NotFoundException(
                        "stock_level_not_found",
                        "Chưa có dữ liệu tồn kho cho sản phẩm tại chi nhánh"
                ));

        long newQuantity =
                (long) stock.getQuantity() + request.quantityDelta();

        if (newQuantity < 0) {
            throw new ConflictException(
                    "insufficient_stock",
                    "Tồn kho không đủ để thực hiện điều chỉnh"
            );
        }

        if (newQuantity > Integer.MAX_VALUE) {
            throw new ConflictException(
                    "inventory_quantity_overflow",
                    "Số lượng tồn kho vượt giới hạn"
            );
        }

        stock.setQuantity((int) newQuantity);
        stock.setUpdatedAtUtc(TimeUtil.now());

        StockLevel saved = stockLevelRepository.save(stock);

        StockMovement movement = StockMovement.builder()
                .branchId(request.branchId())
                .productId(product.getId())
                .quantityDelta(request.quantityDelta())
                .reason(request.reason().trim())
                .performedByUserId(principal.getUserId())
                .createdAtUtc(TimeUtil.now())
                .build();

        stockMovementRepository.save(movement);

        return toInventoryResponse(saved);
    }

    private Long resolveBranch(
            AppPrincipal principal,
            Long requestedBranchId
    ) {
        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            if (requestedBranchId != null) {
                branchScopeGuard.requireBranch(
                        principal,
                        requestedBranchId
                );
            }

            return principal.getBranchId();
        }

        return requestedBranchId;
    }

    private InventoryResponse toInventoryResponse(
            StockLevel stock
    ) {
        Product product = productRepository
                .findById(stock.getProductId())
                .orElseThrow(() -> new NotFoundException(
                        "product_not_found",
                        "Không tìm thấy sản phẩm"
                ));

        return new InventoryResponse(
                stock.getId(),
                stock.getBranchId(),
                stock.getProductId(),
                product.getSku(),
                product.getName(),
                stock.getQuantity(),
                stock.getUpdatedAtUtc()
        );
    }

    private StockMovementResponse toMovementResponse(
            StockMovement movement
    ) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getBranchId(),
                movement.getProductId(),
                movement.getQuantityDelta(),
                movement.getReason(),
                movement.getPerformedByUserId(),
                movement.getCreatedAtUtc()
        );
    }

    @Transactional
    public void deductForSale(
            Long branchId,
            Long productId,
            int quantity,
            Long performedByUserId,
            String reason
    ) {
        if (quantity <= 0) {
            throw new ConflictException(
                    "invalid_sale_quantity",
                    "Số lượng bán không hợp lệ"
            );
        }

        StockLevel stock = stockLevelRepository
                .findForUpdate(branchId, productId)
                .orElseThrow(() -> new NotFoundException(
                        "stock_level_not_found",
                        "Chưa có dữ liệu tồn kho cho sản phẩm"
                ));

        if (stock.getQuantity() < quantity) {
            throw new ConflictException(
                    "insufficient_stock",
                    "Tồn kho không đủ"
            );
        }

        stock.setQuantity(
                stock.getQuantity() - quantity
        );
        stock.setUpdatedAtUtc(TimeUtil.now());

        stockLevelRepository.save(stock);

        stockMovementRepository.save(
                StockMovement.builder()
                        .branchId(branchId)
                        .productId(productId)
                        .quantityDelta(-quantity)
                        .reason(reason)
                        .performedByUserId(performedByUserId)
                        .createdAtUtc(TimeUtil.now())
                        .build()
        );
    }
}