package com.demo.banking.api;

import com.demo.banking.bridge.AccountCobolAdapter;
import com.demo.banking.bridge.AccountCobolAdapter.CobolBalanceResult;
import com.demo.banking.bridge.AccountCobolAdapter.CobolTransactionRecord;
import com.demo.banking.bridge.AccountCobolAdapter.TransactionRequest;
import com.demo.banking.kafka.AccountEventProducer;
import com.demo.banking.model.AccountTransaction;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountCobolAdapter cobolAdapter;
    private final AccountEventProducer eventProducer;

    public AccountController(AccountCobolAdapter cobolAdapter, AccountEventProducer eventProducer) {
        this.cobolAdapter = cobolAdapter;
        this.eventProducer = eventProducer;
    }

    @PostMapping("/{accountNumber}/transactions")
    public ResponseEntity<TransactionResponse> postTransaction(
            @PathVariable String accountNumber,
            @Valid @RequestBody TransactionRequest request) {
        // 1. Write to COBOL flat file (legacy path)
        CobolTransactionRecord cobolRecord = cobolAdapter.toCobolRecord(accountNumber, request);
        cobolAdapter.writeTransaction(cobolRecord);

        // 2. Publish Kafka event (modern path — both run during migration)
        AccountTransactionEvent event = buildEvent(accountNumber, request);
        eventProducer.publish(event);

        return ResponseEntity.accepted().body(new TransactionResponse(event.getTransactionId().toString()));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountNumber) {
        CobolBalanceResult result = cobolAdapter.queryBalance(accountNumber);
        return ResponseEntity.ok(new BalanceResponse(accountNumber, result.getBalance(), "USD"));
    }

    private AccountTransactionEvent buildEvent(String accountNumber, TransactionRequest request) {
        return new AccountTransactionEvent(
            UUID.randomUUID(),
            accountNumber,
            request.transactionType(),
            request.amount(),
            request.currency(),
            Instant.now(),
            "PENDING"
        );
    }

    // --- Inner types ---

    public record TransactionResponse(String transactionId) {}

    public record BalanceResponse(String accountNumber, BigDecimal balance, String currency) {}

    public record AccountTransactionEvent(
        UUID transactionId,
        String accountNumber,
        String transactionType,
        BigDecimal amount,
        String currency,
        Instant timestamp,
        String status
    ) {
        public UUID getTransactionId() { return transactionId; }
        public String getAccountNumber() { return accountNumber; }
    }
}
