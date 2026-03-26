package com.demo.banking.bridge;

import com.demo.banking.model.LoanApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Adapter between Java domain objects and COBOL flat-file loan records.
 *
 * Field width constants below mirror the PIC clause definitions in:
 *   legacy-loans-cobol/copybooks/LOAN.CPY
 *
 * If the copybook changes, these constants MUST be updated to match.
 * Use Sourcegraph cross-repo search to find all affected files:
 *   search: LOAN-ID or LOAN-ACCOUNT-NUMBER
 */
@Service
public class LoanCobolAdapter {

    private static final Logger log = LoggerFactory.getLogger(LoanCobolAdapter.class);

    private final CobolBridgeService bridgeService;

    // Field widths — mirror LOAN.CPY
    static final int LOAN_ID_LEN              = 12;  // LOAN-ID             PIC 9(12)
    static final int LOAN_ACCOUNT_NUMBER_LEN  = 10;  // LOAN-ACCOUNT-NUMBER PIC 9(10)
    static final int LOAN_TYPE_LEN            = 4;   // LOAN-TYPE           PIC X(4)
    static final int LOAN_TERM_MONTHS_LEN     = 4;   // LOAN-TERM-MONTHS    PIC 9(4)
    static final int ORIGINATION_DATE_LEN     = 8;   // ORIGINATION-DATE    PIC 9(8)

    // Additional field widths for loan amounts
    static final int PRINCIPAL_AMOUNT_LEN     = 13;  // PRINCIPAL-AMOUNT    PIC S9(9)V99
    static final int INTEREST_RATE_LEN        = 5;   // INTEREST-RATE       PIC 9(2)V9(3)
    static final int MONTHLY_PAYMENT_LEN      = 13;  // MONTHLY-PAYMENT     PIC S9(9)V99

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public LoanCobolAdapter(CobolBridgeService bridgeService) {
        this.bridgeService = bridgeService;
    }

    /**
     * Converts a LoanApplication into a COBOL-compatible fixed-width record.
     */
    public CobolLoanRecord toCobolRecord(LoanApplication application) {
        String loanId   = leftPad(application.getLoanId(), LOAN_ID_LEN, '0');
        String acctNum  = leftPad(application.getAccountNumber(), LOAN_ACCOUNT_NUMBER_LEN, '0');
        String loanType = rightPad(application.getLoanType(), LOAN_TYPE_LEN, ' ');
        String term     = leftPad(String.valueOf(application.getTermMonths()), LOAN_TERM_MONTHS_LEN, '0');
        String origDate = leftPad(LocalDate.now().format(DATE_FMT), ORIGINATION_DATE_LEN, '0');
        String principal = formatAmount(application.getPrincipalAmount(), PRINCIPAL_AMOUNT_LEN);
        String rate     = formatRate(application.getInterestRate(), INTEREST_RATE_LEN);

        String record = loanId + acctNum + loanType + term + origDate + principal + rate;
        return new CobolLoanRecord(record, loanId, acctNum);
    }

    /**
     * Writes a COBOL loan record to the legacy input flat file.
     */
    public void writeLoanApplication(CobolLoanRecord record) {
        try {
            bridgeService.appendToInputFile("LOAN-INPUT.dat", record.rawRecord());
            log.info("Wrote COBOL loan record for loanId={} account={}",
                record.loanId(), record.accountNumber());
        } catch (IOException e) {
            log.error("Failed to write COBOL loan record for loanId={}", record.loanId(), e);
            throw new AccountCobolAdapter.CobolBridgeException(
                "Failed to write loan to COBOL input file", e);
        }
    }

    /**
     * Reads the COBOL output file and retrieves loan status for a given loan ID.
     */
    public CobolLoanStatus queryLoanStatus(String loanId) {
        try {
            List<String> lines = bridgeService.readOutputFile("LOAN-OUTPUT.dat");
            String paddedLoanId = leftPad(loanId, LOAN_ID_LEN, '0');

            for (String line : lines) {
                if (line.length() >= LOAN_ID_LEN && line.startsWith(paddedLoanId)) {
                    return parseLoanStatus(line, loanId);
                }
            }

            log.warn("No loan record found for loanId={}", loanId);
            return new CobolLoanStatus(loanId, "NOT_FOUND", BigDecimal.ZERO, BigDecimal.ZERO, 0);
        } catch (IOException e) {
            log.error("Failed to read COBOL output file for loanId={}", loanId, e);
            throw new AccountCobolAdapter.CobolBridgeException(
                "Failed to read loan status from COBOL output file", e);
        }
    }

    private CobolLoanStatus parseLoanStatus(String line, String loanId) {
        int offset = LOAN_ID_LEN + LOAN_ACCOUNT_NUMBER_LEN + LOAN_TYPE_LEN;
        String status = "ACTIVE";
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal rate = BigDecimal.ZERO;
        int termMonths = 0;

        try {
            termMonths = Integer.parseInt(line.substring(offset, offset + LOAN_TERM_MONTHS_LEN).trim());
            offset += LOAN_TERM_MONTHS_LEN + ORIGINATION_DATE_LEN;
            long principalCents = Long.parseLong(line.substring(offset, offset + PRINCIPAL_AMOUNT_LEN).trim());
            principal = BigDecimal.valueOf(principalCents, 2);
        } catch (Exception e) {
            log.warn("Could not fully parse loan record for loanId={}", loanId);
        }

        return new CobolLoanStatus(loanId, status, principal, rate, termMonths);
    }

    private String formatAmount(BigDecimal amount, int length) {
        long cents = amount.movePointRight(2).longValue();
        String sign = cents >= 0 ? "+" : "-";
        String digits = String.valueOf(Math.abs(cents));
        return sign + leftPad(digits, length - 1, '0');
    }

    private String formatRate(BigDecimal rate, int length) {
        long rateValue = rate.movePointRight(3).longValue();
        return leftPad(String.valueOf(rateValue), length, '0');
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

    public record CobolLoanRecord(
        String rawRecord,
        String loanId,
        String accountNumber
    ) {}

    public record CobolLoanStatus(
        String loanId,
        String status,
        BigDecimal principalAmount,
        BigDecimal interestRate,
        int termMonths
    ) {}
}
