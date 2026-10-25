
package com.hookforge.webhook;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
public class WebHookRequestSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public String sign(
            String secret,
            long timestamp,
            String requestBody
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "Webhook signing secret cannot be blank"
            );
        }

        if (requestBody == null) {
            throw new IllegalArgumentException(
                    "Request body cannot be null"
            );
        }

        String signedPayload = timestamp + "." + requestBody;

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);

            SecretKeySpec key = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );

            mac.init(key);

            byte[] signature = mac.doFinal(
                    signedPayload.getBytes(StandardCharsets.UTF_8)
            );

            return "t=" + timestamp
                    + ",v1=" + HexFormat.of().formatHex(signature);

        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "Could not generate webhook signature",
                    ex
            );
        }
    }
}
