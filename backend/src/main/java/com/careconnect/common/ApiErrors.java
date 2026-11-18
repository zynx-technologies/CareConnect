package com.careconnect.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Maps domain failures to RFC 9457 responses. Messages must never contain patient data. */
@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {

    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    public static class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
    }

    public static class AccessDenied extends RuntimeException {
        public AccessDenied(String message) { super(message); }
    }

    public static class Unavailable extends RuntimeException {
        public Unavailable(String message) { super(message); }
    }

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }

    @ExceptionHandler(NotFound.class)
    ProblemDetail notFound(NotFound e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()); }

    @ExceptionHandler(Conflict.class)
    ProblemDetail conflict(Conflict e) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()); }

    @ExceptionHandler(AccessDenied.class)
    ProblemDetail denied(AccessDenied e) { return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage()); }

    @ExceptionHandler(BadRequest.class)
    ProblemDetail badRequest(BadRequest e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()); }

    @ExceptionHandler(Unavailable.class)
    ProblemDetail unavailable(Unavailable e) { return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage()); }
}
