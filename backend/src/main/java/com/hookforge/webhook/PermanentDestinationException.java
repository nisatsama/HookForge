
package com.hookforge.webhook;

public class PermanentDestinationException extends IllegalArgumentException {

    public PermanentDestinationException(String message) {
        super(message);
    }

    public PermanentDestinationException(String message, Throwable cause) {
        super(message, cause);
    }
}
