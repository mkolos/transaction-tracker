package com.kolosh.transactiontracker.repository;

import java.util.UUID;

import com.kolosh.transactiontracker.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
}
