package com.demo.banking.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanApplication {

    private String loanId;

    // Validated against LOAN-ACCOUNT-NUMBER PIC 9(10) in LOAN.CPY
    // If account number width changes, also update:
    //   LoanCobolAdapter.ACCOUNT_NUMBER_LEN
    //   openapi.yaml accountNumber maxLength
    @NotNull
    @Size(min = 10, max = 10, message = "Account number must be exactly 10 digits")
    @Pattern(regexp = "\\d{10}", message = "Account number must be numeric")
    private String accountNumber;
    private String loanType;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private int termMonths;
    private String status;
}
