package com.gymfit.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesOrderItemRepository
        extends JpaRepository<SalesOrderItem, Long> {

    List<SalesOrderItem> findAllByOrderIdOrderByIdAsc(Long orderId);
}