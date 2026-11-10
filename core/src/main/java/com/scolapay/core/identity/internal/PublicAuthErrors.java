package com.scolapay.core.identity.internal;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Les exceptions JSON peuvent recopier un mot de passe ou un jeton : elles ne sont jamais journalisées. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = IdentityController.class)
class PublicAuthErrors {
    @ExceptionHandler(AuthAbuseRejection.class)
    void rejected(AuthAbuseRejection rejection, HttpServletResponse response) throws IOException { rejection.write(response); }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    void unreadable(HttpMessageNotReadableException failure, HttpServletResponse response) throws IOException {
        Throwable cause = failure;
        for (int depth=0; depth<8 && cause != null; depth++, cause=cause.getCause()) {
            if (cause instanceof PublicAuthFilter.BodyTooLarge) { AuthAbuseRejection.tooLarge().write(response); return; }
        }
        AuthAbuseRejection.invalid().write(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    void invalid(HttpServletResponse response) throws IOException { AuthAbuseRejection.invalid().write(response); }
}
