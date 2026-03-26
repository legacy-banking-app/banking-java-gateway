package com.demo.banking.kafka;

import com.demo.banking.api.AccountController.AccountTransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AccountEventProducer {

    private static final Logger log = LoggerFactory.getLogger(AccountEventProducer.class);
    private static final String TOPIC = "account.transactions";

    private final KafkaTemplate<String, AccountTransactionEvent> kafkaTemplate;

    public AccountEventProducer(KafkaTemplate<String, AccountTransactionEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(AccountTransactionEvent event) {
        CompletableFuture<SendResult<String, AccountTransactionEvent>> future =
            kafkaTemplate.send(TOPIC, event.getAccountNumber(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Published account transaction event: txnId={} account={} topic={} offset={}",
                    event.getTransactionId(), event.getAccountNumber(),
                    TOPIC, result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish account transaction event: txnId={} account={}",
                    event.getTransactionId(), event.getAccountNumber(), ex);
            }
        });
    }
}
