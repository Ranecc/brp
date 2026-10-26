package dev.brp.common;

/** Client error with an optional i18n message key (falls back to raw text). */
public class BadRequestException extends RuntimeException {

    private final String messageKey;
    private final Object[] args;

    public BadRequestException(String message) {
        super(message);
        this.messageKey = null;
        this.args = new Object[0];
    }

    /** @param messageKey messages[_xx].properties key; the copy judges you there */
    public static BadRequestException of(String messageKey, Object... args) {
        return new BadRequestException(messageKey, args);
    }

    private BadRequestException(String messageKey, Object[] args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = args;
    }

    public String messageKey() { return messageKey; }
    public Object[] args() { return args; }
}
