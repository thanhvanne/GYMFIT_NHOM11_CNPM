package com.gymfit.chat;

public interface ChatProvider {

    String chat(
            String systemPrompt,
            String userMessage
    );
}