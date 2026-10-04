package com.gymfit.chat.admin;

import com.gymfit.chat.admin.dto.CandidateLabelRequest;
import com.gymfit.chat.admin.dto.CandidatePageResponse;
import com.gymfit.chat.admin.dto.CandidateResponse;
import com.gymfit.chat.admin.dto.ChatbotStatsResponse;
import com.gymfit.chat.admin.dto.IntentOptionResponse;
import com.gymfit.common.security.SecurityContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * API quản trị chatbot (V2-1) – khép vòng
 * "bot trả lời chưa chắc chắn → admin gán nhãn → export → huấn luyện lại".
 *
 * <p>Mọi endpoint yêu cầu {@code ADMIN}; MANAGER/MEMBER → 403.
 */
@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ChatbotAdminController {

    private final ChatbotAdminService service;

    private final SecurityContextService securityContextService;

    /**
     * Danh sách câu hỏi chờ gán nhãn (phân trang).
     *
     * @param status {@code null} = mọi trạng thái
     */
    @GetMapping("/candidates")
    public CandidatePageResponse candidates(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(status, page, size);
    }

    /**
     * Gán / đổi / loại bỏ nhãn một ứng viên.
     */
    @PutMapping("/candidates/{id}")
    public CandidateResponse label(
            @PathVariable Long id,
            @RequestBody CandidateLabelRequest request
    ) {
        return service.update(
                id,
                request,
                securityContextService.principal()
        );
    }

    /**
     * Danh sách nhãn để vẽ dropdown (36 intent + nhóm + mô tả).
     */
    @GetMapping("/intents")
    public List<IntentOptionResponse> intents() {
        return service.intents();
    }

    /**
     * Tải về file JSONL các câu đã gán nhãn – dán vào
     * {@code chatbot/seed_from_logs.jsonl} rồi chạy lại huấn luyện.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {

        String jsonl =
                service.exportJsonl();

        byte[] body =
                jsonl.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(
                        new MediaType(
                                "application",
                                "jsonl",
                                StandardCharsets.UTF_8
                        )
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"seed_from_logs.jsonl\""
                )
                .contentLength(body.length)
                .body(body);
    }

    /**
     * Thống kê cho trang quản trị (số tin 24h, % fallback, 👍/👎, top fallback).
     */
    @GetMapping("/stats")
    public ChatbotStatsResponse stats() {
        return service.stats();
    }
}
