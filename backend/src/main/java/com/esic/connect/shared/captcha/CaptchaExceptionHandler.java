package com.esic.connect.shared.captcha;

import com.esic.connect.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Traduit un refus anti-robot en {@code 400} portant un code stable.
 *
 * <p>Le motif exact renvoyé par le fournisseur n'est jamais exposé : il
 * indiquerait à un robot ce qu'il doit corriger. Il reste disponible en
 * supervision, côté serveur.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CaptchaExceptionHandler {

    @ExceptionHandler(CaptchaRejectedException.class)
    public ResponseEntity<ApiError> handle(CaptchaRejectedException exception,
                                           HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(
                Instant.now(), HttpStatus.BAD_REQUEST.value(), "CAPTCHA_REJECTED",
                exception.getMessage(), request.getRequestURI(),
                UUID.randomUUID().toString(), List.of()));
    }
}
