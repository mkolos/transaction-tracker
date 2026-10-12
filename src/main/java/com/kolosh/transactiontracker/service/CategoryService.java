package com.kolosh.transactiontracker.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.kolosh.transactiontracker.domain.Category;
import com.kolosh.transactiontracker.repository.CategoryRepository;
import com.kolosh.transactiontracker.service.Exceptions.DuplicateCategoryException;

@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    public List<Category> list() {
        return categories.findAllByOrderByNameAsc();
    }

    public Category create(String rawName) {
        String name = rawName.strip();
        
        if (categories.existsByNameIgnoreCase(name)) {
            throw new DuplicateCategoryException(name);
        }
        
        try {
            return categories.saveAndFlush(new Category(name));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCategoryException(name);
        }
    }
}
