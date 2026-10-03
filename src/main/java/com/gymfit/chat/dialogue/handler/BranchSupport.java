package com.gymfit.chat.dialogue.handler;

import com.gymfit.branch.dto.BranchResponse;
import com.gymfit.common.error.ApiException;
import com.gymfit.membership.MembershipService;
import com.gymfit.membership.dto.MembershipResponse;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Chọn chi nhánh mặc định khi người dùng không nói rõ:
 * <ol>
 *     <li>Manager → chi nhánh của chính họ.</li>
 *     <li>Member → chi nhánh của membership (nếu có).</li>
 *     <li>Admin → chi nhánh đầu tiên đang ACTIVE.</li>
 *     <li>Không có → chi nhánh đầu tiên đang ACTIVE.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class BranchSupport {

    private final MembershipService membershipService;

    public BranchResponse defaultBranch(
            HandlerContext context,
            List<BranchResponse> branches
    ) {

        RoleCode role =
                context.principal()
                        .getRole();

        if (role == RoleCode.BRANCH_MANAGER) {

            Long branchId =
                    context.principal()
                            .getBranchId();

            if (branchId != null) {

                return branches.stream()
                        .filter(branch ->
                                branch.id()
                                        .equals(branchId)
                        )
                        .findFirst()
                        .orElse(
                                branches.get(0)
                        );
            }
        }

        if (role == RoleCode.MEMBER) {

            Long memberId =
                    context.principal()
                            .getMemberId();

            if (memberId != null) {

                Long branchId =
                        membershipBranchId(
                                context,
                                memberId
                        );

                if (branchId != null) {

                    BranchResponse found =
                            branches.stream()
                                    .filter(branch ->
                                            branch.id()
                                                    .equals(branchId)
                                    )
                                    .findFirst()
                                    .orElse(null);

                    if (found != null) {
                        return found;
                    }
                }
            }
        }

        return branches.get(0);
    }

    private Long membershipBranchId(
            HandlerContext context,
            Long memberId
    ) {

        try {

            MembershipResponse membership =
                    membershipService.current(
                            context.principal(),
                            memberId
                    );

            return membership == null
                    ? null
                    : membership.branchId();

        } catch (ApiException exception) {

            // Chưa có gói tập → không có chi nhánh mặc định
            return null;
        }
    }

}