import http from 'k6/http';
import { check, sleep } from 'k6';

// A valid JWT — regenerate at jwt.io if this one has expired by the time you run this.
const TOKEN = '';

export const options = {
    scenarios: {
        ramping_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 },   // ramp up to 20 concurrent virtual users
                { duration: '1m', target: 20 },    // hold steady at 20 for 1 minute
                { duration: '30s', target: 50 },   // ramp up further to 50
                { duration: '1m', target: 50 },    // hold at 50
                { duration: '30s', target: 0 },    // ramp down
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<1000'],   // fail the test if p95 latency exceeds 1s
        http_req_failed: ['rate<0.5'],        // fail if more than 50% of requests error
        // (set loosely since we EXPECT some 429s by design)
    },
};

export default function () {
    const res = http.get('http://localhost/orders/123', {
        headers: { Authorization: `Bearer ${TOKEN}` },
    });

    check(res, {
        'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    });

    sleep(0.1); // brief pause between each virtual user's requests, more realistic than hammering with zero delay
}