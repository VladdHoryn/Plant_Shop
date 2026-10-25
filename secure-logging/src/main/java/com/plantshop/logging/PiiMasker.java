package com.plantshop.logging;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The logging policy, applied by {@link MaskingEncoder} to every rendered log line:
 * <ul>
 *   <li><b>Forbidden secrets</b> (password, token, API key, authorization, session, cookie, credentials)
 *       are replaced completely with {@value #REDACTED}: as key=value, key: value, JSON, escaped JSON,
 *       URL user-info, bearer token or JWT.</li>
 *   <li><b>Forbidden personal data</b> (names, postal addresses, bank data) is redacted the same way;
 *       code must not log it at all, this is the safety net.</li>
 *   <li><b>Masked identifiers</b>: e-mail addresses and phone numbers keep only a few characters, so
 *       support can still match a log line to a ticket.</li>
 *   <li><b>Allowed</b>: technical ids, correlation id, operation, status, amounts, timestamps.</li>
 * </ul>
 * Detection is pattern based, so a secret logged under an unknown key name or as free text is not
 * recognised; the deny-list below is the documented policy and the tests pin it down.
 */
public final class PiiMasker {

    public static final String REDACTED = "[REDACTED]";

    private static final String SECRET_KEYS =
            "password|passwd|pwd|secret|token|api[_-]?key|authorization|cookie|session|credential|cvv";
    private static final String FORBIDDEN_PII_KEYS =
            "full[_-]?name|first[_-]?name|last[_-]?name|address|street|postal[_-]?code|iban|card[_-]?number";
    private static final String PHONE_KEYS = "phone|mobile|msisdn";

    // Escaped JSON string (JSON logged inside a JSON message), JSON string, or single-quoted string
    private static final String QUOTED_VALUE =
            "\\\\\"(?:(?!\\\\\").)*\\\\\"|\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'";
    // The start of the next pair: key=, key:, "key":, \"key\":
    private static final String NEXT_KEY = "\\\\?[\"']?[\\w.-]+\\\\?[\"']?\\s*[=:]";
    // A bare value (optionally "Bearer xyz") runs to the next pair, a delimiter or the end of the line,
    // so "fullName=Ann Lee, email=..." and "address=1 Green St, Kyiv" are covered entirely
    private static final String BARE_VALUE =
            "(?:(?:bearer|basic|digest)\\s+)?[^\\s\"'{\\[\\\\,;&\\]})][^\\r\\n]*?"
                    + "(?=\\s*,\\s*" + NEXT_KEY + "|\\s+" + NEXT_KEY + "|[;&\\]})\"'\\\\\\r\\n]|\\s*$)";
    private static final String PHONE_VALUE = "\\+?\\d(?:[\\s().-]?\\d){6,}";

    private static final Pattern URL_CREDENTIALS = Pattern.compile("(?<=://)[^/\\s:@]+:[^/\\s@]+(?=@)");
    private static final Pattern JWT = Pattern.compile("\\beyJ[\\w-]+\\.[\\w-]+\\.[\\w-]*");
    private static final Pattern SECRET_PAIR = keyValue(SECRET_KEYS, QUOTED_VALUE + "|" + BARE_VALUE);
    private static final Pattern BEARER = Pattern.compile("(?i)\\b(bearer)\\s+[\\w.~+/=-]{8,}");
    private static final Pattern FORBIDDEN_PAIR = keyValue(FORBIDDEN_PII_KEYS, QUOTED_VALUE + "|" + BARE_VALUE);
    private static final Pattern PHONE_PAIR = keyValue(PHONE_KEYS, QUOTED_VALUE + "|" + PHONE_VALUE);
    private static final Pattern PHONE = Pattern.compile("(?<![\\w+*])\\+\\d(?:[\\s().-]?\\d){8,14}(?!\\d)");
    // "%40" is a URL-encoded "@" (e-mail in a query string)
    private static final Pattern EMAIL =
            Pattern.compile("(?<![\\w.%+-])([\\w.%+-])[\\w.%+-]*(@|%40)((?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,})");

    private PiiMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = URL_CREDENTIALS.matcher(text).replaceAll(REDACTED);
        result = JWT.matcher(result).replaceAll(REDACTED);
        result = replaceValues(result, SECRET_PAIR, value -> REDACTED);
        result = BEARER.matcher(result).replaceAll("$1 " + REDACTED);
        result = replaceValues(result, FORBIDDEN_PAIR, value -> REDACTED);
        result = replaceValues(result, PHONE_PAIR, PiiMasker::maskPhone);
        result = PHONE.matcher(result).replaceAll(m -> Matcher.quoteReplacement(maskPhone(m.group())));
        return EMAIL.matcher(result).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + "***" + m.group(2) + m.group(3)));
    }

    /**
     * {@code +447700900123 -> +44********23}; keeps the country prefix and the last two digits.
     * Values with fewer than 7 digits are not phone numbers (or are already masked) and stay as they are.
     */
    static String maskPhone(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return value;
        }
        String prefix = value.startsWith("+") ? "+" + digits.substring(0, 2) : "";
        int hidden = digits.length() - 2 - (prefix.isEmpty() ? 0 : 2);
        return prefix + "*".repeat(hidden) + digits.substring(digits.length() - 2);
    }

    /**
     * key=value, key: value, "key":"value", \"key\":\"value\" (JSON inside a JSON string), 'key': value.
     * The key is any token containing one of {@code keys} ("accessToken", "x-api-key", "shippingAddress"),
     * but not a source file name, so stack frames such as {@code PasswordService.java:42} stay intact.
     * The colon must follow the key directly (unless the key is quoted), which keeps the
     * {@code "logger : message"} separator of the text log pattern from being taken for a pair.
     */
    private static Pattern keyValue(String keys, String valuePattern) {
        return Pattern.compile(
                "(?<prefix>(?<![\\w.-])(?<q>\\\\?[\"']?)[\\w.-]*(?:" + keys + ")[\\w.-]*(?<!\\.java)(?<!\\.kt)\\k<q>"
                        + "(?:\\s*=\\s*|:\\s*|(?<=[\"'])\\s*:\\s*))"
                        + "(?<value>" + valuePattern + ")",
                Pattern.CASE_INSENSITIVE);
    }

    private static String replaceValues(String text, Pattern pattern, UnaryOperator<String> masker) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            String masked = maskValue(matcher.group("value"), matcher.group("q"), masker);
            matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group("prefix") + masked));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String maskValue(String value, String keyQuote, UnaryOperator<String> masker) {
        if (value.startsWith("\\\"")) {
            return "\\\"" + masker.apply(value.substring(2, value.length() - 2)) + "\\\"";
        }
        char first = value.charAt(0);
        if (first == '"' || first == '\'') {
            return first + masker.apply(value.substring(1, value.length() - 1)) + first;
        }
        // A bare JSON value ("token": 123, "password": null) becomes a string, so the line stays valid JSON
        return keyQuote.endsWith("\"") ? keyQuote + masker.apply(value) + keyQuote : masker.apply(value);
    }
}
