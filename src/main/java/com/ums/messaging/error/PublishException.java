package com.ums.messaging.error;

public class PublishException extends RuntimeException {
    public PublishException(String message) { super(message); }
    public PublishException(String message, Throwable cause) { super(message, cause); }
}
