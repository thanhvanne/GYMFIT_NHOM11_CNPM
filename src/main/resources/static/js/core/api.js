const Api = {
    token() {
        return localStorage.getItem("gymfit_access_token");
    },

    async request(url, options = {}) {
        const headers = new Headers(
            options.headers || {}
        );

        headers.set("Accept", "application/json");

        if (options.body !== undefined
            && !(options.body instanceof FormData)) {
            headers.set(
                "Content-Type",
                "application/json"
            );
        }

        const token = this.token();

        if (token) {
            headers.set(
                "Authorization",
                `Bearer ${token}`
            );
        }

        const response = await fetch(url, {
            ...options,
            headers
        });

        if (response.status === 401) {
            localStorage.removeItem(
                "gymfit_access_token"
            );
            localStorage.removeItem(
                "gymfit_current_user"
            );

            if (location.pathname !== "/login") {
                location.href = "/login";
            }
        }

        const contentType =
            response.headers.get("content-type") || "";

        let body = null;

        if (contentType.includes("application/json")) {
            body = await response.json();
        } else if (response.status !== 204) {
            body = await response.text();
        }

        if (!response.ok) {
            const error = new Error(
                body?.message || "Có lỗi xảy ra"
            );

            error.status = response.status;
            error.code = body?.code;
            error.errors = body?.errors || {};
            error.body = body;

            throw error;
        }

        return body;
    },

    get(url) {
        return this.request(url);
    },

    post(url, data) {
        return this.request(url, {
            method: "POST",
            body: JSON.stringify(data)
        });
    },

    put(url, data) {
        return this.request(url, {
            method: "PUT",
            body: JSON.stringify(data)
        });
    },

    patch(url, data) {
        return this.request(url, {
            method: "PATCH",
            body: JSON.stringify(data)
        });
    }
};

window.Api = Api;