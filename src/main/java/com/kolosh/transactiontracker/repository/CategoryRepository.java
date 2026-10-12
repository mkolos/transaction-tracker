package com.kolosh.transactiontracker.repository;

import java.util.List;

import com.kolosh.transactiontracker.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
