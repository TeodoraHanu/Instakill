package com.instakill.post.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class NotificationService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String NTFY_TOPIC_URL = "https://ntfy.sh/instakill-demo-2026";

    @Async
    public void sendPostCreatedNotification(String username, UUID postId) {
        try {
            String message = String.format("New post created by %s (Post ID: %s) at %s",
                    username, postId, LocalDateTime.now());

            restTemplate.postForEntity(NTFY_TOPIC_URL, message, String.class);
            System.out.println("Notification sent successfully to ntfy.sh for post ID: " + postId);
        } catch (Exception e) {
            System.err.println("Failed to send notification: " + e.getMessage());
        }
    }
}