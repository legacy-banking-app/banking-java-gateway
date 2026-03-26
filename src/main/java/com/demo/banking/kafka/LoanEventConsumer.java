package com.demo.banking.kafka;

import com.demo.banking.api.LoanController.LoanApplicationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoanEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LoanEventConsumer.class);

    private final ConcurrentHashMap<String, String> loanStatuses = new ConcurrentHashMap<>();

    @KafkaListener(topics = {"loan.applications", "loan.calculations"}, groupId = "banking-gateway")
    public void consume(LoanApplicationEvent event) {
        log.info("Received loan event: loanId={} account={} status={}",
            event.getLoanId(), event.getAccountNumber(), event.status());

        switch (event.status()) {
            case "APPROVED" -> {
                log.info("Loan APPROVED: loanId={} account={} principal={} rate={} term={}",
                    event.getLoanId(), event.getAccountNumber(),
                    event.principalAmount(), event.interestRate(), event.termMonths());
                loanStatuses.put(event.getLoanId(), "APPROVED");
            }
            case "REJECTED" -> {
                log.warn("Loan REJECTED: loanId={} account={}",
                    event.getLoanId(), event.getAccountNumber());
                loanStatuses.put(event.getLoanId(), "REJECTED");
            }
            case "PENDING" -> {
                log.info("Loan PENDING review: loanId={}", event.getLoanId());
                loanStatuses.put(event.getLoanId(), "PENDING");
            }
            default -> log.debug("Loan status={} for loanId={}",
                event.status(), event.getLoanId());
        }
    }

    public String getLoanStatus(String loanId) {
        return loanStatuses.getOrDefault(loanId, "UNKNOWN");
    }
}
