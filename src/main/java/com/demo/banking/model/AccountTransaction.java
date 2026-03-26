package com.demo.banking.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class AccountTransaction {

    private String transactionId;
    private String accountNumber;
    private String transactionType;
    private BigDecimal amount;
    private String currency;
    private Instant timestamp;
    private String status;
}
