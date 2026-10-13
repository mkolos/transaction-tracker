package com.kolosh.transactiontracker.service;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kolosh.transactiontracker.domain.Transaction;
import com.kolosh.transactiontracker.repository.CategoryRepository;
import com.kolosh.transactiontracker.repository.TransactionRepository;
import com.kolosh.transactiontracker.service.Exceptions.InvalidRequestException;
import com.kolosh.transactiontracker.service.Exceptions.NotFoundException;
import com.kolosh.transactiontracker.service.Exceptions.UnknownCategoryException;

@Service
public class TransactionService {

    private static final Set<String> SORTABLE = new TreeSet<>(Set.of("bookedOn", "amount", "description", "createdAt"));
    private static final Pattern CURRENCY = Pattern.compile("^[A-Za-z]{3}$");

    private final TransactionRepository transactions;
    private final CategoryRepository categories;

    public TransactionService(TransactionRepository transactions, CategoryRepository categories) {
        this.transactions = transactions;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public Page<Transaction> search(TransactionFilter filter, Pageable pageable) {
        return transactions.findAll(TransactionSpecifications.from(validate(filter)), withStableSort(pageable));
    }

    @Transactional(readOnly = true)
    public Transaction get(UUID id) {
        return find(id);
    }

    @Transactional
    public Transaction assignCategory(UUID id, long categoryId) {
        Transaction tx = find(id);
        var category = categories.findById(categoryId).orElseThrow(() -> new UnknownCategoryException(categoryId));
        tx.assignCategoryManually(category);
        return tx;
    }

    @Transactional
    public void delete(UUID id) {
        transactions.delete(find(id));
    }

    private Transaction find(UUID id) {
        return transactions.findWithCategoryById(id)
                .orElseThrow(() -> new NotFoundException("Transaction " + id + " not found"));
    }

    private TransactionFilter validate(TransactionFilter f) {
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            throw new InvalidRequestException("'from' must not be after 'to'");
        }
        if (f.minAmount() != null && f.maxAmount() != null && f.minAmount().compareTo(f.maxAmount()) > 0) {
            throw new InvalidRequestException("'minAmount' must not be greater than 'maxAmount'");
        }
        if (f.uncategorized() && f.categoryId() != null) {
            throw new InvalidRequestException("'uncategorized' and 'category' cannot be combined");
        }
        String currency = f.currency();
        if (currency != null) {
            if (!CURRENCY.matcher(currency).matches()) {
                throw new InvalidRequestException("'currency' must be a 3-letter ISO code");
            }
            currency = currency.toUpperCase(Locale.ROOT);
        }
        String q = f.q() == null || f.q().isBlank() ? null : f.q().strip();
        return new TransactionFilter(f.from(), f.to(), f.categoryId(), currency, q,
                f.minAmount(), f.maxAmount(), f.uncategorized());
    }

    private Pageable withStableSort(Pageable pageable) {
        Sort sort = pageable.getSort().isUnsorted() ? Sort.by(Sort.Order.desc("bookedOn")) : pageable.getSort();
        for (Sort.Order order : sort) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidRequestException(
                        "Cannot sort by '" + order.getProperty() + "'. Allowed: " + SORTABLE);
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by("id")));
    }
}
