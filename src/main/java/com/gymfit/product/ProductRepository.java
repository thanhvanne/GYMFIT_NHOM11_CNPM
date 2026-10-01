package com.gymfit.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    Optional<Product> findBySkuIgnoreCase(
            String sku
    );

    List<Product> findAllByStatusOrderByNameAsc(
            ProductStatus status
    );

    List<Product> findAllByOrderByNameAsc();
}