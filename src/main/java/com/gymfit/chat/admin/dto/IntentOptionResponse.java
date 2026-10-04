package com.gymfit.chat.admin.dto;

/**
 * Một lựa chọn trong danh sách nhãn (dropdown "Gán nhãn").
 *
 * @param name  tên intent = giá trị lưu DB
 * @param group nhóm gom nhãn trong báo cáo
 * @param desc  mô tả tiếng Việt dùng cho admin
 */
public record IntentOptionResponse(

        String name,

        String group,

        String desc
) {
}
