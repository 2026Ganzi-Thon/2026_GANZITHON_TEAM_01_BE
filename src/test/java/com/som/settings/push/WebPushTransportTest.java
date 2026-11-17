package com.som.settings.push;

import com.som.common.error.ApiException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushTransportTest {

    @Test
    void preparesEncryptedPayloadAndVapidAuthorizationWithoutSending() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair vapid = generator.generateKeyPair();
        KeyPair browser = generator.generateKeyPair();
        var transport = new WebPushTransport(publicKey(vapid), encode(fixedWidth(
                ((ECPrivateKey) vapid.getPrivate()).getS().toByteArray())), "mailto:push@example.test");

        var target = new PushSubscriptions.Target(1L, "https://fcm.googleapis.com/fcm/send/test",
                publicKey(browser), encode(new byte[16]));
        var request = transport.prepare(target, new byte[]{10, 20, 30});

        assertThat(request.headers().firstValue("Authorization")).hasValueSatisfying(
                value -> assertThat(value).startsWith("vapid t="));
        assertThat(request.headers().firstValue("Content-Encoding")).hasValue("aes128gcm");
        assertThat(request.bodyPublisher().orElseThrow().contentLength()).isGreaterThan(3);
    }

    @Test
    void rejectsAnEndpointOutsideSupportedPushServices() {
        assertThatThrownBy(() -> WebPushController.validateEndpoint("https://fcm.googleapis.com@localhost/"))
                .isInstanceOf(ApiException.class);
    }

    private static String publicKey(KeyPair pair) {
        var point = ((ECPublicKey) pair.getPublic()).getW();
        byte[] encoded = new byte[65];
        encoded[0] = 4;
        System.arraycopy(fixedWidth(point.getAffineX().toByteArray()), 0, encoded, 1, 32);
        System.arraycopy(fixedWidth(point.getAffineY().toByteArray()), 0, encoded, 33, 32);
        return encode(encoded);
    }

    private static byte[] fixedWidth(byte[] value) {
        byte[] result = new byte[32];
        int length = Math.min(result.length, value.length);
        System.arraycopy(value, value.length - length, result, result.length - length, length);
        return result;
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
