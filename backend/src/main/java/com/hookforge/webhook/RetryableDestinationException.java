
package com.hookforge.webhook;

public class RetryableDestinationException extends RuntimeException {

    public RetryableDestinationException(String message, Throwable cause) {
        super(message, cause);
    }
}
