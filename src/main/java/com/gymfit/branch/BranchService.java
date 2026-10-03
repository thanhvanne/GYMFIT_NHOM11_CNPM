package com.gymfit.branch;

import com.gymfit.audit.AuditService;
import com.gymfit.branch.dto.*;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final BranchServiceConfigRepository serviceConfigRepository;
    private final AuditService auditService;
    private final BranchOperatingHourJdbcRepository operatingHourJdbcRepository;

    @Transactional(readOnly = true)
    public List<BranchResponse> list(
            BranchStatus status
    ) {
        List<Branch> branches =
                status == null
                        ? branchRepository
                        .findAllByOrderByNameAsc()
                        : branchRepository
                        .findAllByStatusOrderByNameAsc(
                                status
                        );

        return branches.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BranchResponse get(Long id) {
        return toResponse(
                requireBranch(id)
        );
    }

    @Transactional
    public BranchResponse create(
            Long actorUserId,
            BranchCreateRequest request
    ) {
        String code =
                request.code()
                        .trim()
                        .toUpperCase();

        if (branchRepository
                .existsByCodeIgnoreCase(code)) {

            throw new ConflictException(
                    "branch_code_exists",
                    "Mã chi nhánh đã tồn tại"
            );
        }

        validateTimezone(
                request.timezone()
        );

        Branch branch =
                Branch.builder()
                        .code(code)
                        .name(
                                request.name()
                                        .trim()
                        )
                        .address(
                                request.address()
                                        .trim()
                        )
                        .phone(
                                normalizeNullable(
                                        request.phone()
                                )
                        )
                        .status(
                                BranchStatus.ACTIVE
                        )
                        .timezone(
                                request.timezone()
                                        .trim()
                        )
                        .createdAtUtc(
                                TimeUtil.now()
                        )
                        .updatedAtUtc(
                                TimeUtil.now()
                        )
                        .build();

        Branch saved =
                branchRepository.save(
                        branch
                );

        auditService.record(
                actorUserId,
                "BRANCH_CREATED",
                "BRANCH",
                saved.getId(),
                saved.getId(),
                Map.of(
                        "code",
                        saved.getCode(),
                        "name",
                        saved.getName()
                )
        );

        return toResponse(saved);
    }

    @Transactional
    public BranchResponse update(
            Long actorUserId,
            Long id,
            BranchUpdateRequest request
    ) {
        Branch branch =
                requireBranch(id);

        String code =
                request.code()
                        .trim()
                        .toUpperCase();

        branchRepository
                .findByCodeIgnoreCase(code)
                .ifPresent(existing -> {
                    if (!existing.getId()
                            .equals(id)) {

                        throw new ConflictException(
                                "branch_code_exists",
                                "Mã chi nhánh đã tồn tại"
                        );
                    }
                });

        validateTimezone(
                request.timezone()
        );

        branch.setCode(code);

        branch.setName(
                request.name().trim()
        );

        branch.setAddress(
                request.address().trim()
        );

        branch.setPhone(
                normalizeNullable(
                        request.phone()
                )
        );

        branch.setStatus(
                request.status()
        );

        branch.setTimezone(
                request.timezone().trim()
        );

        branch.setUpdatedAtUtc(
                TimeUtil.now()
        );

        Branch saved =
                branchRepository.save(
                        branch
                );

        auditService.record(
                actorUserId,
                "BRANCH_UPDATED",
                "BRANCH",
                saved.getId(),
                saved.getId(),
                Map.of(
                        "code",
                        saved.getCode(),
                        "status",
                        saved.getStatus()
                                .name()
                )
        );

        return toResponse(saved);
    }

    @Transactional
    public BranchResponse updateStatus(
            Long actorUserId,
            Long id,
            BranchStatusRequest request
    ) {
        Branch branch =
                requireBranch(id);

        BranchStatus oldStatus =
                branch.getStatus();

        branch.setStatus(
                request.status()
        );

        branch.setUpdatedAtUtc(
                TimeUtil.now()
        );

        Branch saved =
                branchRepository.save(
                        branch
                );

        auditService.record(
                actorUserId,
                "BRANCH_UPDATED",
                "BRANCH",
                saved.getId(),
                saved.getId(),
                Map.of(
                        "code",
                        saved.getCode(),
                        "oldStatus",
                        oldStatus.name(),
                        "newStatus",
                        saved.getStatus()
                                .name()
                )
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BranchServiceResponse> getServices(
            Long branchId
    ) {
        requireBranch(branchId);

        return serviceConfigRepository
                .findAllByIdBranchIdOrderByIdServiceCodeAsc(
                        branchId
                )
                .stream()
                .map(this::toServiceResponse)
                .toList();
    }

    @Transactional
    public List<BranchServiceResponse> updateServices(
            Long actorUserId,
            Long branchId,
            UpdateBranchServicesRequest request
    ) {
        requireBranch(branchId);

        Set<ServiceCode> uniqueServices =
                new HashSet<>();

        for (BranchServiceItemRequest item
                : request.services()) {

            if (!uniqueServices.add(
                    item.serviceCode()
            )) {
                throw new ConflictException(
                        "duplicate_branch_service",
                        "Dịch vụ bị trùng trong cấu hình"
                );
            }
        }

        serviceConfigRepository
                .deleteAllByIdBranchId(
                        branchId
                );

        serviceConfigRepository.flush();

        List<BranchServiceConfig> entities =
                request.services()
                        .stream()
                        .map(item ->
                                BranchServiceConfig
                                        .builder()
                                        .id(
                                                new BranchServiceConfigId(
                                                        branchId,
                                                        item.serviceCode()
                                                )
                                        )
                                        .bookingDurationMinutes(
                                                item.bookingDurationMinutes()
                                        )
                                        .capacity(
                                                item.capacity()
                                        )
                                        .bookingEnabled(
                                                item.bookingEnabled()
                                        )
                                        .build()
                        )
                        .toList();

        serviceConfigRepository
                .saveAll(entities);

        auditService.record(
                actorUserId,
                "BRANCH_SERVICES_UPDATED",
                "BRANCH",
                branchId,
                branchId,
                Map.of(
                        "serviceCount",
                        entities.size()
                )
        );

        return getServices(branchId);
    }

    @Transactional(readOnly = true)
    public List<OperatingHourResponse> getOperatingHours(
            Long branchId
    ) {
        requireBranch(branchId);

        return operatingHourJdbcRepository
                .findAllByBranchId(branchId)
                .stream()
                .map(this::toOperatingHourResponse)
                .toList();
    }

    @Transactional
    public List<OperatingHourResponse> updateOperatingHours(
            Long actorUserId,
            Long branchId,
            UpdateOperatingHoursRequest request
    ) {
        requireBranch(branchId);

        Set<Integer> days = new HashSet<>();

        List<BranchOperatingHourJdbcRepository.OperatingHourValue>
                values = request.hours()
                .stream()
                .map(item -> {
                    if (!days.add(item.dayOfWeek())) {
                        throw new ConflictException(
                                "duplicate_operating_day",
                                "Ngày hoạt động bị trùng"
                        );
                    }

                    LocalTime openTime =
                            normalizeDatabaseTime(
                                    item.openTime()
                            );

                    LocalTime closeTime =
                            normalizeDatabaseTime(
                                    item.closeTime()
                            );

                    if (!openTime.isBefore(closeTime)) {
                        throw new ConflictException(
                                "invalid_operating_hours",
                                "Giờ mở cửa phải trước giờ đóng cửa"
                        );
                    }

                    return new BranchOperatingHourJdbcRepository
                            .OperatingHourValue(
                            item.dayOfWeek(),
                            openTime,
                            closeTime
                    );
                })
                .toList();

        operatingHourJdbcRepository.replaceAll(
                branchId,
                values
        );

        auditService.record(
                actorUserId,
                "BRANCH_OPERATING_HOURS_UPDATED",
                "BRANCH",
                branchId,
                branchId,
                Map.of(
                        "dayCount",
                        values.size()
                )
        );

        return operatingHourJdbcRepository
                .findAllByBranchId(branchId)
                .stream()
                .map(this::toOperatingHourResponse)
                .toList();
    }

    public Branch requireBranch(
            Long id
    ) {
        return branchRepository
                .findById(id)
                .orElseThrow(() ->
                        new NotFoundException(
                                "branch_not_found",
                                "Không tìm thấy chi nhánh"
                        )
                );
    }

    private LocalTime normalizeDatabaseTime(
            LocalTime value
    ) {
        return value.truncatedTo(
                ChronoUnit.SECONDS
        );
    }

    private void validateTimezone(
            String timezone
    ) {
        try {
            ZoneId.of(
                    timezone.trim()
            );
        } catch (Exception exception) {
            throw new ConflictException(
                    "invalid_timezone",
                    "Múi giờ không hợp lệ"
            );
        }
    }

    private String normalizeNullable(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    private BranchResponse toResponse(
            Branch branch
    ) {
        return new BranchResponse(
                branch.getId(),
                branch.getCode(),
                branch.getName(),
                branch.getAddress(),
                branch.getPhone(),
                branch.getStatus(),
                branch.getTimezone(),
                branch.getCreatedAtUtc(),
                branch.getUpdatedAtUtc()
        );
    }

    private BranchServiceResponse toServiceResponse(
            BranchServiceConfig config
    ) {
        return new BranchServiceResponse(
                config.getId()
                        .getBranchId(),
                config.getId()
                        .getServiceCode(),
                config.getBookingDurationMinutes(),
                config.getCapacity(),
                config.getBookingEnabled()
        );
    }

    private OperatingHourResponse toOperatingHourResponse(
            BranchOperatingHour hour
    ) {
        return new OperatingHourResponse(
                hour.getId(),
                hour.getBranchId(),
                hour.getDayOfWeek(),
                hour.getOpenTime(),
                hour.getCloseTime()
        );
    }
}