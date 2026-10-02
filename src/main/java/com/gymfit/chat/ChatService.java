package com.gymfit.chat;

import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Placeholder trong lúc dựng chatbot local (T0).
 * Sẽ được viết lại hoàn chỉnh ở T10.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    public ChatResponse chat(
            AppPrincipal principal,
            String message
    ) {
        return new ChatResponse(
                "Trợ lý GYMFIT đang được nâng cấp, bạn vui lòng tạm thời dùng các chức năng trên trang.",
                TimeUtil.now()
        );
    }
}