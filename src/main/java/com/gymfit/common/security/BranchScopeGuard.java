package com.gymfit.common.security;

import com.gymfit.common.error.ForbiddenException;
import com.gymfit.user.RoleCode;
import org.springframework.stereotype.Component;

@Component
public class BranchScopeGuard {

    public void requireBranch(AppPrincipal principal, Long branchId) {
        if (principal.getRole() == RoleCode.ADMIN) {
            return;
        }

        if (principal.getRole() != RoleCode.BRANCH_MANAGER) {
            throw new ForbiddenException(
                    "branch_scope_forbidden",
                    "Bạn không có quyền truy cập chi nhánh"
            );
        }

        if (principal.getBranchId() == null
                || !principal.getBranchId().equals(branchId)) {
            throw new ForbiddenException(
                    "branch_out_of_scope",
                    "Chi nhánh nằm ngoài phạm vi quản lý"
            );
        }
    }

    public void requireMemberSelf(AppPrincipal principal, Long memberId) {
        if (principal.getRole() != RoleCode.MEMBER) {
            return;
        }

        if (principal.getMemberId() == null
                || !principal.getMemberId().equals(memberId)) {
            throw new ForbiddenException(
                    "member_out_of_scope",
                    "Bạn không có quyền truy cập dữ liệu hội viên này"
            );
        }
    }
}