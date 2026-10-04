package com.gymfit.chat.dialogue;

import com.gymfit.chat.nlu.Intent;
import com.gymfit.user.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ma trận quyền đúng bảng mục 3 của plan: 36 intent × 3 role.
 */
class IntentPolicyTest {

    private IntentPolicy policy;

    @BeforeEach
    void setUp() {
        policy =
                new IntentPolicy();

        policy.load();
    }

    /** intent → các role KHÔNG được phép (null = mọi role đều được phép). */
    private static Map<Intent, Set<RoleCode>> expectDenied() {
        return Map.ofEntries(
                Map.entry(
                        Intent.MY_MEMBERSHIP,
                        Set.of(
                                RoleCode.BRANCH_MANAGER,
                                RoleCode.ADMIN
                        )
                ),
                Map.entry(
                        Intent.MY_BOOKINGS,
                        Set.of(
                                RoleCode.BRANCH_MANAGER,
                                RoleCode.ADMIN
                        )
                ),
                Map.entry(
                        Intent.MY_CHECKINS,
                        Set.of(
                                RoleCode.BRANCH_MANAGER,
                                RoleCode.ADMIN
                        )
                ),
                Map.entry(
                        Intent.MY_ORDERS,
                        Set.of(
                                RoleCode.BRANCH_MANAGER,
                                RoleCode.ADMIN
                        )
                ),
                Map.entry(
                        Intent.BOOKING_CREATE,
                        Set.of(
                                RoleCode.BRANCH_MANAGER,
                                RoleCode.ADMIN
                        )
                ),
                Map.entry(
                        Intent.BOOKING_CANCEL,
                        Set.of(
                                RoleCode.BRANCH_MANAGER,
                                RoleCode.ADMIN
                        )
                ),
                Map.entry(
                        Intent.REPORT_DASHBOARD,
                        Set.of(RoleCode.MEMBER)
                ),
                Map.entry(
                        Intent.REPORT_REVENUE,
                        Set.of(RoleCode.MEMBER)
                ),
                Map.entry(
                        Intent.REPORT_SERVICE,
                        Set.of(RoleCode.MEMBER)
                ),
                Map.entry(
                        Intent.LOW_STOCK,
                        Set.of(RoleCode.MEMBER)
                ),
                Map.entry(
                        Intent.BOOKINGS_TODAY,
                        Set.of(RoleCode.MEMBER)
                ),
                Map.entry(
                        Intent.CHECKINS_REJECTED,
                        Set.of(RoleCode.MEMBER)
                ),
                Map.entry(
                        Intent.AUDIT_RECENT,
                        Set.of(
                                RoleCode.MEMBER,
                                RoleCode.BRANCH_MANAGER
                        )
                )
        );
    }

    @Test
    @DisplayName("37 intent (36 + FAQ_GENERAL gộp 6 FAQ)")
    void intentCount() {
        assertEquals(
                37,
                Intent.values().length
        );

        assertEquals(
                31,
                Intent.activeCount()
        );
    }

    @Test
    @DisplayName("Ma trận quyền: 36 intent × 3 role")
    void permissionMatrix() {

        Map<Intent, Set<RoleCode>> denied =
                expectDenied();

        for (RoleCode role : RoleCode.values()) {

            for (Intent intent : Intent.values()) {

                Set<RoleCode> forbidden =
                        denied.get(intent);

                boolean shouldBeAllowed =
                        forbidden == null
                                || !forbidden.contains(role);

                assertEquals(
                        shouldBeAllowed,
                        policy.isAllowed(
                                role,
                                intent
                        ),
                        role + " → " + intent
                );
            }
        }
    }

    @Test
    @DisplayName("Thông tin & FAQ & slot: cả 3 role đều được phép")
    void everyoneCanAskInfo() {
        List<Intent> shared =
                List.of(
                        Intent.GREETING,
                        Intent.THANKS,
                        Intent.GOODBYE,
                        Intent.HELP,
                        Intent.OUT_OF_SCOPE,
                        Intent.CONFIRM_YES,
                        Intent.CONFIRM_NO,
                        Intent.BRANCH_INFO,
                        Intent.OPERATING_HOURS,
                        Intent.LIST_SERVICES,
                        Intent.LIST_FACILITIES,
                        Intent.LIST_PLANS,
                        Intent.PLAN_DETAIL,
                        Intent.PLAN_COMPARE,
                        Intent.PLAN_RECOMMEND,
                        Intent.LIST_PRODUCTS,
                        Intent.FAQ_CANCEL_POLICY,
                        Intent.FAQ_BOOKING_RULES,
                        Intent.FAQ_CHECKIN_HOWTO,
                        Intent.FAQ_BUY_PLAN_HOWTO,
                        Intent.FAQ_CHECKIN_REJECTED,
                        Intent.FAQ_QR_HOWTO,
                        Intent.BOOKING_AVAILABILITY
                );

        assertEquals(
                23,
                shared.size()
        );

        for (Intent intent : shared) {

            for (RoleCode role : RoleCode.values()) {

                assertTrue(
                        policy.isAllowed(
                                role,
                                intent
                        ),
                        role + " → " + intent
                );
            }
        }
    }

    @Test
    @DisplayName("Remap: nhân viên nói MY_BOOKINGS → BOOKINGS_TODAY")
    void remapStaff() {

        assertEquals(
                Intent.BOOKINGS_TODAY,
                policy.remap(
                        RoleCode.BRANCH_MANAGER,
                        Intent.MY_BOOKINGS
                )
        );

        assertEquals(
                Intent.BOOKINGS_TODAY,
                policy.remap(
                        RoleCode.ADMIN,
                        Intent.MY_BOOKINGS
                )
        );
    }

    @Test
    @DisplayName("Remap: hội viên nói BOOKINGS_TODAY → MY_BOOKINGS")
    void remapMember() {

        assertEquals(
                Intent.MY_BOOKINGS,
                policy.remap(
                        RoleCode.MEMBER,
                        Intent.BOOKINGS_TODAY
                )
        );
    }

    @Test
    @DisplayName("Remap: intent khác giữ nguyên")
    void remapOthers() {

        assertEquals(
                Intent.REPORT_REVENUE,
                policy.remap(
                        RoleCode.MEMBER,
                        Intent.REPORT_REVENUE
                )
        );

        assertEquals(
                Intent.BOOKING_CREATE,
                policy.remap(
                        RoleCode.MEMBER,
                        Intent.BOOKING_CREATE
                )
        );

        assertEquals(
                Intent.MY_MEMBERSHIP,
                policy.remap(
                        RoleCode.MEMBER,
                        Intent.MY_MEMBERSHIP
                )
        );
    }

    @Test
    @DisplayName("Remap rồi mới kiểm quyền: manager nói 'lịch của tôi' không bị chặn")
    void remapBeforePermissionCheck() {
        Intent remapped =
                policy.remap(
                        RoleCode.BRANCH_MANAGER,
                        Intent.MY_BOOKINGS
                );

        assertTrue(
                policy.isAllowed(
                        RoleCode.BRANCH_MANAGER,
                        remapped
                )
        );
    }

    @Test
    @DisplayName("Gợi ý theo vai trò")
    void suggestionsPerRole() {
        assertEquals(
                4,
                policy.suggestionsFor(
                                RoleCode.MEMBER)
                        .size()
        );

        assertEquals(
                4,
                policy.suggestionsFor(
                                RoleCode.BRANCH_MANAGER)
                        .size()
        );

        assertEquals(
                4,
                policy.suggestionsFor(
                                RoleCode.ADMIN)
                        .size()
        );

        assertTrue(
                policy.suggestionsFor(
                                RoleCode.MEMBER)
                        .contains(
                                "Gói của tôi"
                        )
        );

        assertTrue(
                policy.suggestionsFor(
                                RoleCode.ADMIN)
                        .contains(
                                "Nhật ký hệ thống"
                        )
        );
    }

    @Test
    @DisplayName("Mọi intent đều có mô tả tiếng Việt")
    void everyIntentHasDescription() {
        for (Intent intent : Intent.values()) {

            String description =
                    policy.describe(intent);

            assertFalse(
                    description.isBlank(),
                    intent + " thiếu mô tả"
            );

            assertFalse(
                    description.equals(intent.name()),
                    intent + " chưa có mô tả riêng"
            );
        }
    }

    @Test
    @DisplayName("Role/intent null → không ném lỗi, trả false")
    void nullSafe() {

        assertFalse(
                policy.isAllowed(
                        null,
                        Intent.HELP
                )
        );

        assertFalse(
                policy.isAllowed(
                        RoleCode.MEMBER,
                        null
                )
        );

        assertEquals(
                List.of(),
                policy.suggestionsFor(null)
        );
    }

}