# Local development

## Prerequisites
- Docker and Docker Compose
- Java 17
- Maven 3.9+

## Running locally

1. Start infrastructure:
   ```bash
   docker-compose up zookeeper kafka schema-registry
   ```

2. Run the application:
   ```bash
   mvn spring-boot:run
   ```

3. Or run everything together:
   ```bash
   docker-compose up
   ```

## Environment variables
Copy `.env.example` to `.env` and adjust as needed.
The application reads these via Spring's `@Value` and `application.yml`.
