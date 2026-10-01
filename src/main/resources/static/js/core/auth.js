const Auth = {
    tokenKey: "gymfit_access_token",
    userKey: "gymfit_current_user",

    save(loginResponse) {
        localStorage.setItem(
            this.tokenKey,
            loginResponse.accessToken
        );

        localStorage.setItem(
            this.userKey,
            JSON.stringify(loginResponse.user)
        );
    },

    token() {
        return localStorage.getItem(
            this.tokenKey
        );
    },

    user() {
        const value = localStorage.getItem(
            this.userKey
        );

        if (!value) {
            return null;
        }

        try {
            return JSON.parse(value);
        } catch {
            return null;
        }
    },

    logout() {
        localStorage.removeItem(this.tokenKey);
        localStorage.removeItem(this.userKey);
        location.href = "/login";
    },

    redirectByRole(user) {
        switch (user.role) {
            case "ADMIN":
                location.href = "/admin/dashboard";
                break;

            case "BRANCH_MANAGER":
                location.href = "/manager/dashboard";
                break;

            case "MEMBER":
                location.href = "/member/home";
                break;

            default:
                this.logout();
        }
    },

    requireRole(role) {
        const user = this.user();

        if (!this.token() || !user) {
            location.href = "/login";
            return null;
        }

        if (user.role !== role) {
            this.redirectByRole(user);
            return null;
        }

        return user;
    }
};

window.Auth = Auth;