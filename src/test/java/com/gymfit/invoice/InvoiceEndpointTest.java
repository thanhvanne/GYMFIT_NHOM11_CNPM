package com.gymfit.invoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Luồng "xuất hóa đơn – tải về dạng PDF" qua HTTP thật (MockMvc + JWT).
 *
 * <p>Chứng minh: (1) tải được file PDF thật kèm {@code Content-Disposition:
 * attachment}; (2) bắt buộc đăng nhập; (3) hội viên chỉ thấy hóa đơn của mình;
 * (4) quản lý chỉ thấy hóa đơn trong chi nhánh.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvoiceEndpointTest {

    private static final String ORDER_CUA_MEMBER1 = "1";
    private static final String ORDER_CUA_HOI_VIEN_KHAC = "3";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // 1. Hài lòng
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Admin tải hóa đơn đơn đã thanh toán -> 200, application/pdf, tải về được")
    void adminTaiHoaDonVeMay() throws Exception {

        MvcResult result = mockMvc.perform(
                        get("/api/v1/orders/" + ORDER_CUA_MEMBER1 + "/invoice")
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PDF
                        )
                )
                .andExpect(
                        header().string(
                                "Content-Disposition",
                                org.hamcrest.Matchers.containsString("attachment")
                        )
                )
                .andExpect(
                        header().string(
                                "Content-Disposition",
                                org.hamcrest.Matchers.containsString(
                                        "GYMFIT-ORD_SEED_000001.pdf"
                                )
                        )
                )
                .andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();

        String magic = new String(
                pdf, 0, 5, StandardCharsets.US_ASCII
        );

        assertTrue(
                "%PDF-".equals(magic),
                "Body khong phai PDF, bat dau bang: " + magic
        );

        assertTrue(
                pdf.length > 2000,
                "PDF qua nho: " + pdf.length + " byte"
        );
    }

    @Test
    @DisplayName("Hội viên tải được hóa đơn của chính mình")
    void hoiVienTaiDuocHoaDonCuaMinh() throws Exception {

        mockMvc.perform(
                        get("/api/v1/orders/" + ORDER_CUA_MEMBER1 + "/invoice")
                                .header("Authorization", "Bearer " + token("member1@gymfit.local"))
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PDF
                        )
                );
    }

    @Test
    @DisplayName("Quản lý chi nhánh 1 tải được hóa đơn của chi nhánh 1")
    void quanLyTaiDuocHoaDonTrongChiNhanh() throws Exception {

        mockMvc.perform(
                        get("/api/v1/orders/" + ORDER_CUA_MEMBER1 + "/invoice")
                                .header("Authorization", "Bearer " + token("manager.q1@gymfit.local"))
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PDF
                        )
                );
    }

    // ------------------------------------------------------------------
    // 2. Rào chắn
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Chưa đăng nhập -> bị chặn (không tải được hóa đơn)")
    void khongDangNhapBiChan() throws Exception {

        mockMvc.perform(
                        get("/api/v1/orders/" + ORDER_CUA_MEMBER1 + "/invoice")
                )
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Hội viên không tải được hóa đơn của hội viên khác -> 403")
    void hoiVienKhacBiChan403() throws Exception {

        mockMvc.perform(
                        get("/api/v1/orders/" + ORDER_CUA_HOI_VIEN_KHAC + "/invoice")
                                .header("Authorization", "Bearer " + token("member1@gymfit.local"))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("invoice_out_of_scope"));
    }

    @Test
    @DisplayName("Quản lý chi nhánh 1 không tải được hóa đơn chi nhánh 2 -> 403")
    void quanLyNgoaiChiNhachBiChan403() throws Exception {

        mockMvc.perform(
                        get("/api/v1/orders/" + ORDER_CUA_HOI_VIEN_KHAC + "/invoice")
                                .header("Authorization", "Bearer " + token("manager.q1@gymfit.local"))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("invoice_out_of_scope"));
    }

    @Test
    @DisplayName("Không có đơn hàng -> 404 order_not_found")
    void khongCoDonHang404() throws Exception {

        mockMvc.perform(
                        get("/api/v1/orders/999999/invoice")
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("order_not_found"));
    }

    // ------------------------------------------------------------------

    private String token(String email) throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"" + email
                                                + "\",\"password\":\"password\"}"
                                )
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        return node.get("accessToken").asText();
    }
}
