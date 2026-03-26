package com.demo.banking.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanApplication {

    private String loanId;
    private String accountNumber;
    private String loanType;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private int termMonths;
    private String status;
}
