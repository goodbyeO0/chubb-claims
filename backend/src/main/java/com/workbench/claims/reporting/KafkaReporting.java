package com.workbench.claims.reporting;

import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "claims.reporting.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class KafkaReporting {
  public static final String TOPIC = "claims.exposure.v1";
  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, String> kafka;
  private final ExposureConsumer consumer;

  KafkaReporting(
      JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, ExposureConsumer consumer) {
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.consumer = consumer;
  }

  @Bean
  static org.springframework.kafka.support.ProducerListener<Object, Object> safeProducerListener() {
    return new org.springframework.kafka.support.ProducerListener<>() {
      @Override
      public void onError(
          org.apache.kafka.clients.producer.ProducerRecord<Object, Object> record,
          org.apache.kafka.clients.producer.RecordMetadata metadata,
          Exception exception) {
        LoggerFactory.getLogger(KafkaReporting.class)
            .warn(
                "broker_send_failed topic={} reason={}",
                record.topic(),
                exception.getClass().getSimpleName());
      }
    };
  }

  @Bean
  NewTopic exposureTopic() {
    return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
  }

  @Bean
  DefaultErrorHandler retryErrors() {
    // Never acknowledge poison data as applied. Repair/replay operationally; retain the partition
    // offset.
    var handler = new DefaultErrorHandler(new FixedBackOff(2000, Long.MAX_VALUE));
    handler.setClassifications(java.util.Map.of(Exception.class, true), true);
    return handler;
  }

  @KafkaListener(topics = TOPIC, groupId = "claims-exposure-v1")
  public void receive(ConsumerRecord<String, String> record) {
    try {
      consumer.apply(record.key(), record.value());
    } catch (RuntimeException ex) {
      LoggerFactory.getLogger(getClass())
          .error(
              "snapshot_processing_error partition={} offset={} reason={}",
              record.partition(),
              record.offset(),
              ex.getClass().getSimpleName());
      throw ex;
    }
  }

  @Scheduled(fixedDelayString = "${claims.reporting.poll-ms:1000}", initialDelay = 1000)
  public void publish() {
    var rows =
        jdbc.queryForList(
            "select o.event_id,o.payload::text as"
                + " payload,e.claim_id,e.claim_version,c.claim_ref,extract(epoch from"
                + " (current_timestamp-e.occurred_at)) as age from outbox o join claim_event e on"
                + " e.id=o.event_id join claim c on c.id=e.claim_id where o.published_at is null"
                + " order by e.occurred_at,o.event_id limit 20");
    if (!rows.isEmpty())
      LoggerFactory.getLogger(getClass())
          .info(
              "outbox_pending batchSize={} oldestAgeSeconds={}",
              rows.size(),
              rows.getFirst().get("age"));
    for (var row : rows) {
      try {
        kafka
            .send(TOPIC, row.get("claim_id").toString(), row.get("payload").toString())
            .get(3, TimeUnit.SECONDS);
        jdbc.update(
            "update outbox set published_at=current_timestamp where event_id=? and published_at is"
                + " null",
            row.get("event_id"));
        LoggerFactory.getLogger(getClass())
            .info(
                "event_published eventId={} claimRef={} claimVersion={}",
                row.get("event_id"),
                row.get("claim_ref"),
                row.get("claim_version"));
      } catch (Exception ex) {
        if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        LoggerFactory.getLogger(getClass())
            .warn(
                "publish_retry eventId={} claimRef={} claimVersion={} reason={}",
                row.get("event_id"),
                row.get("claim_ref"),
                row.get("claim_version"),
                ex.getClass().getSimpleName());
        break;
      }
    }
  }
}
