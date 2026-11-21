# Reporting operations

HTTP commands commit PostgreSQL state, an audit event and (for financial actions) the full exposure outbox payload together. HTTP success does not wait for Kafka. A single scheduled publisher sends the stored payload keyed by claim UUID, waits up to three seconds, then marks only acknowledged messages published. A lost acknowledgement can produce duplicates. The consumer applies only a strictly newer financial version in PostgreSQL before its record offset is acknowledged. Intermediate nonfinancial versions need no event.

Workload is synchronous SQL. Exposure is asynchronous; `lastAppliedAt` includes closed snapshots and is a freshness hint, not a guarantee that all pending work has applied. Reconciliation groups open claims by currency, counts NULL reserves separately and sums only known reserves. Settlement removes the full outstanding reserve, not the paid amount. Never add different currencies together.

## Trace a delayed change

```sh
docker compose logs -f api
# Search one recorded eventId in the output:
docker compose logs api | rg 'eventId=YOUR_EVENT_UUID'
```

The expected sequence for a reserve is `reserve_saved`, `event_published`, then `snapshot_updated` or `snapshot_skipped`. `publish_retry` leaves the outbox pending. Logs carry eventId, claimRef and claimVersion, never incident descriptions, credentials, tokens or command bodies. `outbox_pending` logs batch size and oldest age. Deployment profile JSON example:

```json
{"message":"snapshot_updated eventId=00000000-0000-4000-8000-000000000305 claimRef=CL-101 claimVersion=5 reason=newer_version","service.name":"claims"}
```

For diagnosis, query event metadata and published state rather than dumping payloads. Compare the event version to the snapshot version. After connectivity returns, pending rows retry automatically. A single publisher is intentional for this local demo; multiple instances require row leasing and shared session storage.

## Invalid messages

Invalid payloads or mismatched keys fail processing and retain the partition offset. The error identifies the partition/offset; broker delivery is not reported as financial application. A poison record blocks that partition. There is no automatic dead-letter or offset-skipping policy in this prototype.

Recovery requires an operator to stop the consumer, preserve the offending record for investigation, reconcile it with the canonical claim_event/outbox data and schema, and repair the producer or consumer defect. If the immutable Kafka record itself is malformed, publish a schema-valid replacement from the verified outbox and deliberately advance only the affected partition past the recorded invalid offset. Record that intervention and reconcile the snapshot against the direct claim query before resuming normal processing. Never bulk-reset offsets or mark pending rows published merely to clear a dashboard warning.

## Scope and runtime

This is a single-team, bounded local dataset. Lists are unpaginated. PostgreSQL is durable on a named volume; sessions are in memory and signing in again after API restart is expected. Local HTTP and seeded identities are for a fictional demo only. Deployment profile enables Secure cookies and JSON stdout; a real deployment also needs HTTPS, supplied secrets, identity provisioning, retention policy and a security review. External coverage lookup, payments, documents and notifications are not implemented.
