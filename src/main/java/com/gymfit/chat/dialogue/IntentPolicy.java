package com.gymfit.chat.dialogue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.user.RoleCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ma trận quyền intent × role và câu hỏi làm rõ.
 * <p>Đọc từ {@code chatbot/intents.json} — <b>không</b> hardcode ma trận trong Java.
 */
@Component
@Slf4j
public class IntentPolicy {

    public static final String RESOURCE =
            "chatbot/intents.json";

    /** intent → role được phép */
    private final Map<Intent, Set<RoleCode>> allowed =
            new EnumMap<>(Intent.class);

    /** intent → mô tả tiếng Việt (dùng cho câu hỏi làm rõ) */
    private final Map<Intent, String> descriptions =
            new EnumMap<>(Intent.class);

    @PostConstruct
    void load() {

        Map<Intent, Set<RoleCode>> loadedRoles =
                new EnumMap<>(Intent.class);

        Map<Intent, String> loadedDescriptions =
                new EnumMap<>(Intent.class);

        try (InputStream input =
                     new ClassPathResource(
                             RESOURCE
                     ).getInputStream()) {

            JsonNode root =
                    new ObjectMapper()
                            .readTree(input);

            root.fields()
                    .forEachRemaining(entry -> {

                        Intent intent;

                        try {
                            intent =
                                    Intent.valueOf(
                                            entry.getKey()
                                    );
                        } catch (IllegalArgumentException exception) {
                            log.warn(
                                    "intents.json có intent lạ: {}",
                                    entry.getKey()
                            );
                            return;
                        }

                        Set<RoleCode> roles =
                                EnumSet.noneOf(
                                        RoleCode.class
                                );

                        JsonNode roleNode =
                                entry.getValue()
                                        .path("roles");

                        roleNode.forEach(value ->
                                roles.add(
                                        RoleCode.valueOf(
                                                value.asText()
                                        )
                                )
                        );

                        loadedRoles.put(
                                intent,
                                roles
                        );

                        loadedDescriptions.put(
                                intent,
                                entry.getValue()
                                        .path("desc")
                                        .asText(intent.name())
                        );
                    });

        } catch (Exception exception) {

            log.error(
                    "Không đọc được intents.json: {}",
                    exception.getMessage()
            );

            throw new IllegalStateException(
                    "Không đọc được " + RESOURCE,
                    exception
            );
        }

        // Intent chưa khai báo trong file → không role nào được phép (fail-safe).
        for (Intent intent : Intent.values()) {
            loadedRoles.putIfAbsent(
                    intent,
                    EnumSet.noneOf(
                            RoleCode.class
                    )
            );

            loadedDescriptions.putIfAbsent(
                    intent,
                    intent.name()
            );
        }

        this.allowed.putAll(loadedRoles);
        this.descriptions.putAll(loadedDescriptions);
    }

    public boolean isAllowed(
            RoleCode role,
            Intent intent
    ) {
        if (role == null
                || intent == null) {
            return false;
        }

        return allowed.getOrDefault(
                intent,
                Set.of()
        ).contains(role);
    }

    /**
     * Đổi intent theo role trước khi kiểm quyền.
     * <ul>
     *     <li>Nhân viên nói "lịch của tôi" → xem lịch hôm nay của chi nhánh.</li>
     *     <li>Hội viên nói "lịch hôm nay" → xem lịch của chính mình (lọc hôm nay).</li>
     * </ul>
     */
    public Intent remap(
            RoleCode role,
            Intent intent
    ) {
        if (intent == null) {
            return null;
        }

        if (role == RoleCode.MEMBER
                && intent == Intent.BOOKINGS_TODAY) {
            return Intent.MY_BOOKINGS;
        }

        if (role != RoleCode.MEMBER
                && intent == Intent.MY_BOOKINGS) {
            return Intent.BOOKINGS_TODAY;
        }

        return intent;
    }

    public String describe(
            Intent intent
    ) {
        if (intent == null) {
            return "";
        }

        return descriptions.getOrDefault(
                intent,
                intent.name()
        );
    }

    /**
     * 3–4 gợi ý nhanh phù hợp vai trò, dùng cho câu chào và câu từ chối.
     */
    public List<String> suggestionsFor(
            RoleCode role
    ) {
        if (role == null) {
            return List.of();
        }

        if (role == RoleCode.MEMBER) {
            return List.of(
                    "Gói của tôi",
                    "Đặt lịch tập",
                    "Lịch sắp tới",
                    "Hủy lịch của tôi"
            );
        }

        if (role == RoleCode.BRANCH_MANAGER) {
            return List.of(
                    "Tổng quan hôm nay",
                    "Doanh thu tháng này",
                    "Hàng sắp hết",
                    "Check-in bị từ chối hôm nay"
            );
        }

        return List.of(
                "Tổng quan hệ thống",
                "Doanh thu tháng này",
                "Nhật ký hệ thống",
                "Chi nhánh nào đang hoạt động"
        );
    }

    /** Danh sách intent mà role được phép (dùng cho API admin). */
    public Map<Intent, String> allowedIntents(
            RoleCode role
    ) {
        Map<Intent, String> result =
                new LinkedHashMap<>();

        List<Intent> sorted =
                new ArrayList<>(
                        allowed.keySet()
                );

        sorted.sort(
                java.util.Comparator.comparing(
                        Intent::name
                )
        );

        for (Intent intent : sorted) {

            if (isAllowed(
                    role,
                    intent
            )) {
                result.put(
                        intent,
                        describe(intent)
                );
            }
        }

        return result;
    }

}