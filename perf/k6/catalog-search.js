// Load profile for the stability test: plant search (DB query + JSON) in catalog-service.
// The thresholds are the performance quality gate; k6 exits with 99 when one is crossed.
//   P95_MS (default 300), ERROR_RATE (default 0.01), VUS (20), DURATION (30s), BASE_URL
import http from 'k6/http';
import { check } from 'k6';

const p95 = __ENV.P95_MS || '300';
const errorRate = __ENV.ERROR_RATE || '0.01';

export const options = {
    vus: Number(__ENV.VUS || 20),
    duration: __ENV.DURATION || '30s',
    thresholds: {
        http_req_duration: [`p(95)<${p95}`],
        http_req_failed: [`rate<${errorRate}`],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const baseUrl = __ENV.BASE_URL || 'http://catalog-service:8081';

export default function () {
    const res = http.get(`${baseUrl}/api/catalog/plants?careLevel=EASY&size=10`, { timeout: '5s' });
    check(res, { 'status is 200': (r) => r.status === 200 });
}
