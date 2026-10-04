package com.gymfit.chat.dialogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.audit.AuditService;
import com.gymfit.audit.dto.AuditEventResponse;
import com.gymfit.booking.BookingService;
import com.gymfit.booking.BookingStatus;
import com.gymfit.booking.dto.AvailabilitySlotResponse;
import com.gymfit.booking.dto.BookingCreateRequest;
import com.gymfit.booking.dto.BookingResponse;
import com.gymfit.branch.BranchService;
import com.gymfit.branch.BranchStatus;
import com.gymfit.branch.FacilityService;
import com.gymfit.branch.FacilityStatus;
import com.gymfit.branch.FacilityType;
import com.gymfit.branch.ServiceCode;
import com.gymfit.branch.dto.BranchResponse;
import com.gymfit.branch.dto.BranchServiceResponse;
import com.gymfit.branch.dto.FacilityResponse;
import com.gymfit.branch.dto.OperatingHourResponse;
import com.gymfit.chat.dialogue.handler.AuditHandler;
import com.gymfit.chat.dialogue.handler.BookingFlowHandler;
import com.gymfit.chat.dialogue.handler.BranchInfoHandler;
import com.gymfit.chat.dialogue.handler.BranchSupport;
import com.gymfit.chat.dialogue.handler.FaqHandler;
import com.gymfit.chat.dialogue.handler.IntentHandler;
import com.gymfit.chat.dialogue.handler.MemberInfoHandler;
import com.gymfit.chat.dialogue.handler.OpsReportHandler;
import com.gymfit.chat.dialogue.handler.PlanHandler;
import com.gymfit.chat.dialogue.handler.ProductHandler;
import com.gymfit.chat.dialogue.handler.SmallTalkHandler;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.chat.nlu.ChatPipeline;
import com.gymfit.chat.nlu.DateTimeParser;
import com.gymfit.chat.nlu.IntentClassifier;
import com.gymfit.chat.nlu.TextNormalizer;
import com.gymfit.chat.nlu.entity.EntityExtractor;
import com.gymfit.chat.nlu.entity.GazetteerProvider;
import com.gymfit.chat.session.ChatMessage;
import com.gymfit.chat.session.ChatSession;
import com.gymfit.chat.session.ChatSessionService;
import com.gymfit.chat.session.RateLimiter;
import com.gymfit.checkin.CheckInMethod;
import com.gymfit.checkin.CheckInResult;
import com.gymfit.checkin.CheckInService;
import com.gymfit.checkin.dto.CheckInResponse;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.inventory.InventoryService;
import com.gymfit.inventory.dto.InventoryResponse;
import com.gymfit.membership.MembershipService;
import com.gymfit.membership.MembershipStatus;
import com.gymfit.membership.dto.MembershipResponse;
import com.gymfit.order.OrderService;
import com.gymfit.order.OrderStatus;
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
import org.springframework.core.io.DefaultResourceLoader;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Dữ liệu fixture cho {@link ScenarioRunnerTest} — plan V2, mục 7.1.
 *
 * <p><b>14 fixture</b> (yêu cầu ≥ 10): mỗi fixture cấu hình sẵn trạng thái
 * hội viên/chi nhánh/báo cáo để một kịch bản chỉ cần nêu {@code fixture}
 * trong JSON là có ngay "điều kiện dữ liệu" đúng.
 *
 * <p>Mọi service đều là Mockito mock; các lượt gọi thật được liệt kê dưới
 * dạng {@code <Interface>.<method>} qua {@link Harness#calls()} để
 * kịch bản khai {@code services_called} / {@code services_never}.
 */
final class ScenarioFixtures {

    /** Mốc thời gian mặc định khi kịch bản không ghi {@code now}. */
    static final Instant DEFAULT_NOW =
            LocalDateTime.of(2026, 10, 5, 9, 0)
                    .toInstant(ZoneOffset.ofHours(7));

    /** Chi nhánh dùng chung: Q1=1, Q7=2, TD=3, BT=4. */
    static final long Q1 = 1L;

    static final long Q7 = 2L;

    static final long TD = 3L;

    static final long BT = 4L;

    private ScenarioFixtures() {
    }

    // ------------------------------------------------------------------
    // Harness — toàn bộ mock + pipeline + DialogueManager thật
    // ------------------------------------------------------------------

    static final class Harness {

        /** Mock đã tạo, kèm nhãn {@code Interface} để tính {@link #calls()}. */
        private final List<Recorded> recorded =
                new ArrayList<>();

        final BranchService branchService =
                mockOf(BranchService.class);

        final FacilityService facilityService =
                mockOf(FacilityService.class);

        final PlanManagementService planService =
                mockOf(PlanManagementService.class);

        final ProductService productService =
                mockOf(ProductService.class);

        final MembershipService membershipService =
                mockOf(MembershipService.class);

        final BookingService bookingService =
                mockOf(BookingService.class);

        final CheckInService checkInService =
                mockOf(CheckInService.class);

        final OrderService orderService =
                mockOf(OrderService.class);

        final ReportService reportService =
                mockOf(ReportService.class);

        final InventoryService inventoryService =
                mockOf(InventoryService.class);

        final AuditService auditService =
                mockOf(AuditService.class);

        final ChatSessionService sessions =
                mock(ChatSessionService.class);

        final ResponseTemplates templates =
                new ResponseTemplates();

        final IntentPolicy policy =
                new IntentPolicy();

        final ChatPipeline pipeline =
                buildPipeline();

        final IntentClassifier classifier;

        final DialogueManager manager;

        AppPrincipal principal;

        /** true → mọi khung giờ đã kín chỗ (fixture {@code *_slot_full}). */
        boolean slotFull;

        /**
         * Trạng thái hội thoại sống — {@code readState} luôn trả về đúng đối
         * tượng này nên các lượt sau thấy slot/awaiting của lượt trước
         * (mock mặc định trả object mới ⇒ mất trạng thái).
         */
        final ConversationState state =
                new ConversationState();

        Harness() {

            templates.load();
            policy.load();

            classifier =
                    new IntentClassifier(
                            pipeline,
                            new DefaultResourceLoader(),
                            "classpath:chatbot/model/intent-model.bin"
                    );

            classifier.load();

            BranchSupport branchSupport =
                    new BranchSupport(
                            membershipService
                    );

            List<IntentHandler> handlers =
                    List.of(
                            new SmallTalkHandler(
                                    templates,
                                    policy
                            ),
                            new BranchInfoHandler(
                                    branchService,
                                    facilityService,
                                    templates,
                                    branchSupport
                            ),
                            new PlanHandler(
                                    planService,
                                    templates,
                                    branchSupport
                            ),
                            new ProductHandler(
                                    productService,
                                    templates
                            ),
                            new MemberInfoHandler(
                                    membershipService,
                                    bookingService,
                                    checkInService,
                                    orderService,
                                    templates
                            ),
                            new BookingFlowHandler(
                                    bookingService,
                                    facilityService,
                                    branchService,
                                    membershipService,
                                    templates,
                                    branchSupport,
                                    new ObjectMapper()
                            ),
                            new OpsReportHandler(
                                    reportService,
                                    bookingService,
                                    checkInService,
                                    inventoryService,
                                    templates
                            ),
                            new FaqHandler(
                                    templates,
                                    retriever()
                            ),
                            new AuditHandler(
                                    auditService,
                                    templates
                            )
                    );

            manager =
                    new DialogueManager(
                            pipeline,
                            classifier,
                            policy,
                            handlers,
                            sessions,
                            new RateLimiter(),
                            templates,
                            retriever(),
                            new BookingActionExecutor(
                                    bookingService,
                                    templates,
                                    new ObjectMapper()
                            ),
                            new ObjectMapper()
                    );

            manager.indexHandlers();
        }

        /**
         * Mốc đếm đầu lượt — {@code calls()} chỉ trả về lượt gọi <b>sau</b>
         * mốc này nên mỗi lượt kiểm được {@code services_never} riêng.
         */
        private final java.util.Map<Object, Integer> baseline =
                new java.util.IdentityHashMap<>();

        /**
         * Gọi trước mỗi lượt hội thoại để {@link #calls()} chỉ tính lượt gọi
         * phát sinh trong chính lượt đó.
         */
        void beginTurn() {

            baseline.clear();

            for (Recorded item : recorded) {

                baseline.put(
                        item.mock(),
                        org.mockito.Mockito.mockingDetails(item.mock())
                                .getInvocations()
                                .size()
                );
            }
        }

        /**
         * Danh sách {@code Interface.method} mà mock <b>thực sự</b> đã gọi
         * <b>trong lượt hiện tại</b>, dùng cho {@code services_called} /
         * {@code services_never}.
         *
         * <p>Dùng {@code mockingDetails(mock).getInvocations()} thay vì
         * answer tùy biến: Mockito <b>không</b> gọi answer của mock khi lượt
         * gọi khớp stub đã {@code when(...)} → ghi nhật ký bằng answer sẽ
         * mất đúng những lượt quan trọng nhất (create/cancel/revenue…).
         * Lượt stub thì Mockito tự loại khỏi danh sách invocations nên
         * không bị lẫn.
         */
        List<String> calls() {

            List<String> calls =
                    new ArrayList<>();

            for (Recorded item : recorded) {

                int from =
                        baseline.getOrDefault(item.mock(), 0);

                int index = 0;

                for (org.mockito.invocation.Invocation invocation :
                        org.mockito.Mockito.mockingDetails(item.mock())
                                .getInvocations()) {

                    if (index++ >= from) {

                        calls.add(
                                item.label()
                                        + "."
                                        + invocation.getMethod()
                                        .getName()
                        );
                    }
                }
            }

            return calls;
        }

        @SuppressWarnings("unchecked")
        private <T> T mockOf(
                Class<T> type
        ) {

            T mock =
                    mock(type);

            recorded.add(
                    new Recorded(
                            mock,
                            type.getSimpleName()
                    )
            );

            return mock;
        }

        /** Một mock + nhãn của nó. */
        private record Recorded(
                Object mock,
                String label
        ) {
        }
    }

    // ------------------------------------------------------------------
    // Danh sách fixture
    // ------------------------------------------------------------------

    /**
     * Mọi fixture bắt đầu bằng cách {@code resetMocks} (xóa stub cũ) rồi
     * dựng lại stub; nhờ vậy chạy tuần tự nhiều kịch bản không dính dữ liệu
     * của kịch bản trước.
     */
    static AppPrincipal apply(
            Harness harness,
            String fixture
    ) {

        reset(harness);
        base(harness);

        return switch (fixture) {

            case "member1_gym_q1_active" ->
                    memberGymQ1(harness, false, false);

            case "member1_gym_q1_slot_full" ->
                    memberGymQ1(harness, true, false);

            case "member1_gym_q1_conflict" ->
                    memberGymQ1(harness, false, true);

            case "member1_gym_q1_three_bookings" ->
                    memberGymQ1Three(harness);

            case "member_no_membership" ->
                    memberNoMembership(harness);

            case "member_expired" ->
                    memberExpired(harness);

            case "member_boxing_only_td" ->
                    memberBoxingOnlyTd(harness);

            case "member_order_paid_no_membership" ->
                    memberOrderPaidNoMembership(harness);

            case "member_with_rejected_checkin_service_not_included" ->
                    memberRejectedCheckIn(
                            harness,
                            "SERVICE_NOT_INCLUDED",
                            Q1
                    );

            case "member_with_rejected_checkin_membership_branch_mismatch" ->
                    memberRejectedCheckIn(
                            harness,
                            "MEMBERSHIP_BRANCH_MISMATCH",
                            Q7
                    );

            case "member_with_rejected_checkin_duplicate_checkin" ->
                    memberRejectedCheckIn(
                            harness,
                            "DUPLICATE_CHECKIN",
                            Q1
                    );

            case "manager_q1" ->
                    staff(harness, Q1);

            case "manager_q7" ->
                    staff(harness, Q7);

            case "admin" ->
                    admin(harness);

            case "member_other" ->
                    memberOther(harness);

            default -> throw new IllegalArgumentException(
                    "Fixture chưa khai báo: " + fixture
                            + " — thêm vào ScenarioFixtures"
            );
        };
    }

    /** Fixture đã khai báo (dùng để fail sớm khi JSON ghi tên lạ). */
    static Set<String> knownFixtures() {
        return Set.of(
                "member1_gym_q1_active",
                "member1_gym_q1_slot_full",
                "member1_gym_q1_conflict",
                "member1_gym_q1_three_bookings",
                "member_no_membership",
                "member_expired",
                "member_boxing_only_td",
                "member_order_paid_no_membership",
                "member_with_rejected_checkin_service_not_included",
                "member_with_rejected_checkin_membership_branch_mismatch",
                "member_with_rejected_checkin_duplicate_checkin",
                "manager_q1",
                "manager_q7",
                "admin",
                "member_other"
        );
    }

    // ------------------------------------------------------------------
    // Stub chung
    // ------------------------------------------------------------------

    private static void reset(
            Harness h
    ) {

        org.mockito.Mockito.reset(
                h.branchService,
                h.facilityService,
                h.planService,
                h.productService,
                h.membershipService,
                h.bookingService,
                h.checkInService,
                h.orderService,
                h.reportService,
                h.inventoryService,
                h.auditService,
                h.sessions
        );

        h.slotFull = false;
    }

    private static void base(
            Harness h
    ) {

        when(h.branchService.list(any()))
                .thenReturn(
                        List.of(
                                branch(Q1, "Q1", "Quận 1"),
                                branch(Q7, "Q7", "Quận 7"),
                                branch(TD, "TD", "Thủ Đức"),
                                branch(BT, "BT", "Bình Thạnh")
                        )
                );

        when(h.branchService.get(anyLong()))
                .thenAnswer(invocation ->
                        branch(
                                invocation.getArgument(0),
                                "Q" + invocation.getArgument(0),
                                "Chi nhánh " + invocation.getArgument(0)
                        )
                );

        when(h.branchService.getServices(any()))
                .thenReturn(
                        List.of(
                                branchService(Q1, ServiceCode.GYM),
                                branchService(Q1, ServiceCode.BOXING),
                                branchService(Q7, ServiceCode.GYM),
                                branchService(Q7, ServiceCode.BOXING),
                                branchService(TD, ServiceCode.GYM),
                                branchService(BT, ServiceCode.GYM)
                        )
                );

        when(h.branchService.getOperatingHours(any()))
                .thenAnswer(invocation ->
                        operatingHours(
                                invocation.getArgument(0)
                        )
                );

        when(h.facilityService.list(
                any(),
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {

            Long branchId =
                    invocation.getArgument(1);

            ServiceCode serviceCode =
                    invocation.getArgument(2);

            return facilityRows(
                    branchId == null ? Q1 : branchId,
                    serviceCode
            );
        });

        when(h.planService.list(
                any(),
                any(),
                any()
        )).thenReturn(
                plans()
        );

        when(h.productService.list(any()))
                .thenReturn(
                        products()
                );

        when(h.bookingService.availability(
                any(),
                any(),
                any()
        )).thenAnswer(invocation ->
                slots(
                        invocation.getArgument(2),
                        h.slotFull
                )
        );

        // create/cancel được stub kiểu "hợp đồng" — bắt chước đúng các phép
        // kiểm của service thật (MembershipService.requireEligible +
        // BookingService) để kịch bản BK-03/BK-04/BK-09/BK-10/BK-11/BK-20
        // thực sự bị chặn, thay vì mock "luôn thành công".
        when(h.bookingService.create(any(), any()))
                .thenAnswer(invocation -> {

                    AppPrincipal principal =
                            invocation.getArgument(0);

                    BookingCreateRequest request =
                            invocation.getArgument(1);

                    requireEligible(
                            h,
                            principal,
                            request
                    );

                    if (!request.startsAt()
                            .isAfter(TimeUtil.now())) {

                        throw new ConflictException(
                                "booking_not_future",
                                "Thời gian đặt lịch phải ở tương lai."
                        );
                    }

                    for (BookingResponse existing :
                            h.bookingService.list(principal)) {

                        if (existing.status()
                                        == BookingStatus.CONFIRMED
                                && existing.startsAtUtc()
                                .equals(
                                        request.startsAt()
                                )) {

                            throw new ConflictException(
                                    "booking_overlap",
                                    "Bạn đã có lịch tập trùng thời gian."
                            );
                        }
                    }

                    if (h.slotFull) {

                        throw new ConflictException(
                                "facility_full",
                                "Khung giờ đã đủ số lượng người."
                        );
                    }

                    return booking(
                            77L,
                            principal.getMemberId(),
                            request.branchId(),
                            request.serviceCode(),
                            BookingStatus.CONFIRMED,
                            request.startsAt()
                    );
                });

        when(h.bookingService.cancel(any(), any(), any()))
                .thenAnswer(invocation -> {

                    AppPrincipal principal =
                            invocation.getArgument(0);

                    Long bookingId =
                            invocation.getArgument(1);

                    BookingResponse existing =
                            h.bookingService.list(principal)
                                    .stream()
                                    .filter(booking ->
                                            bookingId.equals(
                                                    booking.id()
                                            )
                                    )
                                    .findFirst()
                                    .orElse(null);

                    if (existing == null) {

                        throw new ConflictException(
                                "booking_not_found",
                                "Không tìm thấy lịch đặt."
                        );
                    }

                    if (existing.status()
                            == BookingStatus.CANCELLED) {

                        throw new ConflictException(
                                "booking_already_cancelled",
                                "Lịch đặt đã được hủy"
                        );
                    }

                    if (!existing.startsAtUtc()
                            .isAfter(
                                    TimeUtil.now()
                                            .plusSeconds(7200)
                            )) {

                        throw new ConflictException(
                                "booking_cancellation_too_late",
                                "Chỉ được hủy lịch trước ít nhất 2 giờ"
                        );
                    }

                    return booking(
                            bookingId,
                            existing.memberId(),
                            existing.branchId(),
                            existing.serviceCode(),
                            BookingStatus.CANCELLED,
                            existing.startsAtUtc()
                    );
                });

        when(h.bookingService.list(any()))
                .thenReturn(
                        List.of()
                );

        when(h.reportService.dashboard(any()))
                .thenReturn(
                        new DashboardResponse(
                                BigDecimal.valueOf(12_500_000L),
                                18,
                                7,
                                9,
                                42
                        )
                );

        when(h.reportService.revenue(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(
                new RevenueReportResponse(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30),
                        Q1,
                        BigDecimal.valueOf(45_000_000L),
                        BigDecimal.valueOf(35_000_000L),
                        BigDecimal.valueOf(10_000_000L),
                        12
                )
        );

        when(h.reportService.services(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(
                List.of(
                        new ServiceReportResponse(
                                ServiceCode.GYM,
                                30,
                                25
                        ),
                        new ServiceReportResponse(
                                ServiceCode.BOXING,
                                8,
                                5
                        )
                )
        );

        when(h.inventoryService.list(any(), any()))
                .thenReturn(
                        List.of(
                                new InventoryResponse(
                                        1L,
                                        Q1,
                                        1L,
                                        "PROT01",
                                        "Whey protein",
                                        3,
                                        Instant.now()
                                )
                        )
                );

        when(h.auditService.list(any()))
                .thenReturn(
                        List.of(
                                new AuditEventResponse(
                                        1L,
                                        1L,
                                        "LOGIN",
                                        "USER",
                                        1L,
                                        Q1,
                                        "{}",
                                        Instant.now()
                                )
                        )
                );

        sessionStubs(h);
    }

    // ------------------------------------------------------------------
    // Fixture hội viên
    // ------------------------------------------------------------------

    private static AppPrincipal memberGymQ1(
            Harness h,
            boolean slotFull,
            boolean conflict
    ) {

        when(h.membershipService.current(any(), any()))
                .thenReturn(
                        membership(
                                1L,
                                1L,
                                1L,
                                Q1,
                                MembershipStatus.ACTIVE,
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 11, 30),
                                Set.of(ServiceCode.GYM)
                        )
                );

        h.slotFull =
                slotFull;

        if (conflict) {

            when(h.bookingService.list(any()))
                    .thenReturn(
                            List.of(
                                    booking(
                                            11L,
                                            1L,
                                            Q1,
                                            ServiceCode.GYM,
                                            BookingStatus.CONFIRMED,
                                            at(2026, 10, 6, 19, 0)
                                    )
                            )
                    );
        } else {

            when(h.bookingService.list(any()))
                    .thenReturn(
                            List.of(
                                    booking(
                                            10L,
                                            1L,
                                            Q1,
                                            ServiceCode.GYM,
                                            BookingStatus.CONFIRMED,
                                            at(2026, 10, 5, 10, 0)
                                    )
                            )
                    );
        }

        return principal(
                RoleCode.MEMBER,
                Q1,
                1L,
                101L,
                "Nguyễn Văn A"
        );
    }

    /** 3 lịch tương lai — dùng cho BK-19 "hủy lịch" (liệt kê để chọn). */
    private static AppPrincipal memberGymQ1Three(
            Harness h
    ) {

        when(h.membershipService.current(any(), any()))
                .thenReturn(
                        membership(
                                1L,
                                1L,
                                1L,
                                Q1,
                                MembershipStatus.ACTIVE,
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 11, 30),
                                Set.of(ServiceCode.GYM)
                        )
                );

        when(h.bookingService.list(any()))
                .thenReturn(
                        List.of(
                                booking(
                                        12L,
                                        1L,
                                        Q1,
                                        ServiceCode.GYM,
                                        BookingStatus.CONFIRMED,
                                        at(2026, 10, 6, 7, 0)
                                ),
                                booking(
                                        13L,
                                        1L,
                                        Q1,
                                        ServiceCode.GYM,
                                        BookingStatus.CONFIRMED,
                                        at(2026, 10, 6, 12, 0)
                                ),
                                booking(
                                        14L,
                                        1L,
                                        Q1,
                                        ServiceCode.GYM,
                                        BookingStatus.CONFIRMED,
                                        at(2026, 10, 6, 19, 0)
                                )
                        )
                );

        return principal(
                RoleCode.MEMBER,
                Q1,
                1L,
                101L,
                "Nguyễn Văn A"
        );
    }

    private static AppPrincipal memberNoMembership(
            Harness h
    ) {

        when(h.membershipService.current(any(), any()))
                .thenReturn(
                        null
                );

        when(h.bookingService.list(any()))
                .thenReturn(
                        List.of()
                );

        return principal(
                RoleCode.MEMBER,
                Q1,
                1L,
                101L,
                "Nguyễn Văn B"
        );
    }

    private static AppPrincipal memberExpired(
            Harness h
    ) {

        when(h.membershipService.current(any(), any()))
                .thenReturn(
                        membership(
                                2L,
                                1L,
                                1L,
                                Q1,
                                MembershipStatus.EXPIRED,
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 9, 30),
                                Set.of(ServiceCode.GYM)
                        )
                );

        when(h.bookingService.list(any()))
                .thenReturn(
                        List.of()
                );

        return principal(
                RoleCode.MEMBER,
                Q1,
                1L,
                101L,
                "Nguyễn Văn C"
        );
    }

    private static AppPrincipal memberBoxingOnlyTd(
            Harness h
    ) {

        when(h.membershipService.current(any(), any()))
                .thenReturn(
                        membership(
                                3L,
                                1L,
                                3L,
                                TD,
                                MembershipStatus.ACTIVE,
                                LocalDate.of(2026, 9, 10),
                                LocalDate.of(2026, 12, 9),
                                Set.of(ServiceCode.BOXING)
                        )
                );

        when(h.bookingService.list(any()))
                .thenReturn(
                        List.of()
                );

        return principal(
                RoleCode.MEMBER,
                TD,
                1L,
                101L,
                "Trần Thị D"
        );
    }

    private static AppPrincipal memberOrderPaidNoMembership(
            Harness h
    ) {

        when(h.membershipService.current(any(), any()))
                .thenReturn(
                        null
                );

        when(h.orderService.list(any()))
                .thenReturn(
                        List.of(
                                order(
                                        5L,
                                        "ORD_1005",
                                        Q1,
                                        1L,
                                        OrderStatus.PAID,
                                        450_000L
                                ),
                                order(
                                        6L,
                                        "ORD_1006",
                                        Q1,
                                        1L,
                                        OrderStatus.PAID,
                                        300_000L
                                )
                        )
                );

        return principal(
                RoleCode.MEMBER,
                Q1,
                1L,
                101L,
                "Lê Văn E"
        );
    }

    private static AppPrincipal memberRejectedCheckIn(
            Harness h,
            String reason,
            long branchId
    ) {

        when(h.membershipService.current(any(), any()))
                .thenReturn(
                        membership(
                                4L,
                                1L,
                                1L,
                                branchId,
                                MembershipStatus.ACTIVE,
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 11, 30),
                                Set.of(ServiceCode.GYM)
                        )
                );

        when(h.checkInService.list(any()))
                .thenReturn(
                        List.of(
                                new CheckInResponse(
                                        21L,
                                        1L,
                                        4L,
                                        branchId,
                                        ServiceCode.GYM,
                                        CheckInMethod.QR,
                                        CheckInResult.REJECTED,
                                        reason,
                                        1L,
                                        at(2026, 10, 4, 7, 30)
                                )
                        )
                );

        when(h.bookingService.list(any()))
                .thenReturn(
                        List.of()
                );

        return principal(
                RoleCode.MEMBER,
                branchId,
                1L,
                101L,
                "Phạm Văn F"
        );
    }

    private static AppPrincipal staff(
            Harness h,
            long branchId
    ) {

        when(h.membershipService.current(any(), any()))
                .thenReturn(
                        null
                );

        when(h.bookingService.list(any()))
                .thenReturn(
                        List.of(
                                booking(
                                        30L,
                                        7L,
                                        branchId,
                                        ServiceCode.GYM,
                                        BookingStatus.CONFIRMED,
                                        at(2026, 10, 5, 17, 0)
                                ),
                                booking(
                                        31L,
                                        8L,
                                        branchId,
                                        ServiceCode.BOXING,
                                        BookingStatus.CONFIRMED,
                                        at(2026, 10, 5, 19, 0)
                                )
                        )
                );

        when(h.checkInService.list(any()))
                .thenReturn(
                        List.of(
                                new CheckInResponse(
                                        40L,
                                        9L,
                                        30L,
                                        branchId,
                                        ServiceCode.GYM,
                                        CheckInMethod.MANUAL,
                                        CheckInResult.REJECTED,
                                        "MEMBERSHIP_EXPIRED",
                                        1L,
                                        at(2026, 10, 5, 7, 15)
                                )
                        )
                );

        return principal(
                RoleCode.BRANCH_MANAGER,
                branchId,
                null,
                201L,
                "Quản lý " + branchId
        );
    }

    private static AppPrincipal admin(
            Harness h
    ) {

        when(h.membershipService.current(any(), any()))
                .thenReturn(
                        null
                );

        when(h.bookingService.list(any()))
                .thenReturn(
                        List.of()
                );

        return principal(
                RoleCode.ADMIN,
                null,
                null,
                301L,
                "Quản trị viên"
        );
    }

    private static AppPrincipal memberOther(
            Harness h
    ) {

        when(h.membershipService.current(any(), any()))
                .thenReturn(
                        membership(
                                9L,
                                2L,
                                9L,
                                Q7,
                                MembershipStatus.ACTIVE,
                                LocalDate.of(2026, 10, 1),
                                LocalDate.of(2027, 1, 1),
                                Set.of(
                                        ServiceCode.GYM,
                                        ServiceCode.BOXING
                                )
                        )
                );

        when(h.bookingService.list(any()))
                .thenReturn(
                        List.of(
                                booking(
                                        99L,
                                        2L,
                                        Q7,
                                        ServiceCode.BOXING,
                                        BookingStatus.CONFIRMED,
                                        at(2026, 10, 7, 20, 0)
                                )
                        )
                );

        return principal(
                RoleCode.MEMBER,
                Q7,
                2L,
                102L,
                "Đỗ Thị G"
        );
    }

    // ------------------------------------------------------------------
    // Kho FAQ (chia sẻ 1 instance cho mọi fixture - dựng chỉ mục 1 lần)
    // ------------------------------------------------------------------

    private static volatile com.gymfit.chat.knowledge.FaqRetriever SHARED_RETRIEVER;

    static com.gymfit.chat.knowledge.FaqRetriever retriever() {

        if (SHARED_RETRIEVER == null) {

            synchronized (ScenarioFixtures.class) {

                if (SHARED_RETRIEVER == null) {

                    com.gymfit.chat.knowledge.FaqKnowledgeBase kb =
                            new com.gymfit.chat.knowledge.FaqKnowledgeBase();

                    kb.load();

                    com.gymfit.chat.knowledge.FaqRetriever built =
                            new com.gymfit.chat.knowledge.FaqRetriever(kb);

                    built.load();

                    SHARED_RETRIEVER = built;
                }
            }
        }

        return SHARED_RETRIEVER;
    }

    // ------------------------------------------------------------------
    // Session stub
    // ------------------------------------------------------------------

    private static void sessionStubs(
            Harness h
    ) {

        ChatSession session =
                ChatSession.builder()
                        .id("s1")
                        .userId(1L)
                        .createdAtUtc(Instant.now())
                        .updatedAtUtc(Instant.now())
                        .build();

        when(h.sessions.loadOrCreate(any(), any()))
                .thenReturn(
                        session
                );

        when(h.sessions.readState(any()))
                .thenAnswer(invocation ->
                        h.state
                );

        when(h.sessions.saveState(any(), any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(1)
                );

        when(h.sessions.appendUser(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(
                message(1L, ChatMessage.ROLE_USER)
        );

        when(h.sessions.appendBot(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(
                message(99L, ChatMessage.ROLE_BOT)
        );
    }

    // ------------------------------------------------------------------
    // "Hợp đồng" của service thật
    // ------------------------------------------------------------------

    /**
     * Bắt chước {@code MembershipService.requireEligible(memberId, branchId,
     * serviceCode)} — 4 phép kiểm y hệt service thật:
     *
     * <ol>
     *     <li>không có gói {@code ACTIVE} → {@code no_active_membership}</li>
     *     <li>hôm nay ngoài [startDate, endDate] → {@code membership_not_effective}</li>
     *     <li>khác chi nhánh của gói → {@code membership_branch_mismatch}</li>
     *     <li>dịch vụ không có trong gói → {@code membership_service_not_allowed}</li>
     * </ol>
     *
     * <p>Thực tại <b>BookingFlowHandler không chặn 4 lỗi này ở bước thẻ xác
     * nhận</b> (xem kịch bản BK-03/BK-04) nên nếu mock "luôn thành công"
     * thì kịch bản sẽ ghi nhận sai rằng bot đã đặt được lịch. Nguồn dữ
     * liệu lấy từ stub {@code MembershipService.current} của fixture.
     */
    private static void requireEligible(
            Harness h,
            AppPrincipal principal,
            BookingCreateRequest request
    ) {

        MembershipResponse membership =
                h.membershipService.current(
                        principal,
                        principal.getMemberId()
                );

        if (membership == null) {

            throw new ConflictException(
                    "no_active_membership",
                    "Bạn chưa có gói tập đang hoạt động nên chưa đặt lịch được."
            );
        }

        if (membership.status() != MembershipStatus.ACTIVE) {

            throw new ConflictException(
                    "membership_not_effective",
                    membership.status() == MembershipStatus.EXPIRED
                            ? "Gói tập của bạn đã hết hạn."
                            : "Gói tập hiện không có hiệu lực."
            );
        }

        LocalDate today =
                TimeUtil.today();

        if (today.isBefore(
                membership.startDate()
        ) || today.isAfter(
                membership.endDate()
        )) {

            throw new ConflictException(
                    "membership_not_effective",
                    "Gói tập không còn hiệu lực."
            );
        }

        if (!membership.branchId()
                .equals(
                        request.branchId()
                )) {

            throw new ConflictException(
                    "membership_branch_mismatch",
                    "Gói tập không áp dụng tại chi nhánh này."
            );
        }

        if (!membership.services()
                .contains(
                        request.serviceCode()
                )) {

            throw new ConflictException(
                    "membership_service_not_allowed",
                    "Gói tập không bao gồm dịch vụ này."
            );
        }
    }

    // ------------------------------------------------------------------
    // Builder dữ liệu
    // ------------------------------------------------------------------

    private static AppPrincipal principal(
            RoleCode role,
            Long branchId,
            Long memberId,
            long userId,
            String fullName
    ) {

        AppUser user =
                AppUser.builder()
                        .id(userId)
                        .fullName(fullName)
                        .email("u" + userId + "@gymfit.local")
                        .passwordHash("x")
                        .roleCode(role)
                        .status(UserStatus.ACTIVE)
                        .branchId(branchId)
                        .memberId(memberId)
                        .build();

        return new AppPrincipal(user);
    }

    private static ChatMessage message(
            Long id,
            String role
    ) {
        return ChatMessage.builder()
                .id(id)
                .sessionId("s1")
                .role(role)
                .text("x")
                .createdAtUtc(Instant.now())
                .build();
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

    private static BranchServiceResponse branchService(
            Long branchId,
            ServiceCode serviceCode
    ) {
        return new BranchServiceResponse(
                branchId,
                serviceCode,
                60,
                serviceCode == ServiceCode.GYM
                        ? 30
                        : 12,
                true
        );
    }

    private static List<OperatingHourResponse> operatingHours(
            Long branchId
    ) {

        List<OperatingHourResponse> hours =
                new ArrayList<>();

        // Thứ 2 → Thứ 6 (1..6); Thứ 7 & Chủ nhật KHÔNG có hàng →
        // bot báo "không mở cửa" thay vì lỗi (kịch bản BK-25).
        for (int day = 1; day <= 6; day++) {
            hours.add(
                    new OperatingHourResponse(
                            (long) day,
                            branchId == null ? Q1 : branchId,
                            day,
                            LocalTime.of(5, 30),
                            LocalTime.of(22, 0)
                    )
            );
        }

        return hours;
    }

    private static List<FacilityResponse> facilityRows(
            Long branchId,
            ServiceCode serviceCode
    ) {

        if (serviceCode == null) {
            return List.of(
                    facility(branchId, ServiceCode.GYM, "Sân gym"),
                    facility(branchId, ServiceCode.BOXING, "Phòng boxing")
            );
        }

        if (serviceCode == ServiceCode.GYM) {
            return List.of(
                    facility(branchId, ServiceCode.GYM, "Sân gym")
            );
        }

        return List.of(
                facility(branchId, ServiceCode.BOXING, "Phòng boxing")
        );
    }

    private static FacilityResponse facility(
            Long branchId,
            ServiceCode serviceCode,
            String name
    ) {
        return new FacilityResponse(
                branchId * 100
                        + (serviceCode == ServiceCode.GYM ? 1 : 2),
                branchId,
                serviceCode,
                serviceCode == ServiceCode.GYM
                        ? FacilityType.GYM_AREA
                        : FacilityType.BOXING_ROOM,
                name,
                serviceCode == ServiceCode.GYM ? 30 : 12,
                FacilityStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );
    }

    private static List<PlanResponse> plans() {
        return List.of(
                plan(1L, "GYM cơ bản 30 ngày", PlanTier.BASIC, 30,
                        300_000L, ServiceCode.GYM),
                plan(2L, "GYM nâng cao 90 ngày", PlanTier.STANDARD, 90,
                        800_000L, ServiceCode.GYM),
                plan(3L, "Boxing 30 ngày", PlanTier.BASIC, 30,
                        500_000L, ServiceCode.BOXING),
                plan(4L, "Combo GYM + Boxing 90 ngày", PlanTier.PREMIUM, 90,
                        1_200_000L, ServiceCode.GYM, ServiceCode.BOXING)
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

    private static List<ProductResponse> products() {
        return List.of(
                new ProductResponse(
                        1L,
                        "PROT01",
                        "Whey protein 1kg",
                        "Dinh dưỡng",
                        BigDecimal.valueOf(850_000L),
                        ProductStatus.ACTIVE,
                        Instant.now(),
                        Instant.now()
                ),
                new ProductResponse(
                        2L,
                        "SHAK01",
                        "Bộ shaker",
                        "Phụ kiện",
                        BigDecimal.valueOf(120_000L),
                        ProductStatus.ACTIVE,
                        Instant.now(),
                        Instant.now()
                )
        );
    }

    /**
     * Khung giờ có sẵn của {@code date}; {@code full} = mọi khung đã kín.
     *
     * <p>Chủ nhật trả về rỗng — khớp {@link #operatingHours} (chỉ khai
     * Thứ 2 → Thứ 6) nên kịch bản BK-25 thấy "không còn khung giờ trống"
     * thay vì lỗi.
     */
    private static List<AvailabilitySlotResponse> slots(
            LocalDate date,
            boolean full
    ) {

        if (date.getDayOfWeek()
                == java.time.DayOfWeek.SUNDAY) {

            return List.of();
        }

        int capacity =
                full ? 0 : 5;

        List<AvailabilitySlotResponse> result =
                new ArrayList<>();

        int[] hours =
                {6, 7, 8, 17, 18, 19, 20};

        for (int hour : hours) {

            Instant start =
                    date.atTime(hour, 0)
                            .atZone(TimeUtil.VIETNAM)
                            .toInstant();

            result.add(
                    new AvailabilitySlotResponse(
                            start,
                            start.plusSeconds(3600),
                            capacity
                    )
            );
        }

        return result;
    }

    private static MembershipResponse membership(
            Long id,
            Long memberId,
            Long planId,
            long branchId,
            MembershipStatus status,
            LocalDate start,
            LocalDate end,
            Set<ServiceCode> services
    ) {
        return new MembershipResponse(
                id,
                memberId,
                planId,
                id,
                branchId,
                status,
                start,
                end,
                start.atStartOfDay(ZoneOffset.ofHours(7))
                        .toInstant(),
                end.atStartOfDay(ZoneOffset.ofHours(7))
                        .toInstant(),
                null,
                services,
                start.atStartOfDay(ZoneOffset.ofHours(7))
                        .toInstant()
        );
    }

    private static BookingResponse booking(
            Long id,
            Long memberId,
            long branchId,
            ServiceCode serviceCode,
            BookingStatus status,
            Instant startsAt
    ) {
        return new BookingResponse(
                id,
                "BK_" + id,
                memberId,
                1L,
                branchId,
                serviceCode,
                branchId * 100 + 1,
                status,
                startsAt,
                startsAt.plusSeconds(3600),
                null,
                null,
                1L,
                startsAt.minusSeconds(86_400)
        );
    }

    private static OrderResponse order(
            Long id,
            String code,
            long branchId,
            Long memberId,
            OrderStatus status,
            long total
    ) {
        return new OrderResponse(
                id,
                code,
                branchId,
                memberId,
                status,
                BigDecimal.valueOf(total),
                BigDecimal.valueOf(total),
                1L,
                at(2026, 10, 1, 10, 0),
                status == OrderStatus.PAID
                        ? at(2026, 10, 1, 10, 5)
                        : null,
                List.of()
        );
    }

    private static Instant at(
            int year,
            int month,
            int day,
            int hour,
            int minute
    ) {
        return LocalDateTime.of(year, month, day, hour, minute)
                .toInstant(ZoneOffset.ofHours(7));
    }

    // ------------------------------------------------------------------
    // Mockito
    // ------------------------------------------------------------------

    private static ChatPipeline buildPipeline() {

        TextNormalizer normalizer =
                new TextNormalizer();

        normalizer.load();

        EntityExtractor extractor =
                new EntityExtractor(
                        new GazetteerProvider() {
                            @Override
                            public Map<String, Long> branchAliases() {
                                // Khớp theo chuỗi KHÔNG DẤU (NormalizedText.plain)
                                // — đúng cách DbGazetteerProvider nạp từ DB.
                                return Map.ofEntries(
                                        Map.entry("q1", Q1),
                                        Map.entry("q7", Q7),
                                        Map.entry("td", TD),
                                        Map.entry("bt", BT),
                                        Map.entry("quan 1", Q1),
                                        Map.entry("quan 7", Q7),
                                        Map.entry("thu duc", TD),
                                        Map.entry("binh thanh", BT)
                                );
                            }

                            @Override
                            public Map<String, String> productAliases() {
                                return Map.of();
                            }
                        },
                        new DateTimeParser()
                );

        extractor.load();

        return new ChatPipeline(
                normalizer,
                extractor
        );
    }
}
