package com.gymfit.chat.dialogue.handler;

import com.gymfit.audit.AuditService;
import com.gymfit.booking.BookingService;
import com.gymfit.booking.BookingStatus;
import com.gymfit.booking.dto.BookingResponse;
import com.gymfit.branch.BranchService;
import com.gymfit.branch.BranchStatus;
import com.gymfit.branch.FacilityService;
import com.gymfit.branch.FacilityStatus;
import com.gymfit.branch.ServiceCode;
import com.gymfit.branch.dto.BranchResponse;
import com.gymfit.branch.dto.BranchServiceResponse;
import com.gymfit.branch.dto.OperatingHourResponse;
import com.gymfit.checkin.CheckInService;
import com.gymfit.checkin.dto.CheckInResponse;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlu.NormalizedText;
import com.gymfit.chat.nlu.TextNormalizer;
import com.gymfit.chat.nlu.entity.Entities;
import com.gymfit.chat.nlu.entity.EntityExtractor;
import com.gymfit.chat.nlu.entity.GazetteerProvider;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.common.error.ApiException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.inventory.InventoryService;
import com.gymfit.inventory.dto.InventoryResponse;
import com.gymfit.membership.MembershipService;
import com.gymfit.membership.dto.MembershipResponse;
import com.gymfit.order.OrderService;
import com.gymfit.order.dto.OrderResponse;
import com.gymfit.plan.PlanManagementService;
import com.gymfit.plan.PlanStatus;
import com.gymfit.plan.PlanTier;
import com.gymfit.plan.dto.PlanResponse;
import com.gymfit.product.ProductService;
import com.gymfit.product.ProductStatus;
import com.gymfit.product.dto.ProductResponse;
import com.gymfit.report.ReportService;
import com.gymfit.report.dto.DashboardResponse;
import com.gymfit.report.dto.RevenueReportResponse;
import com.gymfit.report.dto.ServiceReportResponse;
import com.gymfit.user.AppUser;
import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Handler chỉ đọc — mock service, không cần DB.
 */
class HandlersTest {

    private static final LocalDate TODAY =
            LocalDate.of(2026, 10, 3);

    private BranchService branchService;
    private FacilityService facilityService;
    private PlanManagementService planService;
    private ProductService productService;
    private MembershipService membershipService;
    private BookingService bookingService;
    private CheckInService checkInService;
    private OrderService orderService;
    private ReportService reportService;
    private InventoryService inventoryService;
    private AuditService auditService;
    private ResponseTemplates templates;
    private ChatPipelineHolder holder;

    /** Giữ pipeline dùng chung để dựng context. */
    private record ChatPipelineHolder(
            TextNormalizer normalizer,
            EntityExtractor extractor
    ) {
    }

    @BeforeEach
    void setUp() {
        branchService =
                mock(BranchService.class);

        facilityService =
                mock(FacilityService.class);

        planService =
                mock(PlanManagementService.class);

        productService =
                mock(ProductService.class);

        membershipService =
                mock(MembershipService.class);

        bookingService =
                mock(BookingService.class);

        checkInService =
                mock(CheckInService.class);

        orderService =
                mock(OrderService.class);

        reportService =
                mock(ReportService.class);

        inventoryService =
                mock(InventoryService.class);

        auditService =
                mock(AuditService.class);

        templates =
                new ResponseTemplates();

        templates.load();

        TextNormalizer normalizer =
                new TextNormalizer();

        normalizer.load();

        EntityExtractor extractor =
                new EntityExtractor(
                        new GazetteerProvider() {
                            @Override
                            public Map<String, Long> branchAliases() {
                                return Map.of(
                                        "q1", 1L,
                                        "q7", 2L,
                                        "td", 3L,
                                        "bt", 4L
                                );
                            }

                            @Override
                            public Map<String, String> productAliases() {
                                return Map.of();
                            }
                        },
                        new com.gymfit.chat.nlu.DateTimeParser()
                );

        extractor.load();

        holder =
                new ChatPipelineHolder(
                        normalizer,
                        extractor
                );
    }

    // ------------------------------------------------------------------
    // Tiện ích
    // ------------------------------------------------------------------

    private static AppPrincipal principal(
            RoleCode role,
            Long branchId,
            Long memberId
    ) {

        AppUser user =
                AppUser.builder()
                        .id(1L)
                        .fullName("Nguyễn Văn A")
                        .email("a@gymfit.local")
                        .passwordHash("x")
                        .roleCode(role)
                        .status(UserStatus.ACTIVE)
                        .branchId(branchId)
                        .memberId(memberId)
                        .build();

        return new AppPrincipal(
                user
        );
    }

    private HandlerContext context(
            AppPrincipal principal,
            Intent intent,
            String raw
    ) {

        NormalizedText text =
                holder.normalizer()
                        .normalize(raw);

        Entities entities =
                holder.extractor()
                        .extract(
                                text,
                                TODAY
                        );

        return new HandlerContext(
                principal,
                intent,
                entities,
                text,
                raw,
                TODAY,
                Map.of()
        );
    }

    private static BranchResponse branch(
            Long id,
            String code,
            String name
    ) {
        return new BranchResponse(
                id,
                code,
                name,
                "Địa chỉ " + code,
                "0900000000",
                BranchStatus.ACTIVE,
                "Asia/Ho_Chi_Minh",
                Instant.now(),
                Instant.now()
        );
    }

    private static PlanResponse plan(
            Long id,
            String name,
            PlanTier tier,
            int days,
            long price,
            ServiceCode... services
    ) {
        return new PlanResponse(
                id,
                1L,
                "PLAN_" + id,
                name,
                tier,
                days,
                BigDecimal.valueOf(price),
                "Mô tả " + name,
                PlanStatus.ACTIVE,
                Set.of(services),
                Instant.now(),
                Instant.now()
        );
    }

    // ==================================================================
    // SmallTalk
    // ==================================================================

    @Test
    @DisplayName("Chào hội viên và nhân viên dùng template khác nhau")
    void greetingPerRole() {

        SmallTalkHandler handler =
                new SmallTalkHandler(
                        templates,
                        new com.gymfit.chat.dialogue.IntentPolicy()
                );

        String member =
                handler.handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                null,
                                1L
                                ),
                                Intent.GREETING,
                                "xin chào"
                        )
                )
                .message();

        String staff =
                handler.handle(
                        context(
                                principal(
                                        RoleCode.BRANCH_MANAGER,
                                1L,
                                null
                                ),
                                Intent.GREETING,
                                "xin chào"
                        )
                )
                .message();

        assertFalse(
                member.equals(
                        staff
                ),
                "Hội viên và nhân viên phải khác câu chào"
        );

        assertFalse(
                member.contains(
                        "doanh thu"
                )
        );
    }

    @Test
    @DisplayName("OUT_OF_SCOPE trả fallback + gợi ý theo vai trò")
    void outOfScopeSuggests() {

        SmallTalkHandler handler =
                new SmallTalkHandler(
                        templates,
                        new com.gymfit.chat.dialogue.IntentPolicy()
                );

        var response =
                handler.handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                null,
                                1L
                                ),
                                Intent.OUT_OF_SCOPE,
                                "giá vàng hôm nay"
                        )
                );

        assertFalse(
                response.suggestions()
                        .isEmpty()
        );

        assertTrue(
                response.suggestions()
                        .stream()
                        .anyMatch(
                                suggestion ->
                                        suggestion.label()
                                                .contains(
                                                        "Gói"
                                                )
                        )
        );
    }

    // ==================================================================
    // BranchInfo
    // ==================================================================

    @Test
    @DisplayName("Giờ mở cửa: hiển thị đúng số ngày có dữ liệu")
    void operatingHours() {

        when(branchService.list(
                BranchStatus.ACTIVE
        )).thenReturn(
                List.of(
                        branch(
                                1L,
                                "Q1",
                                "Quận 1"
                        )
                )
        );

        when(branchService.getOperatingHours(
                1L
        )).thenReturn(
                List.of(
                        new OperatingHourResponse(
                                1L,
                                1L,
                                1,
                                LocalTime.of(
                                        5,
                                        30
                                ),
                                LocalTime.of(
                                        22,
                                        0
                                )
                        )
                )
        );

        BranchInfoHandler handler =
                handler();

        String message =
                handler.handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                null,
                                1L
                                ),
                                Intent.OPERATING_HOURS,
                                "giờ mở cửa"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "05:30"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "22:00"
                ),
                message
        );
    }

    @Test
    @DisplayName("Chi nhánh không có dữ liệu → câu 'chưa có thông tin', không ném lỗi")
    void operatingHoursEmpty() {

        when(branchService.list(
                BranchStatus.ACTIVE
        )).thenReturn(
                List.of(
                        branch(
                                1L,
                                "Q1",
                                "Quận 1"
                        )
                )
        );

        when(branchService.getOperatingHours(
                1L
        )).thenReturn(
                List.of()
        );

        String message =
                handler().handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                null,
                                1L
                                ),
                                Intent.OPERATING_HOURS,
                                "giờ mở cửa"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "chưa có thông tin"
                ),
                message
        );
    }

    @Test
    @DisplayName("Danh sách rỗng → 'chưa có dữ liệu'")
    void emptyBranches() {

        when(branchService.list(
                BranchStatus.ACTIVE
        )).thenReturn(
                List.of()
        );

        String message =
                handler().handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                null,
                                1L
                                ),
                                Intent.BRANCH_INFO,
                                "chi nhánh ở đâu"
                        )
                )
                .message();

        assertEquals(
                "Hiện tại mình chưa có dữ liệu cho phần này.",
                message
        );
    }

    @Test
    @DisplayName("Chi nhánh nói ra không tồn tại → không bịa, liệt kê chi nhánh thật")
    void unknownBranchNotFabricated() {

        when(branchService.list(
                BranchStatus.ACTIVE
        )).thenReturn(
                List.of(
                        branch(
                                1L,
                                "Q1",
                                "Quận 1"
                        )
                )
        );

        String message =
                handler().handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                null,
                                1L
                                ),
                                Intent.BRANCH_INFO,
                                "chi nhánh q9"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "Quận 1"
                ),
                message
        );

        assertFalse(
                message.contains(
                        "Q9"
                ),
                "Không được bịa chi nhánh Q9: " + message
        );
    }

    @Test
    @DisplayName("Dịch vụ chi nhánh: lấy từ BranchService.getServices")
    void branchServices() {

        when(branchService.list(
                BranchStatus.ACTIVE
        )).thenReturn(
                List.of(
                        branch(
                                1L,
                                "Q1",
                                "Quận 1"
                        )
                )
        );

        when(branchService.getServices(
                1L
        )).thenReturn(
                List.of(
                        new BranchServiceResponse(
                                1L,
                                ServiceCode.GYM,
                                60,
                                20,
                                true
                        )
                )
        );

        String message =
                handler().handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                null,
                                1L
                                ),
                                Intent.LIST_SERVICES,
                                "dịch vụ của q1"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "Gym"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "60 phút"
                ),
                message
        );
    }

    @Test
    @DisplayName("ApiException từ service → trả message tiếng Việt, không stack trace")
    void apiExceptionBecomesFriendlyMessage() {

        when(branchService.list(
                BranchStatus.ACTIVE
        )).thenThrow(
                new ForbiddenException(
                        "forbidden",
                        "Bạn không có quyền xem chi nhánh này"
                )
        );

        String message =
                handler().handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                null,
                                1L
                                ),
                                Intent.BRANCH_INFO,
                                "chi nhánh q1"
                        )
                )
                .message();

        assertEquals(
                "Bạn không có quyền xem chi nhánh này",
                message
        );

        assertFalse(
                message.contains(
                        "Exception"
                )
        );
    }

    private BranchInfoHandler handler() {
        return new BranchInfoHandler(
                branchService,
                facilityService,
                templates,
                new BranchSupport(
                        membershipService
                )
        );
    }

    // ==================================================================
    // Plan
    // ==================================================================

    @Test
    @DisplayName("LIST_PLANS hiển thị tên – giá – ngày – dịch vụ")
    void listPlans() {

        when(planService.list(
                any(),
                any(),
                eq(PlanStatus.ACTIVE)
        )).thenReturn(
                List.of(
                        plan(
                                1L,
                                "Gói 3 tháng",
                                PlanTier.STANDARD,
                                90,
                                900_000,
                                ServiceCode.GYM,
                                ServiceCode.BOXING
                        ),
                        plan(
                                2L,
                                "Gói 1 tháng",
                                PlanTier.BASIC,
                                30,
                                300_000,
                                ServiceCode.GYM
                        )
                )
        );

        String message =
                new PlanHandler(
                        planService,
                        templates,
                        new BranchSupport(
                                membershipService
                        )
                )
                        .handle(
                                context(
                                        principal(
                                                RoleCode.MEMBER,
                                                null,
                                                1L
                                        ),
                                        Intent.LIST_PLANS,
                                        "có gói tập nào"
                                )
                        )
                        .message();

        assertTrue(
                message.contains(
                        "Gói 3 tháng"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "900.000đ"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "Gym"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "Boxing"
                ),
                message
        );
    }

    @Test
    @DisplayName("LIST_PLANS rỗng → 'chưa có dữ liệu'")
    void listPlansEmpty() {

        when(planService.list(
                any(),
                any(),
                eq(PlanStatus.ACTIVE)
        )).thenReturn(
                List.of()
        );

        String message =
                new PlanHandler(
                                planService,
                                templates,
                                new BranchSupport(
                                        membershipService
                                )
                        )
                        .handle(
                                context(
                                        principal(
                                                RoleCode.MEMBER,
                                                null,
                                                1L
                                        ),
                                        Intent.LIST_PLANS,
                                        "có gói nào"
                                )
                        )
                        .message();

        assertEquals(
                "Hiện tại mình chưa có dữ liệu cho phần này.",
                message
        );
    }

    @Test
    @DisplayName("PLAN_COMPARE có giá/ngày và kết luận")
    void comparePlans() {

        when(planService.list(
                any(),
                any(),
                eq(PlanStatus.ACTIVE)
        )).thenReturn(
                List.of(
                        plan(
                                1L,
                                "Gói rẻ",
                                PlanTier.BASIC,
                                30,
                                300_000,
                                ServiceCode.GYM
                        ),
                        plan(
                                2L,
                                "Gói đắt",
                                PlanTier.PREMIUM,
                                90,
                                900_000,
                                ServiceCode.GYM
                        )
                )
        );

        String message =
                new PlanHandler(
                                planService,
                                templates,
                                new BranchSupport(
                                        membershipService
                                )
                        )
                        .handle(
                                context(
                                        principal(
                                                RoleCode.MEMBER,
                                                null,
                                                1L
                                        ),
                                        Intent.PLAN_COMPARE,
                                        "so sánh các gói"
                                )
                        )
                        .message();

        assertTrue(
                message.contains(
                        "Giá/ngày"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "Gói rẻ hơn"
                ),
                message
        );
    }

    @Test
    @DisplayName("PLAN_RECOMMEND thiếu cả dịch vụ lẫn ngân sách → hỏi lại")
    void recommendNeedsInput() {

        when(planService.list(
                any(),
                any(),
                eq(PlanStatus.ACTIVE)
        )).thenReturn(
                List.of(
                        plan(
                                1L,
                                "Gói A",
                                PlanTier.BASIC,
                                30,
                                300_000,
                                ServiceCode.GYM
                        )
                )
        );

        var response =
                new PlanHandler(
                                planService,
                                templates,
                                new BranchSupport(
                                        membershipService
                                )
                        )
                        .handle(
                                context(
                                        principal(
                                                RoleCode.MEMBER,
                                                null,
                                                1L
                                        ),
                                        Intent.PLAN_RECOMMEND,
                                        "gói nào phù hợp"
                                )
                        );

        assertTrue(
                response.message()
                        .contains(
                                "dịch vụ nào"
                        ),
                response.message()
        );

        assertEquals(
                4,
                response.suggestions()
                        .size()
        );
    }

    @Test
    @DisplayName("PLAN_RECOMMEND lọc theo ngân sách và dịch vụ")
    void recommendFilters() {

        when(planService.list(
                any(),
                any(),
                eq(PlanStatus.ACTIVE)
        )).thenReturn(
                List.of(
                        plan(
                                1L,
                                "Gói rẻ gym",
                                PlanTier.BASIC,
                                30,
                                300_000,
                                ServiceCode.GYM
                        ),
                        plan(
                                2L,
                                "Gói đắt boxing",
                                PlanTier.PREMIUM,
                                90,
                                2_000_000,
                                ServiceCode.BOXING
                        )
                )
        );

        String message =
                new PlanHandler(
                                planService,
                                templates,
                                new BranchSupport(
                                        membershipService
                                )
                        )
                        .handle(
                                context(
                                        principal(
                                                RoleCode.MEMBER,
                                                null,
                                                1L
                                        ),
                                        Intent.PLAN_RECOMMEND,
                                        "gói nào rẻ nhất dưới 600k"
                                )
                        )
                        .message();

        assertTrue(
                message.contains(
                        "Gói rẻ gym"
                ),
                message
        );

        assertFalse(
                message.contains(
                        "Gói đắt boxing"
                ),
                "Gói vượt ngân sách phải bị loại: " + message
        );
    }

    // ==================================================================
    // Product
    // ==================================================================

    @Test
    @DisplayName("LIST_PRODUCTS nhóm theo danh mục, giới hạn 10 dòng")
    void listProducts() {

        when(productService.list(
                ProductStatus.ACTIVE
        )).thenReturn(
                List.of(
                        new ProductResponse(
                                1L,
                                "WATER",
                                "Nước suối 500ml",
                                "Nước",
                                BigDecimal.valueOf(
                                        10_000),
                                ProductStatus.ACTIVE,
                                Instant.now(),
                                Instant.now()
                        )
                )
        );

        String message =
                new ProductHandler(
                                productService,
                                templates
                        )
                        .handle(
                                context(
                                        principal(
                                                RoleCode.MEMBER,
                                                null,
                                                1L
                                        ),
                                        Intent.LIST_PRODUCTS,
                                        "có bán nước không"
                                )
                        )
                        .message();

        assertTrue(
                message.contains(
                        "Nước suối 500ml"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "10.000đ"
                ),
                message
        );
    }

    @Test
    @DisplayName("LIST_PRODUCTS rỗng → 'chưa có dữ liệu'")
    void listProductsEmpty() {

        when(productService.list(
                ProductStatus.ACTIVE
        )).thenReturn(
                List.of()
        );

        String message =
                new ProductHandler(
                                productService,
                                templates
                        )
                        .handle(
                                context(
                                        principal(
                                                RoleCode.MEMBER,
                                                null,
                                                1L
                                        ),
                                        Intent.LIST_PRODUCTS,
                                        "có bán gì"
                                )
                        )
                        .message();

        assertEquals(
                "Hiện tại mình chưa có dữ liệu cho phần này.",
                message
        );
    }

    // ==================================================================
    // Member
    // ==================================================================

    @Test
    @DisplayName("MY_MEMBERSHIP không có hội viên → 'chưa có gói tập'")
    void membershipWithoutMember() {

        String message =
                memberHandler().handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                        null,
                                        null
                                ),
                                Intent.MY_MEMBERSHIP,
                                "gói của tôi"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "chưa có gói tập"
                ),
                message
        );
    }

    @Test
    @DisplayName("MY_MEMBERSHIP hiển thị gói đang hoạt động")
    void membershipActive() {

        when(membershipService.current(
                any(),
                eq(1L)
        )).thenReturn(
                new MembershipResponse(
                        1L,
                        1L,
                        2L,
                        3L,
                        1L,
                        com.gymfit.membership.MembershipStatus.ACTIVE,
                        LocalDate.of(
                                2026,
                                9,
                                1
                        ),
                        LocalDate.of(
                                2026,
                                12,
                                1
                        ),
                        Instant.now(),
                        null,
                        null,
                        Set.of(ServiceCode.GYM),
                        Instant.now()
                )
        );

        String message =
                memberHandler().handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                        null,
                                        1L
                                ),
                                Intent.MY_MEMBERSHIP,
                                "gói của tôi"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "Gym"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "01/12/2026"
                ),
                message
        );
    }

    @Test
    @DisplayName("MY_BOOKINGS không có lịch sắp tới → câu rõ ràng")
    void bookingsNone() {

        when(bookingService.list(
                any()
        )).thenReturn(
                List.of()
        );

        String message =
                memberHandler().handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                        null,
                                        1L
                                ),
                                Intent.MY_BOOKINGS,
                                "lịch của tôi"
                        )
                )
                .message();

        assertEquals(
                "Bạn không có lịch sắp tới.",
                message
        );
    }

    @Test
    @DisplayName("MY_BOOKINGS chỉ lấy lịch CONFIRMED trong tương lai")
    void bookingsFiltersByStatusAndTime() {

        Instant now =
                Instant.now();

        when(bookingService.list(
                any()
        )).thenReturn(
                List.of(
                        new BookingResponse(
                                1L,
                                "BOOK_FUTURE",
                                1L,
                                1L,
                                1L,
                                ServiceCode.GYM,
                                1L,
                                BookingStatus.CONFIRMED,
                                now.plusSeconds(
                                        86_400
                                ),
                                now.plusSeconds(
                                        90_000
                                ),
                                null,
                                null,
                                1L,
                                now
                        ),
                        new BookingResponse(
                                2L,
                                "BOOK_CANCELLED",
                                1L,
                                1L,
                                1L,
                                ServiceCode.GYM,
                                1L,
                                BookingStatus.CANCELLED,
                                now.plusSeconds(
                                        86_400
                                ),
                                now.plusSeconds(
                                        90_000
                                ),
                                now,
                                "test",
                                1L,
                                now
                        ),
                        new BookingResponse(
                                3L,
                                "BOOK_PAST",
                                1L,
                                1L,
                                1L,
                                ServiceCode.GYM,
                                1L,
                                BookingStatus.CONFIRMED,
                                now.minusSeconds(
                                        86_400
                                ),
                                now.minusSeconds(
                                        80_000
                                ),
                                null,
                                null,
                                1L,
                                now
                        )
                )
        );

        String message =
                memberHandler().handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                        null,
                                        1L
                                ),
                                Intent.MY_BOOKINGS,
                                "lịch sắp tới của tôi"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "BOOK_FUTURE"
                ),
                message
        );

        assertFalse(
                message.contains(
                        "BOOK_CANCELLED"
                ),
                message
        );

        assertFalse(
                message.contains(
                        "BOOK_PAST"
                ),
                message
        );
    }

    @Test
    @DisplayName("MY_CHECKINS / MY_ORDERS rỗng → câu rõ ràng")
    void checkinsAndOrdersEmpty() {

        when(checkInService.list(
                any()
        )).thenReturn(
                List.of()
        );

        when(orderService.list(
                any()
        )).thenReturn(
                List.of()
        );

        assertEquals(
                "Bạn chưa có lần check-in nào.",
                memberHandler().handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                        null,
                                        1L
                                ),
                                Intent.MY_CHECKINS,
                                "lịch sử check-in"
                        )
                )
                .message()
        );

        assertEquals(
                "Bạn chưa có đơn hàng nào.",
                memberHandler().handle(
                        context(
                                principal(
                                        RoleCode.MEMBER,
                                        null,
                                        1L
                                ),
                                Intent.MY_ORDERS,
                                "đơn hàng của tôi"
                        )
                )
                .message()
        );
    }

    private MemberInfoHandler memberHandler() {
        return new MemberInfoHandler(
                membershipService,
                bookingService,
                checkInService,
                orderService,
                templates
        );
    }

    // ==================================================================
    // Audit — kiểm quyền
    // ==================================================================

    @Test
    @DisplayName("AuditHandler với MANAGER KHÔNG gọi AuditService")
    void auditBlockedForManager() {

        AuditHandler handler =
                new AuditHandler(
                        auditService,
                        templates
                );

        String message =
                handler.handle(
                        context(
                                principal(
                                        RoleCode.BRANCH_MANAGER,
                                        1L,
                                        null
                                ),
                                Intent.AUDIT_RECENT,
                                "xem nhật ký hệ thống"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "không có quyền"
                ),
                message
        );

        verify(
                auditService,
                never()
        ).list(any());
    }

    @Test
    @DisplayName("AuditHandler với MEMBER cũng không gọi AuditService")
    void auditBlockedForMember() {

        AuditHandler handler =
                new AuditHandler(
                        auditService,
                        templates
                );

        handler.handle(
                context(
                        principal(
                                RoleCode.MEMBER,
                                null,
                                1L
                        ),
                        Intent.AUDIT_RECENT,
                        "xem nhật ký hệ thống"
                )
        );

        verify(
                auditService,
                never()
        ).list(any());
    }

    @Test
    @DisplayName("AuditHandler với ADMIN thì được gọi service")
    void auditAllowedForAdmin() {

        when(auditService.list(
                any()
        )).thenReturn(
                List.of()
        );

        String message =
                new AuditHandler(
                                auditService,
                                templates
                        )
                        .handle(
                                context(
                                        principal(
                                                RoleCode.ADMIN,
                                                null,
                                                null
                                        ),
                                        Intent.AUDIT_RECENT,
                                        "xem nhật ký hệ thống"
                                )
                        )
                        .message();

        assertEquals(
                "Chưa có sự kiện nào được ghi nhận.",
                message
        );

        verify(
                auditService
        ).list(any());
    }

    // ==================================================================
    // Ops
    // ==================================================================

    @Test
    @DisplayName("REPORT_REVENUE dùng đúng số liệu mock")
    void revenueUsesMockData() {

        when(reportService.revenue(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(
                new RevenueReportResponse(
                        LocalDate.of(
                                2026,
                                10,
                                1
                        ),
                        TODAY,
                        1L,
                        BigDecimal.valueOf(
                                1_500_000
                        ),
                        BigDecimal.valueOf(
                                1_000_000
                        ),
                        BigDecimal.valueOf(
                                500_000
                        ),
                        12
                )
        );

        String message =
                opsHandler().handle(
                        context(
                                principal(
                                        RoleCode.BRANCH_MANAGER,
                                        1L,
                                        null
                                ),
                                Intent.REPORT_REVENUE,
                                "doanh thu tháng này"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "1.500.000đ"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "1.000.000đ"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "500.000đ"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "12"
                ),
                message
        );
    }

    @Test
    @DisplayName("ForbiddenException từ service → câu 'chỉ được xem chi nhánh của mình'")
    void revenueForbidden() {

        when(reportService.revenue(
                any(),
                any(),
                any(),
                any()
        )).thenThrow(
                new ForbiddenException(
                        "branch_scope",
                        "Bạn không có quyền xem chi nhánh khác"
                )
        );

        String message =
                opsHandler().handle(
                        context(
                                principal(
                                        RoleCode.BRANCH_MANAGER,
                                        1L,
                                        null
                                ),
                                Intent.REPORT_REVENUE,
                                "doanh thu chi nhánh q7"
                        )
                )
                .message();

        assertEquals(
                "Bạn chỉ được xem dữ liệu chi nhánh của mình.",
                message
        );
    }

    @Test
    @DisplayName("LOW_STOCK lọc theo ngưỡng cấu hình")
    void lowStockFilters() {

        OpsReportHandler handler =
                opsHandler();

        when(inventoryService.list(
                any(),
                any()
        )).thenReturn(
                List.of(
                        new InventoryResponse(
                                1L,
                                1L,
                                1L,
                                "WATER",
                                "Nước suối",
                                3,
                                Instant.now()
                        ),
                        new InventoryResponse(
                                2L,
                                1L,
                                2L,
                                "BAR",
                                "Protein bar",
                                50,
                                Instant.now()
                        )
                )
        );

        String message =
                handler.handle(
                        context(
                                principal(
                                        RoleCode.BRANCH_MANAGER,
                                        1L,
                                        null
                                ),
                                Intent.LOW_STOCK,
                                "hàng sắp hết"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "Nước suối"
                ),
                message
        );

        assertFalse(
                message.contains(
                        "Protein bar"
                ),
                "Sản phẩm còn nhiều không được liệt kê: " + message
        );
    }

    @Test
    @DisplayName("BOOKINGS_TODAY đếm theo trạng thái và dịch vụ")
    void bookingsToday() {

        Instant now =
                Instant.now();

        when(bookingService.list(
                any()
        )).thenReturn(
                List.of(
                        new BookingResponse(
                                1L,
                                "B1",
                                1L,
                                1L,
                                1L,
                                ServiceCode.GYM,
                                1L,
                                BookingStatus.CONFIRMED,
                                now,
                                now.plusSeconds(
                                        3600
                                ),
                                null,
                                null,
                                1L,
                                now
                        ),
                        new BookingResponse(
                                2L,
                                "B2",
                                1L,
                                1L,
                                1L,
                                ServiceCode.BOXING,
                                2L,
                                BookingStatus.CANCELLED,
                                now,
                                now.plusSeconds(
                                        3600
                                ),
                                now,
                                "test",
                                1L,
                                now
                        )
                )
        );

        String message =
                opsHandler().handle(
                        context(
                                principal(
                                        RoleCode.BRANCH_MANAGER,
                                        1L,
                                        null
                                ),
                                Intent.BOOKINGS_TODAY,
                                "lịch hôm nay"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "2 lượt"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "CONFIRMED=1"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "Gym=1"
                ),
                message
        );
    }

    @Test
    @DisplayName("CHECKINS_REJECTED gom theo lý do")
    void rejectedCheckIns() {

        Instant now =
                Instant.now();

        when(checkInService.list(
                any()
        )).thenReturn(
                List.of(
                        new CheckInResponse(
                                1L,
                                1L,
                                1L,
                                1L,
                                ServiceCode.GYM,
                                com.gymfit.checkin.CheckInMethod.QR,
                                com.gymfit.checkin.CheckInResult.REJECTED,
                                "NO_ACTIVE_MEMBERSHIP",
                                1L,
                                now
                        ),
                        new CheckInResponse(
                                2L,
                                2L,
                                2L,
                                1L,
                                ServiceCode.GYM,
                                com.gymfit.checkin.CheckInMethod.QR,
                                com.gymfit.checkin.CheckInResult.REJECTED,
                                "NO_ACTIVE_MEMBERSHIP",
                                1L,
                                now
                        ),
                        new CheckInResponse(
                                3L,
                                3L,
                                3L,
                                1L,
                                ServiceCode.GYM,
                                com.gymfit.checkin.CheckInMethod.QR,
                                com.gymfit.checkin.CheckInResult.REJECTED,
                                "DUPLICATE_CHECKIN",
                                1L,
                                now
                        )
                )
        );

        String message =
                opsHandler().handle(
                        context(
                                principal(
                                        RoleCode.BRANCH_MANAGER,
                                        1L,
                                        null
                                ),
                                Intent.CHECKINS_REJECTED,
                                "ca bị từ chối hôm nay"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "Chưa có gói tập đang hiệu lực: 2"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "Đã check-in dịch vụ này rồi: 1"
                ),
                message
        );
    }

    @Test
    @DisplayName("REPORT_DASHBOARD hiển thị đủ 5 chỉ số")
    void dashboard() {

        when(reportService.dashboard(
                any()
        )).thenReturn(
                new DashboardResponse(
                        BigDecimal.valueOf(
                                5_000_000
                        ),
                        120,
                        45,
                        60,
                        90
                )
        );

        String message =
                opsHandler().handle(
                        context(
                                principal(
                                        RoleCode.ADMIN,
                                        null,
                                        null
                                ),
                                Intent.REPORT_DASHBOARD,
                                "tổng quan hôm nay"
                        )
                )
                .message();

        assertTrue(
                message.contains(
                        "5.000.000đ"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "120"
                ),
                message
        );

        assertTrue(
                message.contains(
                        "toàn hệ thống"
                ),
                message
        );
    }

    private OpsReportHandler opsHandler() {

        OpsReportHandler handler =
                new OpsReportHandler(
                        reportService,
                        bookingService,
                        checkInService,
                        inventoryService,
                        templates
                );

        org.springframework.test.util.ReflectionTestUtils.setField(
                handler,
                "lowStockThreshold",
                10
        );

        return handler;
    }
}