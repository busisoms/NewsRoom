package com.newsroom.conversation;


/**
 * Failure counterpart to {@link Lookup}. Unchecked so callers that already
 * handle {@link Decision} don't need a new catch site, but can still switch
 * on {@link Reason} when they care.
 *
 * <p>Carries two separate texts: {@link #fallbackMessage()} is the friendly line
 * sent to the user, and the exception message ({@code detail}) is the technical
 * reason for the log. Neither should contain what the user typed, since the
 * exception message ends up in the log.
 */
public class LookupException extends RuntimeException {
    public enum Reason {
        NOT_FOUND,
        UNAVAILABLE
    }
    private final Reason reason;
    private final String fallbackMessage;

    /**
     * @param reason why the lookup failed
     * @param fallbackMessage what to send the user instead of the result
     * @param detail technical description for the log
     */
    public LookupException(Reason reason, String fallbackMessage, String detail) {
        this(reason, fallbackMessage, detail, null);
    }

    /**
     * @param reason why the lookup failed
     * @param fallbackMessage what to send the user instead of the result
     * @param detail technical description for the log
     * @param cause the underlying failure, or {@code null}
     */
    public LookupException(Reason reason, String fallbackMessage, String detail, Throwable cause){
        super(reason.name() + ": " + detail, cause);
        this.reason = reason;
        this.fallbackMessage = fallbackMessage;
    }

    public Reason reason() {
        return reason;
    }

    public String fallbackMessage() {
        return fallbackMessage;
    }

}
