package com.theinterview.webhook.service;

import com.theinterview.webhook.client.MattermostClient;
import com.theinterview.webhook.converter.GitlabConverter;
import com.theinterview.webhook.dto.request.GitlabRequest;
import com.theinterview.webhook.dto.response.MattermostResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 이벤트를 가공하여 MM에 마크다운 형식으로 전달하는 서비스 레이어입니다.
 */
@Service
@RequiredArgsConstructor
public class WebhookService {

    // 알림을 보낼 MM 채널 이름 ( gitlab용 )
    @Value("${notification.mattermost.gitlab-channel}")
    private String gitlabChannel;

    private final GitlabConverter gitlabConverter;
    private final MattermostClient mattermostClient;

    /**
     * Gitlab 웹훅 데이터를 처리하여 마크다운 형식 메시지를 생성하고 전송합니다.
     *
     * @param request 수신된 원본 데이터 객체
     */
    public void processGitlab(GitlabRequest request) {
        String markdown = gitlabConverter.convert(request);
        String color = switch (request.objectKind()) {
            case "push" -> "#2196F3";
            case "merge_request" -> {
                if ("merge".equals(request.attributes().action())) {
                    yield "#008000";
                }
                yield "#FF9800";
            }
            default -> "#808080";
        };

        //MM의 Attachment 첨부 레이아웃 구조 생성
        MattermostResponse.Attachment attachment = new MattermostResponse.Attachment(color,
                markdown);
        // 첫 번째 인자(text)는 빈 문자열로 두어 Attachment 위주의 깔끔한 디자인 유지
        MattermostResponse payload = new MattermostResponse("", gitlabChannel,
                List.of(attachment));
        mattermostClient.sendMessage(payload);
    }
}
