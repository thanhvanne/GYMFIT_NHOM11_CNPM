package com.gymfit.chat.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.chat.admin.dto.CandidateLabelRequest;
import com.gymfit.chat.admin.dto.CandidatePageResponse;
import com.gymfit.chat.admin.dto.CandidateResponse;
import com.gymfit.chat.admin.dto.ChatbotStatsResponse;
import com.gymfit.chat.admin.dto.FeedbackCountResponse;
import com.gymfit.chat.admin.dto.IntentOptionResponse;
import com.gymfit.chat.dialogue.IntentPolicy;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.session.ChatMessage;
import com.gymfit.chat.session.ChatMessageRepository;
import com.gymfit.chat.session.ChatTrainingCandidate;
import com.gymfit.chat.session.ChatTrainingCandidateRepository;
import com.gymfit.common.error.BadRequestException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vòng "gán nhãn → export → huấn luyện lại" (V2-1).
 *
 * <p>Chỉ {@code ADMIN} được gọi; mọi thao tác ở đây là <b>đọc</b> hoặc
 * gán nhãn câu hỏi đã được bot ghi nhận, không đụng tới dữ liệu nghiệp vụ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotAdminService {

    /** Số dòng mỗi trang tối đa. */
    public static final int MAX_SIZE = 100;

    /** Dòng export tối đa (giữ file nhỏ, dễ đọc lại khi huấn luyện). */
    public static final int MAX_EXPORT = 5000;

    private static final Set<String> STATUSES = Set.of(
            ChatTrainingCandidate.STATUS_PENDING,
            ChatTrainingCandidate.STATUS_LABELED,
            ChatTrainingCandidate.STATUS_REJECTED
    );

    private final ChatTrainingCandidateRepository candidates;

    private final ChatMessageRepository messages;

    private final IntentPolicy policy;

    private final ObjectMapper objectMapper;

    /** Ngưỡng dưới {@code clarify} được coi là fallback (cùng giá trị với DialogueManager). */
    @Value("${gymfit.chatbot.threshold-clarify:0.40}")
    private double thresholdClarify;

    // ------------------------------------------------------------------
    // Danh sách
    // ------------------------------------------------------------------

    /**
     * Trang danh sách ứng viên.
     *
     * @param status {@code null}/rỗng = mọi trạng thái
     */
    public CandidatePageResponse list(
            String status,
            int page,
            int size
    ) {

        if (page < 0) {
            throw new BadRequestException(
                    "page_invalid",
                    "Trang bắt đầu từ 0"
            );
        }

        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException(
                    "size_invalid",
                    "Số dòng mỗi trang phải từ 1 đến " + MAX_SIZE
            );
        }

        String normalized =
                normalizeStatus(status);

        Page<ChatTrainingCandidate> result;

        if (normalized == null) {

            result = candidates.findAll(
                    PageRequest.of(
                            page,
                            size,
                            Sort.by(Sort.Direction.DESC, "createdAtUtc")
                    )
            );

        } else {

            result = candidates.findByStatusOrderByCreatedAtUtcDesc(
                    normalized,
                    PageRequest.of(page, size)
            );
        }

        return new CandidatePageResponse(
                result.getTotalElements(),
                page,
                size,
                result.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    // ------------------------------------------------------------------
    // Gán nhãn
    // ------------------------------------------------------------------

    /**
     * Gán / đổi / loại bỏ nhãn một ứng viên.
     *
     * <ul>
     *   <li>{@code LABELED} → bắt buộc có {@code label} thuộc {@link Intent}.</li>
     *   <li>{@code REJECTED} → {@code label} bị bỏ ({@code null}).</li>
     *   <li>{@code PENDING} → đưa về hàng chờ, xóa nhãn cũ.</li>
     * </ul>
     */
    @Transactional
    public CandidateResponse update(
            Long id,
            CandidateLabelRequest request,
            AppPrincipal principal
    ) {

        if (request == null
                || request.status() == null
                || request.status().isBlank()) {

            throw new BadRequestException(
                    "candidate_status_invalid",
                    "Thiếu trạng thái của câu hỏi"
            );
        }

        String status =
                normalizeStatus(request.status());

        String label =
                request.label() == null
                        ? null
                        : request.label().trim();

        if (ChatTrainingCandidate.STATUS_REJECTED.equals(status)
                || ChatTrainingCandidate.STATUS_PENDING.equals(status)) {

            label = null;

        } else if (label == null || label.isEmpty()) {

            throw new BadRequestException(
                    "candidate_label_required",
                    "Phải chọn nhãn intent khi đặt trạng thái LABELED"
            );

        } else if (!isKnownIntent(label)) {

            throw new BadRequestException(
                    "candidate_label_unknown",
                    "Nhãn intent không tồn tại: " + label
            );
        }

        ChatTrainingCandidate candidate =
                candidates.findById(id)
                        .orElseThrow(
                                () -> new NotFoundException(
                                        "candidate_not_found",
                                        "Không tìm thấy câu hỏi huấn luyện"
                                )
                        );

        candidate.setLabel(label);
        candidate.setStatus(status);

        candidate.setLabeledByUserId(
                principal == null
                        ? null
                        : principal.getUserId()
        );

        candidate.setLabeledAtUtc(
                ChatTrainingCandidate.STATUS_PENDING.equals(status)
                        ? null
                        : TimeUtil.now()
        );

        return toResponse(
                candidates.save(candidate)
        );
    }

    // ------------------------------------------------------------------
    // Danh sách nhãn
    // ------------------------------------------------------------------

    public List<IntentOptionResponse> intents() {

        List<IntentOptionResponse> options =
                new ArrayList<>();

        for (Intent intent : Intent.values()) {

            options.add(
                    new IntentOptionResponse(
                            intent.name(),
                            intent.group(),
                            policy.describe(intent)
                    )
            );
        }

        return options;
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    /**
     * JSONL các ứng viên {@code LABELED} – dán vào
     * {@code src/main/resources/chatbot/seed_from_logs.jsonl} rồi chạy
     * {@code mvn exec:java@train}.
     *
     * <p>Đúng schema {@code DatasetGenerator.Sample}: {@code text, intent, group}
     * với {@code group = CAND#id} để chống rò rỉ train/val/test.
     */
    public String exportJsonl() {

        StringBuilder builder =
                new StringBuilder();

        List<ChatTrainingCandidate> all =
                candidates.findAll(
                        Sort.by(Sort.Direction.ASC, "id")
                );

        int written = 0;

        for (ChatTrainingCandidate candidate : all) {

            if (!ChatTrainingCandidate.STATUS_LABELED.equals(
                    candidate.getStatus()
            )) {
                continue;
            }

            if (candidate.getLabel() == null
                    || candidate.getLabel().isBlank()) {
                continue;
            }

            if (written >= MAX_EXPORT) {
                break;
            }

            if (builder.length() > 0) {
                builder.append('\n');
            }

            builder.append(toJsonl(candidate));
            written++;
        }

        if (builder.length() > 0) {
            builder.append('\n');
        }

        log.info(
                "Export {} dòng seed_from_logs.jsonl",
                written
        );

        return builder.toString();
    }

    private String toJsonl(
            ChatTrainingCandidate candidate
    ) {

        Map<String, String> line =
                new LinkedHashMap<>();

        line.put("text", candidate.getText());
        line.put("intent", candidate.getLabel());
        line.put("group", "CAND#" + candidate.getId());

        try {
            return objectMapper.writeValueAsString(line);

        } catch (JsonProcessingException exception) {

            throw new IllegalStateException(
                    "Không serialize được dòng JSONL",
                    exception
            );
        }
    }

    // ------------------------------------------------------------------
    // Thống kê
    // ------------------------------------------------------------------

    public ChatbotStatsResponse stats() {

        Instant since =
                TimeUtil.now()
                        .minus(24, ChronoUnit.HOURS);

        List<ChatMessage> userMessages =
                messages.findByRoleAndCreatedAtUtcAfter(
                        ChatMessage.ROLE_USER,
                        since
                );

        List<ChatMessage> botReplies =
                messages.findByRoleAndCreatedAtUtcAfter(
                        ChatMessage.ROLE_BOT,
                        since
                );

        long fallback =
                botReplies.stream()
                        .filter(this::isFallback)
                        .count();

        long thumbsUp = 0;
        long thumbsDown = 0;

        Map<String, long[]> feedbackByIntentMap =
                new HashMap<>();

        for (ChatMessage message :
                messages.findTop500ByFeedbackNotNullOrderByIdDesc()) {

            boolean up =
                    ChatMessage.FEEDBACK_UP.equals(
                            message.getFeedback()
                    );

            boolean down =
                    ChatMessage.FEEDBACK_DOWN.equals(
                            message.getFeedback()
                    );

            if (up) {
                thumbsUp++;
            }

            if (down) {
                thumbsDown++;
            }

            String key =
                    message.getIntent() == null
                            ? "UNKNOWN"
                            : message.getIntent();

            long[] counts =
                    feedbackByIntentMap.computeIfAbsent(
                            key,
                            ignored -> new long[2]
                    );

            if (up) {
                counts[0]++;
            }

            if (down) {
                counts[1]++;
            }
        }

        List<FeedbackCountResponse> feedbackByIntent =
                feedbackByIntentMap.entrySet()
                        .stream()
                        .map(
                                entry ->
                                        new FeedbackCountResponse(
                                                entry.getKey(),
                                                entry.getValue()[0],
                                                entry.getValue()[1],
                                                rate(
                                                        entry.getValue()[1],
                                                        entry.getValue()[0]
                                                                + entry.getValue()[1]
                                                )
                                        )
                        )
                        .sorted(
                                Comparator.comparingLong(
                                                FeedbackCountResponse::down
                                        )
                                        .reversed()
                                        .thenComparing(
                                                FeedbackCountResponse::intent
                                        )
                        )
                        .limit(10)
                        .toList();

        List<String> topFallbackTexts =
                candidates.findTop20ByStatusOrderByCreatedAtUtcDesc(
                                ChatTrainingCandidate.STATUS_PENDING
                        )
                        .stream()
                        .map(ChatTrainingCandidate::getText)
                        .toList();

        return new ChatbotStatsResponse(
                userMessages.size(),
                botReplies.size(),
                fallback,
                rate(fallback, botReplies.size()),
                candidates.countByStatus(
                        ChatTrainingCandidate.STATUS_PENDING
                ),
                candidates.countByStatus(
                        ChatTrainingCandidate.STATUS_LABELED
                ),
                candidates.countByStatus(
                        ChatTrainingCandidate.STATUS_REJECTED
                ),
                thumbsUp,
                thumbsDown,
                rate(thumbsDown, thumbsUp + thumbsDown),
                feedbackByIntent,
                topFallbackTexts
        );
    }

    /**
     * Fallback = dự đoán {@code OUT_OF_SCOPE} hoặc độ tin cậy dưới
     * {@code threshold-clarify} – đúng nhánh {@code DialogueManager.gate}
     * trả {@code fallback(...)}.
     */
    private boolean isFallback(
            ChatMessage message
    ) {

        if (Intent.OUT_OF_SCOPE.name().equals(
                message.getIntent()
        )) {
            return true;
        }

        BigDecimal confidence =
                message.getConfidence();

        return confidence != null
                && confidence.doubleValue() < thresholdClarify;
    }

    // ------------------------------------------------------------------
    // Hỗ trợ
    // ------------------------------------------------------------------

    private String normalizeStatus(
            String status
    ) {

        if (status == null || status.isBlank()) {
            return null;
        }

        String normalized =
                status.trim().toUpperCase();

        if (!STATUSES.contains(normalized)) {

            throw new BadRequestException(
                    "candidate_status_invalid",
                    "Trạng thái không hợp lệ: " + status
            );
        }

        return normalized;
    }

    private boolean isKnownIntent(
            String label
    ) {

        for (Intent intent : Intent.values()) {

            if (intent.name().equals(label)) {
                return true;
            }
        }

        return false;
    }

    private double rate(
            long part,
            long whole
    ) {

        if (whole <= 0) {
            return 0.0;
        }

        return Math.round(
                (double) part * 10000.0 / (double) whole
        ) / 10000.0;
    }

    private CandidateResponse toResponse(
            ChatTrainingCandidate candidate
    ) {

        return new CandidateResponse(
                candidate.getId(),
                candidate.getText(),
                candidate.getPredictedIntent(),
                candidate.getConfidence(),
                candidate.getLabel(),
                candidate.getStatus(),
                candidate.getCreatedAtUtc(),
                candidate.getLabeledAtUtc()
        );
    }
}
