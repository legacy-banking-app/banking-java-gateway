package com.demo.banking.kafka;

import com.demo.banking.api.LoanController.LoanApplicationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class LoanEventProducer {

    private static final Logger log = LoggerFactory.getLogger(LoanEventProducer.class);
    private static final String TOPIC = "loan.applications";

    private final KafkaTemplate<String, LoanApplicationEvent> kafkaTemplate;

    public LoanEventProducer(KafkaTemplate<String, LoanApplicationEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(LoanApplicationEvent event) {
        CompletableFuture<SendResult<String, LoanApplicationEvent>> future =
            kafkaTemplate.send(TOPIC, event.getAccountNumber(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Published loan application event: loanId={} account={} topic={} offset={}",
                    event.getLoanId(), event.getAccountNumber(),
                    TOPIC, result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish loan application event: loanId={} account={}",
                    event.getLoanId(), event.getAccountNumber(), ex);
            }
        });
    }
}
