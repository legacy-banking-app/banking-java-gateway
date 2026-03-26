package com.demo.banking.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BalanceInquiry {

    private String accountNumber;
    private BigDecimal balance;
    private String currency;
    private String asOfDate;
}
