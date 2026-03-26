package com.demo.banking.api;

import com.demo.banking.bridge.LoanCobolAdapter;
import com.demo.banking.bridge.LoanCobolAdapter.CobolLoanRecord;
import com.demo.banking.bridge.LoanCobolAdapter.CobolLoanStatus;
import com.demo.banking.kafka.LoanEventProducer;
import com.demo.banking.model.LoanApplication;
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
@RequestMapping("/api/v1/loans")
@Validated
public class LoanController {

    private static final Logger log = LoggerFactory.getLogger(LoanController.class);

    private final LoanCobolAdapter cobolAdapter;
    private final LoanEventProducer eventProducer;

    public LoanController(LoanCobolAdapter cobolAdapter, LoanEventProducer eventProducer) {
        this.cobolAdapter = cobolAdapter;
        this.eventProducer = eventProducer;
    }

    @PostMapping("/apply")
    public ResponseEntity<LoanApplicationResponse> applyForLoan(
            @Valid @RequestBody LoanApplicationRequest request) {
        // Build domain model
        LoanApplication application = new LoanApplication();
        application.setLoanId(UUID.randomUUID().toString());
        application.setAccountNumber(request.accountNumber());
        application.setLoanType(request.loanType());
        application.setPrincipalAmount(request.principalAmount());
        application.setInterestRate(request.interestRate());
        application.setTermMonths(request.termMonths());
        application.setStatus("PENDING");

        // 1. Write to COBOL flat file (legacy path)
        CobolLoanRecord cobolRecord = cobolAdapter.toCobolRecord(application);
        cobolAdapter.writeLoanApplication(cobolRecord);

        // 2. Publish Kafka event (modern path — both run during migration)
        LoanApplicationEvent event = buildEvent(application);
        eventProducer.publish(event);

        return ResponseEntity.accepted().body(
            new LoanApplicationResponse(application.getLoanId(), "PENDING"));
    }

    @GetMapping("/{loanId}")
    public ResponseEntity<LoanStatusResponse> getLoanStatus(@PathVariable String loanId) {
        CobolLoanStatus status = cobolAdapter.queryLoanStatus(loanId);
        return ResponseEntity.ok(new LoanStatusResponse(
            status.loanId(),
            status.status(),
            status.principalAmount(),
            status.interestRate(),
            status.termMonths()
        ));
    }

    private LoanApplicationEvent buildEvent(LoanApplication application) {
        return new LoanApplicationEvent(
            application.getLoanId(),
            application.getAccountNumber(),
            application.getLoanType(),
            application.getPrincipalAmount(),
            application.getInterestRate(),
            application.getTermMonths(),
            Instant.now(),
            application.getStatus()
        );
    }

    // --- Inner types ---

    public record LoanApplicationRequest(
        String accountNumber,
        String loanType,
        BigDecimal principalAmount,
        BigDecimal interestRate,
        int termMonths
    ) {}

    public record LoanApplicationResponse(String loanId, String status) {}

    public record LoanStatusResponse(
        String loanId,
        String status,
        BigDecimal principalAmount,
        BigDecimal interestRate,
        int termMonths
    ) {}

    public record LoanApplicationEvent(
        String loanId,
        String accountNumber,
        String loanType,
        BigDecimal principalAmount,
        BigDecimal interestRate,
        int termMonths,
        Instant timestamp,
        String status
    ) {
        public String getLoanId() { return loanId; }
        public String getAccountNumber() { return accountNumber; }
    }
}
