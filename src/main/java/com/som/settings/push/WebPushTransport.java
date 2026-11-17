package com.som.settings.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Security;
import java.time.Duration;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "som.push.enabled", havingValue = "true")
class WebPushTransport {

    private final String publicKey;
    private final PushService push;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    WebPushTransport(@Value("${som.push.public-key:}") String publicKey,
                     @Value("${som.push.private-key:}") String privateKey,
                     @Value("${som.push.subject:}") String subject) {
        if (publicKey.isBlank() || privateKey.isBlank() || subject.isBlank()) {
            throw new IllegalStateException("Web Push requires a VAPID key pair and contact subject");
        }
        this.publicKey = publicKey;
        Security.addProvider(new BouncyCastleProvider());
        try {
            push = new PushService(publicKey, privateKey, subject);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Web Push VAPID configuration");
        }
    }

    String publicKey() {
        return publicKey;
    }

    int send(PushSubscriptions.Target target, byte[] payload) throws Exception {
        return http.send(prepare(target, payload), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    HttpRequest prepare(PushSubscriptions.Target target, byte[] payload) throws Exception {
        WebPushController.validateEndpoint(target.endpoint());
        Notification message = new Notification(target.endpoint(), target.key(), target.auth(), payload, 300);
        var prepared = push.preparePost(message, Encoding.AES128GCM);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(target.endpoint()))
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofByteArray(EntityUtils.toByteArray(prepared.getEntity())));
        for (var header : prepared.getAllHeaders()) {
            request.header(header.getName(), header.getValue());
        }
        return request.build();
    }
}
