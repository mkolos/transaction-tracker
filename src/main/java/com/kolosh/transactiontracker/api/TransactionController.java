package com.kolosh.transactiontracker.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.kolosh.transactiontracker.api.Dtos.AssignCategoryRequest;
import com.kolosh.transactiontracker.api.Dtos.PageResponse;
import com.kolosh.transactiontracker.api.Dtos.TransactionDetail;
import com.kolosh.transactiontracker.api.Dtos.TransactionSummary;
import com.kolosh.transactiontracker.service.TransactionFilter;
import com.kolosh.transactiontracker.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<TransactionSummary> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "category", required = false) Long categoryId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "false") boolean uncategorized,
            Pageable pageable) {
        var filter = new TransactionFilter(from, to, categoryId, currency, q, minAmount, maxAmount, uncategorized);
        return PageResponse.of(service.search(filter, pageable).map(TransactionSummary::of));
    }

    @GetMapping("/{id}")
    TransactionDetail get(@PathVariable UUID id) {
        return TransactionDetail.of(service.get(id));
    }

    @PutMapping("/{id}/category")
    TransactionDetail assignCategory(@PathVariable UUID id, @Valid @RequestBody AssignCategoryRequest body) {
        return TransactionDetail.of(service.assignCategory(id, body.categoryId()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
