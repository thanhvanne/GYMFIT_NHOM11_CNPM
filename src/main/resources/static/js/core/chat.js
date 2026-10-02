const GymfitChat = {
    messages: [],

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

        const button =
            document.getElementById(
                "ai-chat-send"
            );

        const message =
            input.value.trim();

        if (!message) {
            return;
        }

        this.addUser(message);

        input.value = "";
        button.disabled = true;

        const typing =
            this.addTyping();

        try {
            const response =
                await Api.post(
                    "/api/v1/chat",
                    {
                        message
                    }
                );

            typing.remove();

            this.addAssistant(
                response.message
            );

        } catch (error) {
            typing.remove();

            this.addAssistant(
                error.message
                || "Tôi chưa thể trả lời lúc này."
            );
        } finally {
            button.disabled = false;
            input.focus();
        }
    },

    addUser(message) {
        this.appendMessage(
            "user",
            message
        );
    },

    addAssistant(message) {
        this.appendMessage(
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
            return;
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

window.GymfitChat =
    GymfitChat;

document.addEventListener(
    "DOMContentLoaded",
    () => GymfitChat.init()
);