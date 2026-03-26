# banking-java-gateway

Java Spring Boot gateway that bridges legacy COBOL banking systems with modern Kafka-based event streaming. This service implements a **dual-write pattern** during migration: every transaction is written to both the legacy COBOL flat-file interface and published as a Kafka event.

## Architecture

```
┌──────────────┐     ┌─────────────────────┐     ┌──────────────────┐
│  REST API    │────▶│ banking-java-gateway │────▶│  Kafka Topics    │
│  Clients     │     │                     │     │  (modern path)   │
└──────────────┘     │  Dual-Write Pattern │     └──────────────────┘
                     │                     │
                     │  AccountController  │     ┌──────────────────┐
                     │  LoanController     │────▶│  COBOL Flat Files│
                     │                     │     │  (legacy path)   │
                     └─────────────────────┘     └──────────────────┘
                              │                           │
                              ▼                           ▼
                     ┌─────────────────┐        ┌──────────────────┐
                     │ AccountCobol    │        │ legacy-accounts- │
                     │ Adapter         │◀──────▶│ cobol (mainframe)│
                     │ LoanCobol      │        │ legacy-loans-    │
                     │ Adapter         │        │ cobol (mainframe)│
                     └─────────────────┘        └──────────────────┘
```

## COBOL Bridge

The `bridge/` package contains adapters that convert between Java domain objects and COBOL fixed-width flat-file records. Field width constants in each adapter **exactly mirror** the PIC clause definitions in the corresponding COBOL copybooks:

| Adapter                  | Source Copybook                                  |
|--------------------------|--------------------------------------------------|
| `AccountCobolAdapter`    | `legacy-accounts-cobol/copybooks/ACCOUNT.CPY`    |
|                          | `legacy-accounts-cobol/copybooks/TRANSACTION.CPY`|
| `LoanCobolAdapter`       | `legacy-loans-cobol/copybooks/LOAN.CPY`          |

### Field Mapping

The file `src/main/resources/cobol-field-mappings.properties` defines the mapping between COBOL field names (e.g., `ACCOUNT-NUMBER`) and Java field names (e.g., `accountNumber`).

> ⚠️ **Known Issue:** The mappings for `LOAN-TERM-MONTHS` and `OUTSTANDING-BALANCE` are currently **swapped**. This is tracked for a batch fix across all affected repositories.

## Dual-Write Pattern

During the migration from COBOL to Kafka-based processing, every operation follows two paths:

1. **Legacy Path:** The request is converted to a COBOL fixed-width record and appended to an input flat file (e.g., `ACCT-INPUT.dat`). The mainframe batch job picks this up.
2. **Modern Path:** The same request is published as a Kafka event to the appropriate topic (`account.transactions` or `loan.applications`).

Both paths run simultaneously. Once the Kafka consumers are fully validated, the COBOL path will be decommissioned.

## API Endpoints

### Accounts
- `POST /api/v1/accounts/{accountNumber}/transactions` — Submit a transaction (dual-write)
- `GET /api/v1/accounts/{accountNumber}/balance` — Query account balance

### Loans
- `POST /api/v1/loans/apply` — Submit a loan application (dual-write)
- `GET /api/v1/loans/{loanId}` — Query loan status

## Kafka Topics

| Topic                  | Producer              | Consumer              |
|------------------------|-----------------------|-----------------------|
| `account.transactions` | `AccountEventProducer`| `AccountEventConsumer`|
| `loan.applications`    | `LoanEventProducer`   | `LoanEventConsumer`   |
| `loan.calculations`    | (external)            | `LoanEventConsumer`   |

## Configuration

See `src/main/resources/application.yml`. Key environment variables:

| Variable                 | Default                  | Description                     |
|--------------------------|--------------------------|---------------------------------|
| `KAFKA_BOOTSTRAP_SERVERS`| `localhost:9092`         | Kafka broker addresses          |
| `SCHEMA_REGISTRY_URL`   | `http://localhost:8081`  | Confluent Schema Registry URL   |
| `COBOL_FILE_PATH`        | `/opt/cobol/data`        | Base path for COBOL flat files  |

## Sourcegraph Demo Scenarios

### 1. Cross-Repo Search: `ACCOUNT-NUMBER`
Search for `ACCOUNT-NUMBER` across all repositories to see how the same field appears in five locations across three repos:
- `legacy-accounts-cobol/copybooks/ACCOUNT.CPY` — PIC clause definition
- `legacy-accounts-cobol/copybooks/TRANSACTION.CPY` — PIC clause definition
- `banking-java-gateway/.../AccountCobolAdapter.java` — Java constant mirroring the PIC width
- `banking-java-gateway/.../cobol-field-mappings.properties` — field name mapping
- `banking-react-dashboard/src/utils/fieldMappings.ts` — frontend display mapping

### 2. Batch Changes: Fix Swapped Mappings
The `cobol-field-mappings.properties` file has an **intentional bug**: `LOAN-TERM-MONTHS` and `OUTSTANDING-BALANCE` are mapped to the wrong Java fields. Use Sourcegraph Batch Changes to fix this across all repositories that reference these fields.

### 3. Code Navigation
Follow the code navigation chain:
1. `AccountController.postTransaction()` calls `cobolAdapter.toCobolRecord()`
2. `AccountCobolAdapter.toCobolRecord()` uses `TRANSACTION_AMOUNT_LEN` constant
3. The constant's comment references `TRANSACTION.CPY` → `PIC S9(9)V99`
4. Navigate to `legacy-accounts-cobol/copybooks/TRANSACTION.CPY` to see the source definition

## Building

```bash
mvn clean package
```

## Running

```bash
mvn spring-boot:run
```

Or with Docker:
```bash
docker build -t banking-java-gateway .
docker run -p 8080:8080 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e SCHEMA_REGISTRY_URL=http://schema-registry:8081 \
  -e COBOL_FILE_PATH=/opt/cobol/data \
  banking-java-gateway
```
