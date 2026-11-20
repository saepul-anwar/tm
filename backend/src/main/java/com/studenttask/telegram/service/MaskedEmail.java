package com.studenttask.telegram.service;

/**
 * Masks an email for places where it is shown to a third party (a Telegram chat): the domain stays, the local part
 * keeps at most its first character ({@code alice@example.test} becomes {@code a***@example.test}). Local parts
 * shorter than 3 characters show nothing of themselves so they are not revealed almost whole. Malformed input yields
 * a fixed placeholder, never the input.
 */
public final class MaskedEmail {

    static final String MASK = "***";
    private static final int MIN_LOCAL_LENGTH_FOR_FIRST_CHAR = 3;

    private MaskedEmail() {
    }

    public static String of(String email) {
        if (email == null) {
            return MASK;
        }
        String value = email.trim();
        int at = value.lastIndexOf('@');
        if (at < 0 || at == value.length() - 1) {
            return MASK;
        }
        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        if (local.codePointCount(0, local.length()) < MIN_LOCAL_LENGTH_FOR_FIRST_CHAR) {
            return MASK + '@' + domain;
        }
        return new String(Character.toChars(local.codePointAt(0))) + MASK + '@' + domain;
    }
}
