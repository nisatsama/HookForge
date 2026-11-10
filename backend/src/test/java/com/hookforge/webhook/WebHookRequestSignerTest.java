
package com.hookforge.webhook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebHookRequestSignerTest {

    private final WebHookRequestSigner signer =
            new WebHookRequestSigner();

    @Test
    void shouldGenerateCorrectHmacSha256Signature() {

        String secret = "hookforge_test_secret_123";
        long timestamp = 1730000000L;

        String requestBody =
                "{\"event\":\"order.created\",\"id\":\"evt_123\"}";

        String expectedSignature =
                "t=1730000000,v1=8a82dc73542ffbe102f526305f074576fbe291cfd384c403f37419a13bdb3870";

        String actualSignature = signer.sign(
                secret,
                timestamp,
                requestBody
        );

        assertEquals(expectedSignature, actualSignature);
    }
}
