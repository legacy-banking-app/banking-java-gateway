package com.demo.banking.kafka;

import com.demo.banking.api.AccountController.AccountTransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountEventConsumer.class);

    private final ConcurrentHashMap<String, BigDecimal> accountBalances = new ConcurrentHashMap<>();

    @KafkaListener(topics = "account.transactions", groupId = "banking-gateway")
    public void consume(AccountTransactionEvent event) {
        log.info("Received account transaction event: txnId={} account={} status={}",
            event.getTransactionId(), event.getAccountNumber(), event.status());

        switch (event.status()) {
            case "COMPLETED" -> {
                log.info("Transaction COMPLETED: txnId={} account={} amount={}",
                    event.getTransactionId(), event.getAccountNumber(), event.amount());
                accountBalances.merge(
                    event.getAccountNumber(),
                    event.amount(),
                    BigDecimal::add
                );
            }
            case "REJECTED" -> log.warn("Transaction REJECTED: txnId={} account={} reason=pending-review",
                event.getTransactionId(), event.getAccountNumber());
            default -> log.debug("Transaction status={} for txnId={}",
                event.status(), event.getTransactionId());
        }
    }

    public BigDecimal getBalance(String accountNumber) {
        return accountBalances.getOrDefault(accountNumber, BigDecimal.ZERO);
    }
}
