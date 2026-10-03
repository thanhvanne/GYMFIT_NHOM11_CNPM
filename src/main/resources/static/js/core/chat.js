const GymfitChat = {
    messages: [],

    /**
     * Mã phiên do server cấp — giữ trong sessionStorage để đa lượt hội thoại
     * (điền slot, xác nhận đặt/hủy lịch) không bị mất khi tải lại trang.
     */
    sessionId: sessionStorage.getItem("gymfit_chat_session") || null,

    init() {
        const toggle =
            document.getElementById(
                "ai-chat-toggle"
            );

        const close =
            document.getElementById(
                "ai-chat-close"
            );

        const form =
            document.getElementById(
                "ai-chat-form"
            );

        if (!toggle || !form) {
            return;
        }

        toggle.addEventListener(
            "click",
            () => this.open()
        );

        if (close) {
            close.addEventListener(
                "click",
                () => this.close()
            );
        }

        form.addEventListener(
            "submit",
            event =>
                this.submit(event)
        );

        this.addAssistant(
            "Xin chào! Tôi là GYMFIT AI. Tôi có thể hỗ trợ gì cho bạn?"
        );
    },

    open() {
        document.getElementById(
            "ai-chat-panel"
        ).classList.remove(
            "hidden"
        );

        document.getElementById(
            "ai-chat-input"
        ).focus();
    },

    close() {
        document.getElementById(
            "ai-chat-panel"
        ).classList.add(
            "hidden"
        );
    },

    async submit(event) {
        event.preventDefault();

        const input =
            document.getElementById(
                "ai-chat-input"
            );

        const message =
            input.value.trim();

        if (!message) {
            return;
        }

        input.value = "";

        await this.send(message);
    },

    /**
     * Gửi một lượt: câu người dùng hoặc payload nút bấm
     * ({@code TEXT:x} | {@code CONFIRM:id} | {@code CANCEL:id}).
     */
    async send(value) {

        if (!value) {
            return;
        }

        const isPayload =
            /^(CONFIRM|CANCEL|TEXT):/.test(
                value
            );

        const label =
            isPayload && value.startsWith("TEXT:")
                ? value.substring(5)
                : value;

        this.addUser(
            this.actionLabel(value, label)
        );

        const button =
            document.getElementById(
                "ai-chat-send"
            );

        const input =
            document.getElementById(
                "ai-chat-input"
            );

        if (button) {
            button.disabled = true;
        }

        if (input) {
            input.disabled = true;
        }

        const typing =
            this.addTyping();

        try {

            const body =
                {
                    sessionId: this.sessionId
                };

            if (isPayload) {
                body.payload = value;
            } else {
                body.message = value;
            }

            const response =
                await Api.post(
                    "/api/v1/chat",
                    body
                );

            typing.remove();

            if (response.sessionId) {
                this.sessionId =
                    response.sessionId;

                sessionStorage.setItem(
                    "gymfit_chat_session",
                    this.sessionId
                );
            }

            const bubble =
                this.addAssistant(
                    response.message
                );

            if (response.card) {
                this.renderCard(
                    bubble,
                    response.card
                );
            }

            if (response.suggestions
                && response.suggestions.length) {
                this.renderSuggestions(
                    bubble,
                    response.suggestions
                );
            }

            if (response.messageId) {
                this.renderFeedback(
                    bubble,
                    response.messageId
                );
            }

        } catch (error) {

            typing.remove();

            this.addAssistant(
                error.message
                || "Tôi chưa thể trả lời lúc này."
            );

        } finally {

            if (button) {
                button.disabled = false;
            }

            if (input) {
                input.disabled = false;
                input.focus();
            }
        }
    },

    /** Câu hiển thị cho nút bấm: "✔ Xác nhận" / "✖ Hủy". */
    actionLabel(value, fallback) {

        if (value.startsWith("CONFIRM:")) {
            return "✔ Xác nhận";
        }

        if (value.startsWith("CANCEL:")) {
            return "✖ Hủy";
        }

        return fallback;
    },

    renderCard(bubble, card) {

        const wrap =
            document.createElement("div");

        wrap.className =
            "ai-chat-card";

        const title =
            document.createElement("div");

        title.className =
            "ai-chat-card-title";

        title.textContent =
            card.title || "";

        wrap.appendChild(title);

        (card.lines || []).forEach(line => {

            const row =
                document.createElement("div");

            row.className =
                "ai-chat-card-line";

            const label =
                document.createElement("span");

            label.textContent =
                line.label + ":";

            const value =
                document.createElement("strong");

            value.textContent =
                line.value;

            row.appendChild(label);
            row.appendChild(value);

            wrap.appendChild(row);
        });

        if (card.type === "CONFIRM") {

            const actions =
                document.createElement("div");

            actions.className =
                "ai-chat-card-actions";

            const confirm =
                this.button(
                    "Xác nhận",
                    card.confirmPayload,
                    "button button-primary"
                );

            const cancel =
                this.button(
                    card.title && card.title.indexOf("hủy") >= 0
                        ? "Giữ lịch"
                        : "Hủy",
                    card.cancelPayload,
                    "button"
                );

            actions.appendChild(confirm);
            actions.appendChild(cancel);

            wrap.appendChild(actions);
        }

        bubble.appendChild(wrap);
    },

    renderSuggestions(bubble, suggestions) {

        const wrap =
            document.createElement("div");

        wrap.className =
            "ai-chat-suggestions";

        suggestions.forEach(item =>
            wrap.appendChild(
                this.button(
                    item.label,
                    item.payload,
                    "ai-chat-chip"
                )
            )
        );

        bubble.appendChild(wrap);
    },

    renderFeedback(bubble, messageId) {

        const wrap =
            document.createElement("div");

        wrap.className =
            "ai-chat-feedback";

        const send =
            (value, text) => {

                const button =
                    document.createElement("button");

                button.type = "button";
                button.textContent = text;

                button.addEventListener(
                    "click",
                    async () => {

                        button.disabled = true;

                        try {

                            await Api.post(
                                "/api/v1/chat/feedback",
                                {
                                    sessionId: this.sessionId,
                                    messageId,
                                    feedback: value
                                }
                            );

                            wrap.textContent =
                                "Cảm ơn bạn đã phản hồi!";

                        } catch (error) {
                            button.disabled = false;
                        }
                    }
                );

                return button;
            };

        wrap.appendChild(
            send(
                "UP",
                "👍"
            )
        );

        wrap.appendChild(
            send(
                "DOWN",
                "👎"
            )
        );

        bubble.appendChild(wrap);
    },

    button(label, payload, className) {

        const button =
            document.createElement("button");

        button.type = "button";
        button.className = className;
        button.textContent = label;

        button.addEventListener(
            "click",
            () => {

                if (!payload) {
                    return;
                }

                // Một lần bấm một lượt: chặn bấm chuỗi nhanh làm loạn phiên
                const parent =
                    button.parentElement;

                if (parent) {
                    Array.from(
                        parent.children
                    ).forEach(child =>
                        child.disabled = true
                    );
                }

                this.send(payload);
            }
        );

        return button;
    },

    addUser(message) {
        return this.appendMessage(
            "user",
            message
        );
    },

    addAssistant(message) {
        return this.appendMessage(
            "assistant",
            message
        );
    },

    appendMessage(role, message) {
        const container =
            document.getElementById(
                "ai-chat-messages"
            );

        if (!container) {
            return null;
        }

        const wrapper =
            document.createElement("div");

        wrapper.className =
            `ai-message ai-message-${role}`;

        const bubble =
            document.createElement("div");

        bubble.className =
            "ai-message-bubble";

        bubble.textContent = message;

        wrapper.appendChild(bubble);
        container.appendChild(wrapper);

        container.scrollTop =
            container.scrollHeight;

        return bubble;
    },

    addTyping() {
        const container =
            document.getElementById(
                "ai-chat-messages"
            );

        const wrapper =
            document.createElement("div");

        wrapper.className =
            "ai-message ai-message-assistant";

        const bubble =
            document.createElement("div");

        bubble.className =
            "ai-message-bubble ai-typing";

        bubble.textContent =
            "GYMFIT AI đang trả lời...";

        wrapper.appendChild(bubble);
        container.appendChild(wrapper);

        container.scrollTop =
            container.scrollHeight;

        return wrapper;
    }
};

window.GymfitChat = GymfitChat;

document.addEventListener(
    "DOMContentLoaded",
    () => GymfitChat.init()
);
