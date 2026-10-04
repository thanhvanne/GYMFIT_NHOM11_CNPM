package com.gymfit.chat.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.chat.session.ChatTrainingCandidate;
import com.gymfit.chat.session.ChatTrainingCandidateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API quản trị chatbot (V2-1) qua HTTP thật.
 *
 * <p>AC: admin gán nhãn 1 candidate và export đúng dòng JSONL;
 * MANAGER/MEMBER gọi → 403; MockMvc phủ 403/200.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatbotAdminEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatTrainingCandidateRepository candidateRepository;

    private Long candidateId;

    @BeforeEach
    void taoCandidate() {

        ChatTrainingCandidate candidate =
                ChatTrainingCandidate.builder()
                        .text("nhap nhan nay de test V2-1")
                        .predictedIntent("OUT_OF_SCOPE")
                        .confidence(new BigDecimal("0.3100"))
                        .status(ChatTrainingCandidate.STATUS_PENDING)
                        .createdAtUtc(com.gymfit.common.util.TimeUtil.now())
                        .build();

        candidateId = candidateRepository.save(candidate).getId();
    }

    @AfterEach
    void xoaCandidate() {

        if (candidateId != null) {
            candidateRepository.deleteById(candidateId);
            candidateId = null;
        }
    }

    // ------------------------------------------------------------------
    // 1. Hài lòng
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Admin xem danh sách ứng viên -> 200, có tổng và mục")
    void adminXemDuocDanhSach() throws Exception {

        mockMvc.perform(
                        get("/api/v1/chatbot/candidates")
                                .param("page", "0")
                                .param("size", "10")
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("Admin gán nhãn ứng viên -> 200, status LABELED, label được lưu")
    void adminGanNhanCandidate() throws Exception {

        mockMvc.perform(
                        put("/api/v1/chatbot/candidates/" + candidateId)
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"label\":\"LIST_PLANS\",\"status\":\"LABELED\"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(candidateId))
                .andExpect(jsonPath("$.status").value("LABELED"))
                .andExpect(jsonPath("$.label").value("LIST_PLANS"));

        ChatTrainingCandidate saved =
                candidateRepository.findById(candidateId).orElseThrow();

        assertEquals("LABELED", saved.getStatus());
        assertEquals("LIST_PLANS", saved.getLabel());
        assertNotNull(saved.getLabeledAtUtc());
        assertNotNull(saved.getLabeledByUserId());
    }

    @Test
    @DisplayName("Export JSONL đúng schema text/intent/group CAND#id")
    void exportDungJsonl() throws Exception {

        mockMvc.perform(
                        put("/api/v1/chatbot/candidates/" + candidateId)
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"label\":\"FAQ_QR_HOWTO\",\"status\":\"LABELED\"}")
                )
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(
                        get("/api/v1/chatbot/export")
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.parseMediaType("application/jsonl")
                        )
                )
                .andExpect(
                        header().string(
                                "Content-Disposition",
                                org.hamcrest.Matchers.containsString("seed_from_logs.jsonl")
                        )
                )
                .andReturn();

        String jsonl =
                result.getResponse().getContentAsString(
                        java.nio.charset.StandardCharsets.UTF_8
                );

        String expected =
                "{\"text\":\"nhap nhan nay de test V2-1\""
                        + ",\"intent\":\"FAQ_QR_HOWTO\""
                        + ",\"group\":\"CAND#" + candidateId + "\"}";

        assertEquals(
                true,
                jsonl.contains(expected),
                "Dòng JSONL không đúng. Thực tế: " + jsonl
        );
    }

    @Test
    @DisplayName("Lấy 36 intent làm nhãn -> 200, có nhóm và mô tả")
    void layDanhSachNhan() throws Exception {

        mockMvc.perform(
                        get("/api/v1/chatbot/intents")
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(36))
                .andExpect(jsonPath("$[0].name").isNotEmpty())
                .andExpect(jsonPath("$[0].group").isNotEmpty())
                .andExpect(jsonPath("$[0].desc").isNotEmpty());
    }

    @Test
    @DisplayName("Xem thống kê -> 200, có số tin 24h và tỷ lệ fallback")
    void xemThongKe() throws Exception {

        mockMvc.perform(
                        get("/api/v1/chatbot/stats")
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messagesLast24h").isNumber())
                .andExpect(jsonPath("$.fallbackRate").isNumber())
                .andExpect(jsonPath("$.candidatesPending").isNumber())
                .andExpect(jsonPath("$.feedbackByIntent").isArray())
                .andExpect(jsonPath("$.topFallbackTexts").isArray());
    }

    // ------------------------------------------------------------------
    // 2. Rào chắn quyền
    // ------------------------------------------------------------------

    @Test
    @DisplayName("MANAGER gọi API quản trị chatbot -> 403")
    void quanLyBiChan403() throws Exception {

        mockMvc.perform(
                        get("/api/v1/chatbot/candidates")
                                .header("Authorization", "Bearer " + token("manager.q1@gymfit.local"))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MEMBER gọi API quản trị chatbot -> 403")
    void hoiVienBiChan403() throws Exception {

        mockMvc.perform(
                        get("/api/v1/chatbot/stats")
                                .header("Authorization", "Bearer " + token("member1@gymfit.local"))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MEMBER không gán được nhãn -> 403 và candidate không đổi")
    void hoiVienKhongGanNhanDuoc() throws Exception {

        mockMvc.perform(
                        put("/api/v1/chatbot/candidates/" + candidateId)
                                .header("Authorization", "Bearer " + token("member1@gymfit.local"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"label\":\"LIST_PLANS\",\"status\":\"LABELED\"}")
                )
                .andExpect(status().isForbidden());

        ChatTrainingCandidate saved =
                candidateRepository.findById(candidateId).orElseThrow();

        assertEquals(ChatTrainingCandidate.STATUS_PENDING, saved.getStatus());
    }

    @Test
    @DisplayName("Chưa đăng nhập -> bị chặn")
    void khongDangNhapBiChan() throws Exception {

        mockMvc.perform(
                get("/api/v1/chatbot/candidates")
        ).andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------
    // 3. Lỗi nghiệp vụ
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Gán nhãn không tồn tại -> 400 candidate_label_unknown")
    void nhanKhongTonTai400() throws Exception {

        mockMvc.perform(
                        put("/api/v1/chatbot/candidates/" + candidateId)
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"label\":\"KHONG_TON_TAI\",\"status\":\"LABELED\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("candidate_label_unknown"));
    }

    @Test
    @DisplayName("LABELED mà thiếu nhãn -> 400 candidate_label_required")
    void thieuNhan400() throws Exception {

        mockMvc.perform(
                        put("/api/v1/chatbot/candidates/" + candidateId)
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"label\":null,\"status\":\"LABELED\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("candidate_label_required"));
    }

    @Test
    @DisplayName("Trạng thái sai -> 400 candidate_status_invalid")
    void trangThaiSai400() throws Exception {

        mockMvc.perform(
                        put("/api/v1/chatbot/candidates/" + candidateId)
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"label\":\"LIST_PLANS\",\"status\":\"DI_KHAC\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("candidate_status_invalid"));
    }

    @Test
    @DisplayName("Candidate không tồn tại -> 404 candidate_not_found")
    void khongTimThay404() throws Exception {

        mockMvc.perform(
                        put("/api/v1/chatbot/candidates/99999999")
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"label\":\"LIST_PLANS\",\"status\":\"LABELED\"}")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("candidate_not_found"));
    }

    @Test
    @DisplayName("Số dòng mỗi trang vượt giới hạn -> 400 size_invalid")
    void sizeQuaLon400() throws Exception {

        mockMvc.perform(
                        get("/api/v1/chatbot/candidates")
                                .param("size", "5000")
                                .header("Authorization", "Bearer " + token("admin@gymfit.local"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("size_invalid"));
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
