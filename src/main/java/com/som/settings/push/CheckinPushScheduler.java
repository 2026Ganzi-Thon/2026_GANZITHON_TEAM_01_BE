package com.som.settings.push;

import com.som.common.time.ServiceDates;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "som.push.enabled", havingValue = "true")
class CheckinPushScheduler {

    private static final Logger log = LoggerFactory.getLogger(CheckinPushScheduler.class);
    private static final byte[] PAYLOAD = """
            {"title":"솜","body":"오늘 마음을 잠깐 돌아볼까요?","tag":"som-daily-checkin","url":"/"}
            """.getBytes(StandardCharsets.UTF_8);

    private final PushSubscriptions subscriptions;
    private final WebPushTransport transport;
    private final ServiceDates dates;

    CheckinPushScheduler(PushSubscriptions subscriptions, WebPushTransport transport, ServiceDates dates) {
        this.subscriptions = subscriptions;
        this.transport = transport;
        this.dates = dates;
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    void sendDueReminders() {
        var now = dates.now();
        for (var target : subscriptions.claimDue(now.toLocalDate(), now.toLocalTime())) {
            try {
                int status = transport.send(target, PAYLOAD);
                if (status == 404 || status == 410) {
                    subscriptions.removeExpired(target);
                } else if (status < 200 || status >= 300) {
                    log.warn("Web Push rejected for subscription {}: HTTP {}", target.id(), status);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Web Push failed for subscription {}: {}", target.id(), e.getClass().getSimpleName());
            }
        }
    }
}
