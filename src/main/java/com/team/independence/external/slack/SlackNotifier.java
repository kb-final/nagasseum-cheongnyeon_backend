package com.team.independence.external.slack;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotifier {

    private static final String DEDUP_KEY_PREFIX = "slack:batch-fail:";
    private static final Duration DEDUP_TTL = Duration.ofMinutes(30);

    @Value("${slack.webhook.url:}")
    private String webhookUrl;

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;

    /** batchName별로 dedup key를 나눠, 서로 다른 배치의 실패 알림이 30분 내 겹쳐도 한쪽이 다른 쪽을 억제하지 않게 한다. */
    public void sendBatchFailureSummary(String batchName, String message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("[Slack] webhook URL이 설정되지 않아 알림을 건너뜁니다.");
            return;
        }

        String dedupKey = DEDUP_KEY_PREFIX + batchName + ":" + LocalDate.now();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(dedupKey))) {
            log.info("[Slack] 중복 알림 억제 (30분 내 이미 전송): key={}", dedupKey);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"text\": \"" + escapeJson(message) + "\"}";
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            restTemplate.exchange(webhookUrl, HttpMethod.POST, request, String.class);
            redisTemplate.opsForValue().set(dedupKey, "1", DEDUP_TTL);
            log.info("[Slack] 배치 실패 알림 전송 완료");
        } catch (Exception e) {
            log.error("[Slack] 알림 전송 실패 (배치는 계속 진행)", e);
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n");
    }
}
