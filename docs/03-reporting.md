# 6. Reporting and backend completion

Implement workload and exposure from the [HTTP contract](../api/openapi.yaml). Use the exact event body in [exposure-event.schema.json](../api/exposure-event.schema.json).

1. Implement a direct SQL reference query grouping open claims by currency. For each group return count(*), count(*) FILTER (WHERE reserve IS NULL), and COALESCE(sum(reserve),0). Format totals as two-decimal strings. This is the reconciliation oracle. Build the workload endpoint from claim and app_user: open assigned count by every officer (include zero), unassigned count, AWAITING_INFO count, and claims closed in the rolling seven days ending at the injected Clock instant. Awaiting-info is a subset of open workload.
2. Publish full financial state on submission, reserve updates, settlement and rejection. Use topic claims.exposure.v1 keyed by claim UUID. outbox.event_id equals claim_event.id and payload.eventId. The payload records state **at commit time**. A worker must publish the stored payload, even when the claim has changed again. The API acknowledges a successful database commit without waiting on the broker.
3. Run one scheduled publisher in the demo. Poll a small batch of pending rows, wait for broker acknowledgement with a bounded timeout, then mark only acknowledged rows as published. Never mark failed sends. Record retries and retry on a bounded backoff. A crash after acknowledgement may send a duplicate. The consumer protects the financial result. For a future multi-instance deployment use row leasing or an equivalent coordination strategy.
4. Consume with Spring Kafka. Validate key and payload, apply the upsert below in a PostgreSQL transaction, and commit the Kafka offset **after** the database commit. Disable auto-commit. Transient failures leave the message retryable. Track invalid payloads as processing errors with the event identifier where available; document the operator recovery path. A poison message must never create a financial row or be reported as successfully applied.
5. Serve exposure from exposure_snapshot. Include only open snapshots, group by currency sorted by code. lastAppliedAt is the maximum applied_at across all snapshot rows, including closed ones, or null for an empty table. It indicates the last applied change and by itself cannot prove the queue has caught up.
6. Make logs searchable by claimRef/eventId/claimVersion: reserve_saved, publish_retry, event_published, snapshot_updated and snapshot_skipped. Log a reason for skipped versions. Measure or log the oldest unpublished event age by joining outbox to claim_event.occurred_at. Keep one JSON deployment example and the command docker compose logs -f api in the final README.

```sql
INSERT INTO exposure_snapshot (claim_id, claim_version, currency, is_open, reserve, applied_at)
VALUES (:claimId, :claimVersion, :currency, :isOpen, :reserve, CURRENT_TIMESTAMP)
ON CONFLICT (claim_id) DO UPDATE
SET claim_version=EXCLUDED.claim_version, currency=EXCLUDED.currency,
    is_open=EXCLUDED.is_open, reserve=EXCLUDED.reserve, applied_at=EXCLUDED.applied_at
WHERE exposure_snapshot.claim_version < EXCLUDED.claim_version;
```

A changed row count of zero means the incoming version was equal or older. Versions can skip. For PostgreSQL timestamptz serialization use UTC on the wire.

**Gate R1–R5.** From backend/ run ./mvnw -Dtest=ReportingTest,KafkaFlowTest test, then ./mvnw test. Use PostgreSQL and at least one real broker-backed publish/consume integration test. For recovery, stop only the disposable broker, submit a reserve, assert HTTP success and a pending row; restart the broker and poll a bounded interval for the matching snapshot version. Include the last observed row/log on timeout. Reconcile by currency with the direct query after catch-up. If the normal flow stalls, locate the first missing stage by eventId in outbox, publisher logs and consumer logs.

**Backend completion gate.** Re-run the contract checker. Exercise the auth handshake, intake, staff commands, two reports and both terminal outcomes against the actual API. Compare response status, content type and fields to api/openapi.yaml. Record commands and real outcomes. Begin the fixture UI after these backend gates pass.
