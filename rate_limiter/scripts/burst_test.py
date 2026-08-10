#!/usr/bin/env python3
"""Generate normal and burst traffic against the rate-limited demo endpoint.

Phases:
  1. normal  - steady low-rate requests (default 2 req/s for 15 s)
  2. burst   - high-rate concurrent requests (default 50 req/s for 5 s)
  3. recover - pause, then verify the bucket refilled and requests pass again

Requires Python 3.8+ (stdlib only). Start the app first:
  docker compose up -d && mvn spring-boot:run
"""
import argparse
import threading
import time
import urllib.error
import urllib.request
from collections import Counter

BASE = "http://localhost:8080/api/demo"


def one_request(client_id):
    req = urllib.request.Request(BASE, headers={"X-Client-Id": client_id})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception:
        return 0


def run_normal(client_id, rate, duration):
    statuses = Counter()
    deadline = time.monotonic() + duration
    while time.monotonic() < deadline:
        statuses[one_request(client_id)] += 1
        time.sleep(1.0 / rate)
    return statuses


def run_burst(client_id, rate, duration):
    statuses = Counter()
    deadline = time.monotonic() + duration
    while time.monotonic() < deadline:
        workers = [threading.Thread(target=lambda: statuses.update([one_request(client_id)]))
                   for _ in range(rate)]
        for w in workers:
            w.start()
        for w in workers:
            w.join()
        time.sleep(1.0)
    return statuses


def run_recovery(client_id, pause, count):
    time.sleep(pause)
    return Counter(one_request(client_id) for _ in range(count))


def report(label, statuses, total):
    ok = statuses.get(200, 0)
    print(f"\n--- {label} ({total} requests) ---")
    print(f"  allowed (200): {ok}  |  blocked (429): {statuses.get(429, 0)}  |  other: {sum(v for k, v in statuses.items() if k not in (200, 429))}")
    print(f"  pass rate: {100 * ok / total:.1f}%")


def main():
    global BASE  # noqa: PLW0603
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--base-url", default=BASE, help="rate-limited endpoint URL")
    p.add_argument("--client-id", default="loadtest", help="X-Client-Id header value")
    p.add_argument("--normal-rate", type=float, default=2, help="requests per second, normal phase")
    p.add_argument("--normal-duration", type=float, default=15, help="seconds for normal phase")
    p.add_argument("--burst-rate", type=int, default=50, help="requests per second, burst phase")
    p.add_argument("--burst-duration", type=float, default=5, help="seconds for burst phase")
    p.add_argument("--recovery-pause", type=float, default=3, help="pause before recovery check")
    args = p.parse_args()
    BASE = args.base_url

    print(f"Rate limiter load test  (client={args.client_id}, endpoint={BASE})")

    normal = run_normal(args.client_id, args.normal_rate, args.normal_duration)
    report(f"Normal traffic  ({args.normal_rate}/s for {args.normal_duration}s)", normal, sum(normal.values()))

    burst = run_burst(args.client_id, args.burst_rate, args.burst_duration)
    report(f"Burst traffic  ({args.burst_rate}/s for {args.burst_duration}s)", burst, sum(burst.values()))

    recovery = run_recovery(args.client_id, args.recovery_pause, 5)
    report(f"Recovery check  (after {args.recovery_pause}s pause, 5 requests)", recovery, sum(recovery.values()))


if __name__ == "__main__":
    main()