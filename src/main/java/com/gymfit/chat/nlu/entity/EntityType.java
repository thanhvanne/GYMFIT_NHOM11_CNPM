package com.gymfit.chat.nlu.entity;

/**
 * Loại entity trích được từ câu nói.
 * <p>
 * {@code DATE}, {@code TIME}, {@code MONEY}, {@code BOOKING_CODE}
 * sẽ bị mask khi đưa vào mô hình dự đoán intent (T5);
 * {@code SERVICE}, {@code BRANCH}, {@code TIER} thì giữ nguyên vì mang tín hiệu.
 */
public enum EntityType {

    SERVICE,
    BRANCH,
    TIER,
    DATE,
    TIME,
    MONEY,
    DURATION,
    BOOKING_CODE,
    PRODUCT

}