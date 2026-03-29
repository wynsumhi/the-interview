package com.theinterview.webhook.client;

import com.theinterview.webhook.dto.response.MattermostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class MattermostClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${notification.mattermost.webhook-url}")
    private String webhookUrl;

    /**
     * Mattermost Incoming Webhook을 통해 메시지를 전송합니다.
     *
     * @param payload 전송할 메시지 객체 (attachments)
     */
    public void sendMessage(MattermostResponse payload) {
        try {
            restTemplate.postForEntity(webhookUrl, payload, String.class);
        } catch (Exception e) {
            System.err.println("[Mattermost] 메시지 전송 실패 : " + e.getMessage());
        }
    }
}