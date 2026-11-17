package com.som.settings.push;

import com.som.auth.web.CurrentUserId;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Base64;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/push")
@ConditionalOnProperty(name = "som.push.enabled", havingValue = "true")
class WebPushController {

    private final PushSubscriptions subscriptions;
    private final WebPushTransport transport;

    WebPushController(PushSubscriptions subscriptions, WebPushTransport transport) {
        this.subscriptions = subscriptions;
        this.transport = transport;
    }

    @GetMapping("/public-key")
    PublicKey publicKey() {
        return new PublicKey(transport.publicKey());
    }

    @PutMapping("/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void subscribe(@CurrentUserId Long userId, @Valid @RequestBody Subscription body) {
        validateEndpoint(body.endpoint());
        byte[] key = decode(body.keys().p256dh());
        if (key.length != 65 || key[0] != 4 || decode(body.keys().auth()).length != 16) {
            throw invalid();
        }
        subscriptions.save(userId, body.endpoint(), body.keys().p256dh(), body.keys().auth());
    }

    @DeleteMapping("/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribe(@CurrentUserId Long userId, @Valid @RequestBody Endpoint body) {
        subscriptions.remove(userId, body.endpoint());
    }

    static void validateEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw invalid();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.normalize().getPath();
        if (path == null || uri.getRawQuery() != null || !path.equals(uri.getRawPath())) {
            throw invalid();
        }
        boolean supported = (host.equals("fcm.googleapis.com") && path.startsWith("/fcm/send/"))
                || (host.equals("web.push.apple.com") && path.startsWith("/"))
                || (host.equals("updates.push.services.mozilla.com") && path.startsWith("/wpush/"));
        if (!supported) {
            throw invalid();
        }
    }

    private static byte[] decode(String encoded) {
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "웹 푸시 구독 정보를 확인해 주세요.");
    }

    record PublicKey(String publicKey) {}
    record Endpoint(@NotBlank @Size(max = 2048) String endpoint) {}
    record Subscription(@NotBlank @Size(max = 2048) String endpoint, @Valid @NotNull Keys keys) {}
    record Keys(@NotBlank @Size(max = 128) String p256dh, @NotBlank @Size(max = 32) String auth) {}
}
