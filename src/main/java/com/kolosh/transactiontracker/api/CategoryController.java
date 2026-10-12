package com.kolosh.transactiontracker.api;

import java.net.URI;
import java.util.List;

import com.kolosh.transactiontracker.api.Dtos.CategoryResponse;
import com.kolosh.transactiontracker.api.Dtos.CreateCategoryRequest;
import com.kolosh.transactiontracker.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    List<CategoryResponse> list() {
        return service.list().stream().map(CategoryResponse::of).toList();
    }

    @PostMapping
    ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest body) {
        var created = CategoryResponse.of(service.create(body.name()));
        return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id())).body(created);
    }
}
