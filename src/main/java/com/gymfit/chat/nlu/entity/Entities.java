package com.gymfit.chat.nlu.entity;

import com.gymfit.branch.ServiceCode;
import com.gymfit.plan.PlanTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Kết quả trích entity từ câu nói.
 *
 * @param spans       danh sách cụm đã nhận diện (đã loại trùng lặp, không chồng lấn)
 * @param services    dịch vụ được nhắc (GYM/BOXING/PICKLEBALL)
 * @param branchId    chi nhánh (null nếu không nhắc hoặc không tìm thấy)
 * @param date        ngày tuyệt đối của mốc thời gian
 * @param time        giờ trong ngày theo giờ chi nhánh
 * @param tier        hạng gói
 * @param durationDays số ngày của gói (1 tháng = 30…)
 * @param money       số tiền VND
 * @param bookingCode mã lịch/đơn người dùng đọc ra
 * @param productSku  mã sản phẩm
 * @param rangeFrom   đầu khoảng báo cáo
 * @param rangeTo     cuối khoảng báo cáo
 */
public record Entities(
        List<Span> spans,
        Set<ServiceCode> services,
        Long branchId,
        LocalDate date,
        LocalTime time,
        PlanTier tier,
        Integer durationDays,
        BigDecimal money,
        String bookingCode,
        String productSku,
        LocalDate rangeFrom,
        LocalDate rangeTo
) {

    public boolean hasEntity() {
        return branchId != null
                || date != null
                || time != null
                || tier != null
                || money != null
                || bookingCode != null
                || productSku != null
                || durationDays != null
                || !services.isEmpty();
    }

    /** Khoảng báo cáo; nếu không có thì lấy ngày đơn. */
    public boolean hasRange() {
        return rangeFrom != null
                && rangeTo != null;
    }

}