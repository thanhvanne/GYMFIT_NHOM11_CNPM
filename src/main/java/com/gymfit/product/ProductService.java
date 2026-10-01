package com.gymfit.product;

import com.gymfit.branch.Branch;
import com.gymfit.branch.BranchRepository;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.util.MoneyUtil;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.inventory.StockLevel;
import com.gymfit.inventory.StockLevelRepository;
import com.gymfit.product.dto.ProductCreateRequest;
import com.gymfit.product.dto.ProductResponse;
import com.gymfit.product.dto.ProductUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final StockLevelRepository stockLevelRepository;

    @Transactional(readOnly = true)
    public List<ProductResponse> list(
            ProductStatus status
    ) {
        List<Product> products =
                status == null
                        ? productRepository
                        .findAllByOrderByNameAsc()
                        : productRepository
                        .findAllByStatusOrderByNameAsc(
                                status
                        );

        return products.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(
                requireProduct(id)
        );
    }

    @Transactional
    public ProductResponse create(
            ProductCreateRequest request
    ) {
        String sku =
                normalizeSku(
                        request.sku()
                );

        if (productRepository
                .findBySkuIgnoreCase(sku)
                .isPresent()) {

            throw new ConflictException(
                    "product_sku_exists",
                    "Mã SKU đã tồn tại"
            );
        }

        Product product =
                Product.builder()
                        .sku(sku)
                        .name(
                                request.name()
                                        .trim()
                        )
                        .category(
                                request.category()
                                        .trim()
                        )
                        .price(
                                MoneyUtil.normalize(
                                        request.price()
                                )
                        )
                        .status(
                                ProductStatus.ACTIVE
                        )
                        .createdAtUtc(
                                TimeUtil.now()
                        )
                        .updatedAtUtc(
                                TimeUtil.now()
                        )
                        .build();

        Product saved =
                productRepository
                        .saveAndFlush(
                                product
                        );

        List<Branch> branches =
                branchRepository.findAll();

        List<StockLevel> stockLevels =
                branches.stream()
                        .map(branch ->
                                StockLevel.builder()
                                        .branchId(
                                                branch.getId()
                                        )
                                        .productId(
                                                saved.getId()
                                        )
                                        .quantity(0)
                                        .updatedAtUtc(
                                                TimeUtil.now()
                                        )
                                        .build()
                        )
                        .toList();

        stockLevelRepository
                .saveAll(stockLevels);

        return toResponse(saved);
    }

    @Transactional
    public ProductResponse update(
            Long id,
            ProductUpdateRequest request
    ) {
        Product product =
                requireProduct(id);

        String sku =
                normalizeSku(
                        request.sku()
                );

        productRepository
                .findBySkuIgnoreCase(sku)
                .ifPresent(existing -> {
                    if (!existing.getId()
                            .equals(id)) {

                        throw new ConflictException(
                                "product_sku_exists",
                                "Mã SKU đã tồn tại"
                        );
                    }
                });

        product.setSku(sku);

        product.setName(
                request.name().trim()
        );

        product.setCategory(
                request.category().trim()
        );

        product.setPrice(
                MoneyUtil.normalize(
                        request.price()
                )
        );

        product.setStatus(
                request.status()
        );

        product.setUpdatedAtUtc(
                TimeUtil.now()
        );

        return toResponse(
                productRepository.save(
                        product
                )
        );
    }

    public Product requireProduct(
            Long id
    ) {
        return productRepository
                .findById(id)
                .orElseThrow(() ->
                        new NotFoundException(
                                "product_not_found",
                                "Không tìm thấy sản phẩm"
                        )
                );
    }

    private String normalizeSku(
            String sku
    ) {
        return sku.trim()
                .toUpperCase();
    }

    private ProductResponse toResponse(
            Product product
    ) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getStatus(),
                product.getCreatedAtUtc(),
                product.getUpdatedAtUtc()
        );
    }
}