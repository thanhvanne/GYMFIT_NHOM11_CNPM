package com.gymfit.user;

import com.gymfit.audit.AuditService;
import com.gymfit.branch.Branch;
import com.gymfit.branch.BranchRepository;
import com.gymfit.branch.BranchStatus;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.member.Member;
import com.gymfit.member.MemberRepository;
import com.gymfit.member.MemberStatus;
import com.gymfit.user.dto.UserCreateRequest;
import com.gymfit.user.dto.UserResponse;
import com.gymfit.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepository;
    private final BranchRepository branchRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<UserResponse> list(RoleCode role) {
        List<AppUser> users = role == null
                ? userRepository.findAllByOrderByCreatedAtUtcDesc()
                : userRepository
                .findAllByRoleCodeOrderByCreatedAtUtcDesc(role);

        return users.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return toResponse(requireUser(id));
    }

    @Transactional
    public UserResponse create(
            Long actorUserId,
            UserCreateRequest request
    ) {
        String email = normalizeEmail(request.email());

        ensureEmailAvailable(email, null);

        Scope scope = validateScope(
                request.role(),
                request.branchId(),
                request.memberId(),
                null
        );

        AppUser user = AppUser.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .passwordHash(
                        passwordEncoder.encode(
                                request.password()
                        )
                )
                .roleCode(request.role())
                .status(request.status())
                .branchId(scope.branchId())
                .memberId(scope.memberId())
                .createdAtUtc(TimeUtil.now())
                .updatedAtUtc(TimeUtil.now())
                .build();

        AppUser saved = userRepository.save(user);

        auditService.record(
                actorUserId,
                "USER_CREATED",
                "APP_USER",
                saved.getId(),
                saved.getBranchId(),
                Map.of(
                        "email", saved.getEmail(),
                        "role", saved.getRoleCode().name()
                )
        );

        return toResponse(saved);
    }

    @Transactional
    public UserResponse update(
            Long actorUserId,
            Long id,
            UserUpdateRequest request
    ) {
        AppUser user = requireUser(id);

        if (id.equals(actorUserId)
                && request.status() != UserStatus.ACTIVE) {
            throw new ConflictException(
                    "cannot_disable_self",
                    "Không thể khóa hoặc vô hiệu hóa tài khoản đang đăng nhập"
            );
        }

        if (id.equals(actorUserId)
                && request.role() != RoleCode.ADMIN) {
            throw new ConflictException(
                    "cannot_change_own_role",
                    "Không thể thay đổi role của tài khoản đang đăng nhập"
            );
        }

        String email = normalizeEmail(request.email());

        ensureEmailAvailable(email, id);

        Scope scope = validateScope(
                request.role(),
                request.branchId(),
                request.memberId(),
                id
        );

        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setRoleCode(request.role());
        user.setStatus(request.status());
        user.setBranchId(scope.branchId());
        user.setMemberId(scope.memberId());

        if (request.password() != null
                && !request.password().isBlank()) {
            user.setPasswordHash(
                    passwordEncoder.encode(
                            request.password()
                    )
            );
        }

        user.setUpdatedAtUtc(TimeUtil.now());

        AppUser saved = userRepository.save(user);

        auditService.record(
                actorUserId,
                "USER_UPDATED",
                "APP_USER",
                saved.getId(),
                saved.getBranchId(),
                Map.of(
                        "email", saved.getEmail(),
                        "role", saved.getRoleCode().name(),
                        "status", saved.getStatus().name()
                )
        );

        return toResponse(saved);
    }

    /**
     * Tạo tài khoản {@code MEMBER} cho một hội viên đã lưu (F3).
     *
     * <p>Nguyên tắc: kiểm trùng email + kiểm hội viên (ACTIVE, chưa có tài khoản)
     * <b>trước</b> khi băm mật khẩu/lưu; lỗi ném ra để transaction của
     * {@code MemberService.create} rollback toàn bộ.
     *
     * @param rawPassword mật khẩu thô – chỉ được băm, không ghi log/audit
     */
    @Transactional
    public AppUser createForMember(
            Long actorUserId,
            Member member,
            String username,
            String rawPassword
    ) {
        ensureEmailAvailable(username, null);

        Scope scope = validateScope(
                RoleCode.MEMBER,
                null,
                member.getId(),
                null
        );

        AppUser user = AppUser.builder()
                .fullName(member.getFullName())
                .email(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .roleCode(RoleCode.MEMBER)
                .status(UserStatus.ACTIVE)
                .branchId(null)
                .memberId(scope.memberId())
                .mustChangePassword(true)
                .createdAtUtc(TimeUtil.now())
                .updatedAtUtc(TimeUtil.now())
                .build();

        AppUser saved = userRepository.save(user);

        // Chỉ ghi định danh – KHÔNG đưa mật khẩu vào details_json.
        auditService.record(
                actorUserId,
                "USER_CREATED",
                "APP_USER",
                saved.getId(),
                member.getHomeBranchId(),
                Map.of(
                        "email", saved.getEmail(),
                        "role", "MEMBER"
                )
        );

        return saved;
    }

    /**
     * Đặt lại mật khẩu tài khoản đã có: băm mật khẩu mới, bật cờ bắt buộc đổi
     * mật khẩu lần đầu (D3) và ghi audit {@code PASSWORD_RESET} (không có mật khẩu).
     */
    @Transactional
    public void resetPassword(
            Long actorUserId,
            AppUser user,
            String rawPassword,
            Long branchId
    ) {
        user.setPasswordHash(
                passwordEncoder.encode(rawPassword)
        );
        user.setMustChangePassword(true);
        user.setUpdatedAtUtc(TimeUtil.now());

        AppUser saved = userRepository.save(user);

        auditService.record(
                actorUserId,
                "PASSWORD_RESET",
                "APP_USER",
                saved.getId(),
                branchId,
                Map.of(
                        "email", saved.getEmail()
                )
        );
    }

    private Scope validateScope(
            RoleCode role,
            Long branchId,
            Long memberId,
            Long currentUserId
    ) {
        return switch (role) {
            case ADMIN -> {
                if (branchId != null || memberId != null) {
                    throw new ConflictException(
                            "invalid_admin_scope",
                            "Tài khoản ADMIN không được gán chi nhánh hoặc hội viên"
                    );
                }

                yield new Scope(null, null);
            }

            case BRANCH_MANAGER -> {
                if (branchId == null || memberId != null) {
                    throw new ConflictException(
                            "invalid_manager_scope",
                            "BRANCH_MANAGER phải thuộc đúng một chi nhánh"
                    );
                }

                Branch branch = branchRepository
                        .findById(branchId)
                        .orElseThrow(() -> new NotFoundException(
                                "branch_not_found",
                                "Không tìm thấy chi nhánh"
                        ));

                if (branch.getStatus() != BranchStatus.ACTIVE) {
                    throw new ConflictException(
                            "branch_inactive",
                            "Không thể gán quản lý vào chi nhánh không hoạt động"
                    );
                }

                yield new Scope(
                        branch.getId(),
                        null
                );
            }

            case MEMBER -> {
                if (branchId != null || memberId == null) {
                    throw new ConflictException(
                            "invalid_member_scope",
                            "Tài khoản MEMBER phải liên kết đúng một hội viên"
                    );
                }

                Member member = memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new NotFoundException(
                                "member_not_found",
                                "Không tìm thấy hội viên"
                        ));

                if (member.getStatus() != MemberStatus.ACTIVE) {
                    throw new ConflictException(
                            "member_inactive",
                            "Không thể tạo tài khoản cho hội viên không hoạt động"
                    );
                }

                userRepository
                        .findByMemberId(member.getId())
                        .ifPresent(existing -> {
                            if (currentUserId == null
                                    || !existing.getId()
                                    .equals(currentUserId)) {
                                throw new ConflictException(
                                        "member_account_exists",
                                        "Hội viên đã có tài khoản đăng nhập"
                                );
                            }
                        });

                yield new Scope(
                        null,
                        member.getId()
                );
            }
        };
    }

    private void ensureEmailAvailable(
            String email,
            Long currentUserId
    ) {
        userRepository
                .findByEmailIgnoreCase(email)
                .ifPresent(existing -> {
                    if (currentUserId == null
                            || !existing.getId()
                            .equals(currentUserId)) {
                        throw new ConflictException(
                                "user_email_exists",
                                "Email tài khoản đã được sử dụng"
                        );
                    }
                });
    }

    private AppUser requireUser(Long id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "user_not_found",
                        "Không tìm thấy người dùng"
                ));
    }

    private String normalizeEmail(String email) {
        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRoleCode(),
                user.getStatus(),
                user.getBranchId(),
                user.getMemberId(),
                user.getCreatedAtUtc(),
                user.getUpdatedAtUtc()
        );
    }

    private record Scope(
            Long branchId,
            Long memberId
    ) {
    }
}