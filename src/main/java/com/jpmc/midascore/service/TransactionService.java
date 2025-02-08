package com.jpmc.midascore.service;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.repository.TransactionRepository;
import com.jpmc.midascore.repository.UserRepository;
import com.jpmc.midascore.foundation.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import org.springframework.web.client.RestTemplate;
import com.jpmc.midascore.foundation.Incentive;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private static final String INCENTIVE_API_URL = "http://localhost:8080/incentive";

    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.restTemplate = new RestTemplate();
    }

    @Transactional
    public boolean processTransaction(Transaction transaction) {
        UserRecord sender = userRepository.findById(transaction.getSenderId()).orElse(null);
        UserRecord recipient = userRepository.findById(transaction.getRecipientId()).orElse(null);

        if (sender == null || recipient == null || sender.getBalance().compareTo(transaction.getAmount()) < 0) {
            return false;
        }

        // Get incentive from API
        Incentive incentive = restTemplate.postForObject(INCENTIVE_API_URL, transaction, Incentive.class);
        BigDecimal incentiveAmount = incentive != null ? incentive.getAmount() : BigDecimal.ZERO;

        // Update balances
        sender.setBalance(sender.getBalance().subtract(transaction.getAmount()));
        recipient.setBalance(recipient.getBalance().add(transaction.getAmount()).add(incentiveAmount));

        // Save updated users
        userRepository.save(sender);
        userRepository.save(recipient);

        // Create and save transaction record
        TransactionRecord record = new TransactionRecord();
        record.setSender(sender);
        record.setRecipient(recipient);
        record.setAmount(transaction.getAmount());
        record.setIncentiveAmount(incentiveAmount);
        record.setTimestamp(LocalDateTime.now());
        transactionRepository.save(record);

        return true;
    }
} 