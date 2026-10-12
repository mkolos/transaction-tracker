package com.kolosh.transactiontracker.api;

import java.net.URI;

import com.kolosh.transactiontracker.service.Exceptions.DuplicateCategoryException;
import com.kolosh.transactiontracker.service.Exceptions.InvalidRequestException;
import com.kolosh.transactiontracker.service.Exceptions.NotFoundException;
import com.kolosh.transactiontracker.service.Exceptions.UnknownCategoryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the API as RFC 7807 {@code application/problem+json}. The base class already
 * converts Spring's own failures (malformed JSON, bad UUID/number/date in the URL, @Valid failures,
 * unsupported method...) to ProblemDetail; we only add our domain exceptions.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found", e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalid(InvalidRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", e.getMessage());
    }

    @ExceptionHandler(UnknownCategoryException.class)
    ProblemDetail unknownCategory(UnknownCategoryException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "unknown-category", "Unknown category", e.getMessage());
    }

    @ExceptionHandler(DuplicateCategoryException.class)
    ProblemDetail duplicateCategory(DuplicateCategoryException e) {
        return problem(HttpStatus.CONFLICT, "duplicate-category", "Category already exists", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String slug, String title, String detail) {
        var pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("/errors/" + slug));
        pd.setTitle(title);
        return pd;
    }
}
