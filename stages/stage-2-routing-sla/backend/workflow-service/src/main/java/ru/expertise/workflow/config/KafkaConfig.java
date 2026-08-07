package ru.expertise.workflow.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic processEventsTopic(WorkflowProperties properties) {
        return topic(properties.getKafka().getTopicProcessEvents());
    }

    @Bean
    public NewTopic taskEventsTopic(WorkflowProperties properties) {
        return topic(properties.getKafka().getTopicTaskEvents());
    }

    @Bean
    public NewTopic slaEventsTopic(WorkflowProperties properties) {
        return topic(properties.getKafka().getTopicSlaEvents());
    }

    @Bean
    public NewTopic dlqTopic(WorkflowProperties properties) {
        return topic(properties.getKafka().getTopicDlq());
    }

    @Bean
    public NewTopic requestAcceptedTopic(WorkflowProperties properties) {
        return topic(properties.getKafka().getTopicRequestAccepted());
    }

    private NewTopic topic(String name) {
        return new NewTopic(name, 3, (short) 1);
    }

    /** Publishes records that exhaust retries to the DLQ topic, preserving the source partition. */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<Object, Object> kafkaTemplate,
                                                                        WorkflowProperties properties) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(properties.getKafka().getTopicDlq(), record.partition()));
    }

    /**
     * A {@code CommonErrorHandler} bean is auto-detected by Spring Boot's Kafka autoconfiguration
     * and applied to the default listener container factory, so no manual factory wiring is needed.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3));
    }
}
