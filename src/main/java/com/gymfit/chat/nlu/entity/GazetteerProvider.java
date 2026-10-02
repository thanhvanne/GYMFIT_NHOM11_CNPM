package com.gymfit.chat.nlu.entity;

import java.util.Map;

/**
 * Nguồn cung cấp từ điển động (alias → id) lấy từ dữ liệu thật.
 * <p>
 * Tách thành interface để test không cần database.
 */
public interface GazetteerProvider {

    /**
     * @return alias đã bỏ dấu, chữ thường → branchId
     */
    Map<String, Long> branchAliases();

    /**
     * @return alias đã bỏ dấu, chữ thường → sku
     */
    Map<String, String> productAliases();

}