package com.demo.banking.bridge;

import com.demo.banking.model.AccountTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Adapter between Java domain objects and COBOL flat-file records.
 *
 * Field width constants below mirror the PIC clause definitions in:
 *   legacy-accounts-cobol/copybooks/ACCOUNT.CPY
 *   legacy-accounts-cobol/copybooks/TRANSACTION.CPY
 *
 * If either copybook changes, these constants MUST be updated to match.
 * Use Sourcegraph cross-repo search to find all affected files:
 *   search: ACCOUNT-NUMBER or TRANSACTION-AMOUNT
 */
@Service
public class AccountCobolAdapter {

    private static final Logger log = LoggerFactory.getLogger(AccountCobolAdapter.class);

    private final CobolBridgeService bridgeService;

    // Field widths — mirror ACCOUNT.CPY
    static final int ACCOUNT_NUMBER_LEN      = 10;  // ACCOUNT-NUMBER      PIC 9(10)
    static final int ACCOUNT_HOLDER_NAME_LEN = 40;  // ACCOUNT-HOLDER-NAME PIC X(40)
    static final int ACCOUNT_OPEN_DATE_LEN   = 8;   // ACCOUNT-OPEN-DATE   PIC 9(8)

    // Field widths — mirror TRANSACTION.CPY
    static final int TRANSACTION_ID_LEN      = 12;  // TRANSACTION-ID      PIC 9(12)
    static final int TRANSACTION_TYPE_LEN    = 3;   // TRANSACTION-TYPE    PIC X(3)
    static final int TRANSACTION_AMOUNT_LEN  = 13;  // TRANSACTION-AMOUNT  PIC S9(9)V99 (sign + 9 + decimal + 2)
    static final int TRANSACTION_DATE_LEN    = 8;   // TRANSACTION-DATE    PIC 9(8)
    static final int TRANSACTION_TIME_LEN    = 6;   // TRANSACTION-TIME    PIC 9(6)

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");

    public AccountCobolAdapter(CobolBridgeService bridgeService) {
        this.bridgeService = bridgeService;
    }

    /**
     * Converts a Java TransactionRequest into a COBOL-compatible fixed-width record.
     * Numeric fields are left-padded with zeros; alphanumeric fields are right-padded with spaces.
     */
    public CobolTransactionRecord toCobolRecord(String accountNumber, TransactionRequest request) {
        String acctNum   = leftPad(accountNumber, ACCOUNT_NUMBER_LEN, '0');
        String txnId     = leftPad(request.transactionId(), TRANSACTION_ID_LEN, '0');
        String txnType   = rightPad(request.transactionType(), TRANSACTION_TYPE_LEN, ' ');
        String txnAmount = formatAmount(request.amount(), TRANSACTION_AMOUNT_LEN);
        String txnDate   = leftPad(LocalDate.now().format(DATE_FMT), TRANSACTION_DATE_LEN, '0');
        String txnTime   = leftPad(LocalTime.now().format(TIME_FMT), TRANSACTION_TIME_LEN, '0');

        String record = acctNum + txnId + txnType + txnAmount + txnDate + txnTime;
        return new CobolTransactionRecord(record, acctNum, txnId);
    }

    /**
     * Writes a COBOL transaction record to the legacy input flat file.
     */
    public void writeTransaction(CobolTransactionRecord record) {
        try {
            bridgeService.appendToInputFile("ACCT-INPUT.dat", record.rawRecord());
            log.info("Wrote COBOL transaction record for account={} txnId={}",
                record.accountNumber(), record.transactionId());
        } catch (IOException e) {
            log.error("Failed to write COBOL transaction record for account={}",
                record.accountNumber(), e);
            throw new CobolBridgeException("Failed to write transaction to COBOL input file", e);
        }
    }

    /**
     * Reads the COBOL output file and extracts the balance for a given account.
     * The output file contains fixed-width records; we filter by the ACCOUNT-NUMBER
     * prefix (first 10 chars) and parse the balance from the subsequent field.
     */
    public CobolBalanceResult queryBalance(String accountNumber) {
        try {
            List<String> lines = bridgeService.readOutputFile("ACCT-OUTPUT.dat");
            String paddedAcct = leftPad(accountNumber, ACCOUNT_NUMBER_LEN, '0');

            for (String line : lines) {
                if (line.length() >= ACCOUNT_NUMBER_LEN && line.startsWith(paddedAcct)) {
                    BigDecimal balance = parseBalance(line);
                    return new CobolBalanceResult(accountNumber, balance);
                }
            }

            log.warn("No balance record found for account={}", accountNumber);
            return new CobolBalanceResult(accountNumber, BigDecimal.ZERO);
        } catch (IOException e) {
            log.error("Failed to read COBOL output file for account={}", accountNumber, e);
            throw new CobolBridgeException("Failed to read balance from COBOL output file", e);
        }
    }

    private BigDecimal parseBalance(String line) {
        // Balance field starts after ACCOUNT-NUMBER (10) + ACCOUNT-HOLDER-NAME (40)
        int balanceStart = ACCOUNT_NUMBER_LEN + ACCOUNT_HOLDER_NAME_LEN;
        int balanceLen = 13; // PIC S9(9)V99
        if (line.length() < balanceStart + balanceLen) {
            return BigDecimal.ZERO;
        }
        String raw = line.substring(balanceStart, balanceStart + balanceLen).trim();
        try {
            long cents = Long.parseLong(raw);
            return BigDecimal.valueOf(cents, 2);
        } catch (NumberFormatException e) {
            log.warn("Could not parse balance from COBOL record: '{}'", raw);
            return BigDecimal.ZERO;
        }
    }

    private String formatAmount(BigDecimal amount, int length) {
        long cents = amount.movePointRight(2).longValue();
        String sign = cents >= 0 ? "+" : "-";
        String digits = String.valueOf(Math.abs(cents));
        return sign + leftPad(digits, length - 1, '0');
    }

    private static String leftPad(String value, int length, char padChar) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return String.valueOf(padChar).repeat(length - value.length()) + value;
    }

    private static String rightPad(String value, int length, char padChar) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return value + String.valueOf(padChar).repeat(length - value.length());
    }

    // --- Inner types ---

    public record TransactionRequest(
        String transactionId,
        String transactionType,
        BigDecimal amount,
        String currency
    ) {}

    public record CobolTransactionRecord(
        String rawRecord,
        String accountNumber,
        String transactionId
    ) {}

    public record CobolBalanceResult(
        String accountNumber,
        BigDecimal balance
    ) {
        public BigDecimal getBalance() { return balance; }
    }

    public static class CobolBridgeException extends RuntimeException {
        public CobolBridgeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
