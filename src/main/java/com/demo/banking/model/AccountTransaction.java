package com.demo.banking.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class AccountTransaction {

    private String transactionId;

    // Validated against ACCOUNT-NUMBER PIC 9(10) in ACCOUNT.CPY
    // If account number width changes, also update:
    //   AccountCobolAdapter.ACCOUNT_NUMBER_LEN
    //   openapi.yaml accountNumber maxLength
    //   banking-web-ui accountSchemas.ts
    @NotNull
    @Size(min = 10, max = 10, message = "Account number must be exactly 10 digits")
    @Pattern(regexp = "\\d{10}", message = "Account number must be numeric")
    private String accountNumber;
    private String transactionType;
    private BigDecimal amount;
    private String currency;
    private Instant timestamp;
    private String status;
}
