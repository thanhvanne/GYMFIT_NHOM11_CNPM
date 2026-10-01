document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user =
            Auth.requireRole("MEMBER");

        if (!user) {
            return;
        }

        document.getElementById(
            "logout-button"
        ).addEventListener(
            "click",
            () => Auth.logout()
        );

        try {
            const member =
                await Api.get(
                    `/api/v1/members/${user.memberId}`
                );

            const branch =
                await Api.get(
                    `/api/v1/branches/${member.homeBranchId}`
                );

            renderMember(
                member,
                branch
            );

            try {
                const membership =
                    await Api.get(
                        `/api/v1/members/${user.memberId}/memberships/current`
                    );

                const plan =
                    await Api.get(
                        `/api/v1/plans/${membership.planId}`
                    );

                renderMembership(
                    membership,
                    plan
                );
            } catch (error) {
                if (error.status === 404) {
                    renderNoMembership();
                } else {
                    throw error;
                }
            }
        } catch (error) {
            showError(
                error.message
                || "Không tải được hồ sơ"
            );
        }
    }
);

function renderMember(
    member,
    branch
) {
    document.getElementById(
        "profile-avatar"
    ).textContent =
        member.fullName
            .trim()
            .charAt(0)
            .toUpperCase();

    document.getElementById(
        "profile-name"
    ).textContent =
        member.fullName;

    document.getElementById(
        "profile-code"
    ).textContent =
        member.memberCode;

    document.getElementById(
        "profile-email"
    ).textContent =
        member.email || "—";

    document.getElementById(
        "profile-phone"
    ).textContent =
        member.phone;

    document.getElementById(
        "profile-branch"
    ).textContent =
        branch.name;

    document.getElementById(
        "profile-dob"
    ).textContent =
        formatDate(
            member.dateOfBirth
        );
}

function renderMembership(
    membership,
    plan
) {
    document.getElementById(
        "profile-membership-name"
    ).textContent =
        plan.name;

    document.getElementById(
        "profile-membership-status"
    ).textContent =
        membership.status;

    document.getElementById(
        "profile-start"
    ).textContent =
        formatDate(
            membership.startDate
        );

    document.getElementById(
        "profile-end"
    ).textContent =
        formatDate(
            membership.endDate
        );
}

function renderNoMembership() {
    document.getElementById(
        "profile-membership-name"
    ).textContent =
        "Chưa có gói tập";

    document.getElementById(
        "profile-membership-status"
    ).textContent =
        "NONE";

    document.getElementById(
        "profile-start"
    ).textContent = "—";

    document.getElementById(
        "profile-end"
    ).textContent = "—";
}

function formatDate(value) {
    if (!value) {
        return "—";
    }

    const [y, m, d] =
        value.split("-");

    return `${d}/${m}/${y}`;
}

function showError(message) {
    const box =
        document.getElementById(
            "page-error"
        );

    box.textContent = message;
    box.classList.remove("hidden");
}