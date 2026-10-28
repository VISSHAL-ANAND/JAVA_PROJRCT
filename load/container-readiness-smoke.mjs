import http from 'node:http';
import { performance } from 'node:perf_hooks';

const configuredBase = process.env.CAMPUSOS_LOAD_TEST_URL || '';
let base;
try { base = new URL(configuredBase); } catch { throw new Error('CAMPUSOS_LOAD_TEST_URL must be a valid local HTTP URL.'); }
if (base.protocol !== 'http:' || !['127.0.0.1', 'localhost', '::1'].includes(base.hostname)) {
  throw new Error('This stress test only permits localhost HTTP targets; refusing any remote host.');
}

const concurrency = 1_000;
const timeoutMs = 15_000;
const target = new URL('/api/health/ready', base);
const agent = new http.Agent({ keepAlive: false, maxSockets: concurrency, maxTotalSockets: concurrency });
const latencies = [];
const failures = [];
const started = performance.now();

function requestOne(index) {
  const requestStarted = performance.now();
  return new Promise((resolve) => {
    let body = '';
    const request = http.get(target, { agent, timeout: timeoutMs, headers: { accept: 'application/json' } }, (response) => {
      response.setEncoding('utf8');
      response.on('data', (chunk) => { body += chunk; });
      response.on('end', () => {
        const elapsed = performance.now() - requestStarted;
        latencies.push(elapsed);
        let validBody = false;
        try { const parsed = JSON.parse(body); validBody = parsed.status === 'ready' && parsed.database === 'connected'; } catch {}
        if (response.statusCode !== 200 || !validBody) failures.push({ index, status: response.statusCode, body: body.slice(0, 200) });
        resolve();
      });
    });
    request.on('timeout', () => request.destroy(new Error('request timeout')));
    request.on('error', (error) => { failures.push({ index, error: error.message }); resolve(); });
  });
}

try { await Promise.all(Array.from({ length: concurrency }, (_, index) => requestOne(index))); }
finally { agent.destroy(); }

latencies.sort((a, b) => a - b);
const percentile = (p) => latencies[Math.max(0, Math.ceil(p / 100 * latencies.length) - 1)] ?? Infinity;
const totalMs = Math.round(performance.now() - started);
const p50Ms = Math.round(percentile(50));
const p95Ms = Math.round(percentile(95));
const maxMs = Math.round(latencies.at(-1) ?? Infinity);
console.log('CAMPUSOS production-container readiness load: requests=' + concurrency + ' succeeded=' + (concurrency - failures.length) + ' failed=' + failures.length + ' totalMs=' + totalMs + ' p50Ms=' + p50Ms + ' p95Ms=' + p95Ms + ' maxMs=' + maxMs);
if (failures.length) { console.error('First failures:', JSON.stringify(failures.slice(0, 10))); process.exitCode = 1; }
if (latencies.length !== concurrency || totalMs >= 20_000 || p95Ms >= 10_000) {
  console.error('Container failed the 1,000-request database-readiness latency budget (total <20s, p95 <10s).');
  process.exitCode = 1;
}
