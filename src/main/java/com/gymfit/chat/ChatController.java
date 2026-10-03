package com.gymfit.chat;

import com.gymfit.chat.dto.ChatRequest;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.dto.FeedbackRequest;
import com.gymfit.common.security.SecurityContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SecurityContextService securityContextService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request
    ) {
        return chatService.chat(
                securityContextService.principal(),
                request
        );
    }

    /**
     * Đánh giá 👍/👎 một tin nhắn của bot.
     */
    @PostMapping("/feedback")
    @PreAuthorize("isAuthenticated()")
    public ChatResponse feedback(
            @Valid @RequestBody FeedbackRequest request
    ) {
        return chatService.feedback(
                securityContextService.principal(),
                request
        );
    }
}
