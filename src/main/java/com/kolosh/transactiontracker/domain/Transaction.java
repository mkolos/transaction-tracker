package com.kolosh.transactiontracker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "booked_on", nullable = false)
    private LocalDate bookedOn;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String description;

    // LAZY: don't load the category row unless something asks for it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", nullable = false, length = 10)
    private CategorySource categorySource = CategorySource.NONE;

    @Column(name = "matched_rule_id")
    private Long matchedRuleId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
        // for JPA
    }

    public Transaction(LocalDate bookedOn, BigDecimal amount, String currency, String description) {
        this.bookedOn = bookedOn;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
    }

    /** Manual override: the user's choice always wins over rules. */
    public void assignCategoryManually(Category category) {
        this.category = category;
        this.categorySource = CategorySource.MANUAL;
        this.matchedRuleId = null;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getBookedOn() {
        return bookedOn;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public CategorySource getCategorySource() {
        return categorySource;
    }

    public Long getMatchedRuleId() {
        return matchedRuleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
