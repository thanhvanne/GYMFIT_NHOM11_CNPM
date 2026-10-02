package com.gymfit.chat.nlu;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật scheduler cho chatbot (hiện chỉ dùng để làm mới gazetteer mỗi 10 phút).
 * Tách riêng để không phải chạm vào {@code GymFitApplication}.
 */
@Configuration
@EnableScheduling
public class ChatbotSchedulingConfig {
}