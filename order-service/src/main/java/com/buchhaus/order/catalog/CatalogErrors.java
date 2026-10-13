package com.buchhaus.order.catalog;

import com.buchhaus.common.ApiException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** Turns the catalogue's 4xx error responses back into ApiExceptions with the same message. */
public final class CatalogErrors {

    static final String UNAVAILABLE = "The catalogue is temporarily unavailable. Your cart is safe - please try again in a minute.";
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private CatalogErrors() {
    }

    public static ApiException translate(int status, String body) {
        HttpStatus http;
        try {
            http = HttpStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            http = HttpStatus.BAD_REQUEST;
        }
        return new ApiException(http, message(body));
    }

    static String message(String body) {
        if (body != null) {
            Matcher m = MESSAGE.matcher(body);
            if (m.find()) {
                return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
            }
        }
        return "The catalogue rejected the request";
    }

    /** What a caller sees when retries are used up or the circuit breaker is open. */
    public static ApiException unavailable(Throwable cause) {
        return cause instanceof ApiException api ? api : ApiException.unavailable(UNAVAILABLE);
    }
}
