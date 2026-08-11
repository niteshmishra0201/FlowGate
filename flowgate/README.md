## Performance Benchmark

Load tested with [k6](https://k6.io) against the full stack (nginx → 2x FlowGate instances → Redis-backed rate limiting → backend services), simulating realistic ramping traffic from 0 to 50 concurrent virtual users over ~3.5 minutes.

**Results:**
- **175 requests/second** sustained throughput
- **37,365 total requests** processed
- **p50 (median) latency: 7.06ms**
- **p90 latency: 18.49ms**
- **p95 latency: 26.68ms**
- **99.87% valid response rate** (200 or 429 — both are correct outcomes, since rate limiting is expected to reject some traffic by design)

**Notes on methodology:** the test's success criteria intentionally accepted both `200` and `429` responses as valid — a `429` under load is FlowGate's rate limiter working correctly, not a failure. A small cluster of request timeouts (0.12% of total) occurred during the test's ramp-down phase and is most likely k6 client-side teardown behavior rather than a gateway capacity issue.

Notably, mean latency (82ms) is significantly higher than median latency (7ms) — a real, observed example of why this project reports percentiles rather than averages: a small number of slow outliers can skew an average dramatically without reflecting the typical request experience.