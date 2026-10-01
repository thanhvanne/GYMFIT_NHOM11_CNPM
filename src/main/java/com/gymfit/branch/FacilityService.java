package com.gymfit.branch;

import com.gymfit.audit.AuditService;
import com.gymfit.branch.dto.FacilityCreateRequest;
import com.gymfit.branch.dto.FacilityResponse;
import com.gymfit.branch.dto.FacilityUpdateRequest;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final BranchRepository branchRepository;
    private final BranchServiceConfigRepository serviceConfigRepository;
    private final BranchScopeGuard branchScopeGuard;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<FacilityResponse> list(
            AppPrincipal principal,
            Long branchId,
            ServiceCode serviceCode,
            FacilityStatus status
    ) {
        List<Facility> facilities;

        if (principal.getRole()
                == RoleCode.ADMIN) {

            facilities = branchId == null
                    ? facilityRepository.findAll()
                    : facilityRepository
                    .findAllByBranchIdOrderByNameAsc(
                            branchId
                    );

        } else if (
                principal.getRole()
                        == RoleCode.BRANCH_MANAGER
        ) {
            Long scopedBranch =
                    principal.getBranchId();

            if (branchId != null) {
                branchScopeGuard.requireBranch(
                        principal,
                        branchId
                );

                scopedBranch = branchId;
            }

            facilities =
                    facilityRepository
                            .findAllByBranchIdOrderByNameAsc(
                                    scopedBranch
                            );

        } else {
            if (branchId == null) {
                facilities =
                        facilityRepository
                                .findAll();
            } else {
                facilities =
                        facilityRepository
                                .findAllByBranchIdOrderByNameAsc(
                                        branchId
                                );
            }
        }

        return facilities.stream()
                .filter(facility ->
                        serviceCode == null
                                || facility.getServiceCode()
                                == serviceCode
                )
                .filter(facility ->
                        status == null
                                || facility.getStatus()
                                == status
                )
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FacilityResponse get(
            AppPrincipal principal,
            Long id
    ) {
        Facility facility =
                requireFacility(id);

        if (principal.getRole()
                == RoleCode.BRANCH_MANAGER) {

            branchScopeGuard.requireBranch(
                    principal,
                    facility.getBranchId()
            );
        }

        return toResponse(facility);
    }

    @Transactional
    public FacilityResponse create(
            AppPrincipal principal,
            FacilityCreateRequest request
    ) {
        if (principal.getRole()
                != RoleCode.ADMIN) {

            branchScopeGuard.requireBranch(
                    principal,
                    request.branchId()
            );
        }

        Branch branch =
                branchRepository
                        .findById(
                                request.branchId()
                        )
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "branch_not_found",
                                        "Không tìm thấy chi nhánh"
                                )
                        );

        if (branch.getStatus()
                != BranchStatus.ACTIVE) {

            throw new ConflictException(
                    "branch_inactive",
                    "Chi nhánh không hoạt động"
            );
        }

        validateService(
                request.branchId(),
                request.serviceCode()
        );

        validateFacilityType(
                request.serviceCode(),
                request.facilityType()
        );

        ensureNameAvailable(
                request.branchId(),
                request.name(),
                null
        );

        Facility facility =
                Facility.builder()
                        .branchId(
                                request.branchId()
                        )
                        .serviceCode(
                                request.serviceCode()
                        )
                        .facilityType(
                                request.facilityType()
                        )
                        .name(
                                request.name()
                                        .trim()
                        )
                        .capacity(
                                request.capacity()
                        )
                        .status(
                                request.status()
                        )
                        .createdAtUtc(
                                TimeUtil.now()
                        )
                        .updatedAtUtc(
                                TimeUtil.now()
                        )
                        .build();

        Facility saved =
                facilityRepository.save(
                        facility
                );

        auditService.record(
                principal.getUserId(),
                "FACILITY_CREATED",
                "FACILITY",
                saved.getId(),
                saved.getBranchId(),
                Map.of(
                        "name",
                        saved.getName(),
                        "serviceCode",
                        saved.getServiceCode()
                                .name()
                )
        );

        return toResponse(saved);
    }

    @Transactional
    public FacilityResponse update(
            AppPrincipal principal,
            Long id,
            FacilityUpdateRequest request
    ) {
        Facility facility =
                requireFacility(id);

        if (principal.getRole()
                != RoleCode.ADMIN) {

            branchScopeGuard.requireBranch(
                    principal,
                    facility.getBranchId()
            );
        }

        validateService(
                facility.getBranchId(),
                request.serviceCode()
        );

        validateFacilityType(
                request.serviceCode(),
                request.facilityType()
        );

        ensureNameAvailable(
                facility.getBranchId(),
                request.name(),
                id
        );

        facility.setServiceCode(
                request.serviceCode()
        );

        facility.setFacilityType(
                request.facilityType()
        );

        facility.setName(
                request.name().trim()
        );

        facility.setCapacity(
                request.capacity()
        );

        facility.setStatus(
                request.status()
        );

        facility.setUpdatedAtUtc(
                TimeUtil.now()
        );

        Facility saved =
                facilityRepository.save(
                        facility
                );

        auditService.record(
                principal.getUserId(),
                "FACILITY_UPDATED",
                "FACILITY",
                saved.getId(),
                saved.getBranchId(),
                Map.of(
                        "name",
                        saved.getName(),
                        "status",
                        saved.getStatus()
                                .name()
                )
        );

        return toResponse(saved);
    }

    public Facility requireFacility(
            Long id
    ) {
        return facilityRepository
                .findById(id)
                .orElseThrow(() ->
                        new NotFoundException(
                                "facility_not_found",
                                "Không tìm thấy cơ sở vật chất"
                        )
                );
    }

    private void validateService(
            Long branchId,
            ServiceCode serviceCode
    ) {
        if (!serviceConfigRepository
                .existsByIdBranchIdAndIdServiceCode(
                        branchId,
                        serviceCode
                )) {

            throw new ConflictException(
                    "service_not_supported",
                    "Chi nhánh không hỗ trợ dịch vụ này"
            );
        }
    }

    private void validateFacilityType(
            ServiceCode serviceCode,
            FacilityType facilityType
    ) {
        boolean valid =
                switch (serviceCode) {
                    case GYM ->
                            facilityType
                                    == FacilityType.GYM_AREA;

                    case BOXING ->
                            facilityType
                                    == FacilityType.BOXING_ROOM;

                    case PICKLEBALL ->
                            facilityType
                                    == FacilityType.PICKLEBALL_COURT;
                };

        if (!valid) {
            throw new ConflictException(
                    "facility_type_mismatch",
                    "Loại cơ sở vật chất không phù hợp dịch vụ"
            );
        }
    }

    private void ensureNameAvailable(
            Long branchId,
            String name,
            Long currentId
    ) {
        facilityRepository
                .findByBranchIdAndNameIgnoreCase(
                        branchId,
                        name.trim()
                )
                .ifPresent(existing -> {
                    if (currentId == null
                            || !existing.getId()
                            .equals(currentId)) {

                        throw new ConflictException(
                                "facility_name_exists",
                                "Tên cơ sở vật chất đã tồn tại trong chi nhánh"
                        );
                    }
                });
    }

    private FacilityResponse toResponse(
            Facility facility
    ) {
        return new FacilityResponse(
                facility.getId(),
                facility.getBranchId(),
                facility.getServiceCode(),
                facility.getFacilityType(),
                facility.getName(),
                facility.getCapacity(),
                facility.getStatus(),
                facility.getCreatedAtUtc(),
                facility.getUpdatedAtUtc()
        );
    }
}