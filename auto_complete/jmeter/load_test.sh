#!/bin/bash

HOST="localhost:8080"
BASE_URL="http://$HOST/api/autocomplete"
RESULTS_FILE="results.txt"

CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

check_server() {
    if ! curl -s "$BASE_URL?prefix=a&k=1" > /dev/null 2>&1; then
        echo "Server not running at $HOST"
        exit 1
    fi
    echo "Server is running"
}

flush_cache() {
    docker exec auto_complete-redis-1 redis-cli FLUSHALL > /dev/null 2>&1 || true
    sleep 1
}

calc_stats() {
    awk '{
        sum+=$1*1000; count++
        if($1*1000<min||NR==1) min=$1*1000
        if($1*1000>max) max=$1*1000
        a[NR]=$1*1000
    } END {
        for(i=1;i<=NR;i++) for(j=i+1;j<=NR;j++)
            if(a[i]>a[j]) {t=a[i];a[i]=a[j];a[j]=t}
        printf "%.1f %.1f %.1f %.1f", sum/NR, min, max, a[int(NR*0.95)+1]
    }'
}

test_cold_start() {
    echo ""
    echo "=== Test 1: Cold Start (Cache Miss) ==="
    flush_cache

    local tmpfile="/tmp/cold_start.txt"
    > "$tmpfile"

    echo "Running 100 requests with different prefixes..."
    for i in $(seq 1 100); do
        prefix=$(echo app net py do wh yo ba ca ma vi | tr ' ' '\n' | sed -n "$((i % 10 + 1))p")
        curl -s -o /dev/null -w "%{time_total}\n" "$BASE_URL?prefix=$prefix&k=8" >> "$tmpfile"
    done

    local count=$(wc -l < "$tmpfile")
    local stats=$(cat "$tmpfile" | calc_stats)

    echo "  Requests: $count"
    echo "$stats" | awk '{printf "  Avg: %.1fms | Min: %.1fms | Max: %.1fms | p95: %.1fms\n", $1, $2, $3, $4}'
    echo "cold_start,$count,$stats" >> "$RESULTS_FILE"
}

test_cache_hit() {
    echo ""
    echo "=== Test 2: Cache Hit ==="

    echo "Warming up cache..."
    for i in $(seq 1 10); do
        curl -s -o /dev/null "$BASE_URL?prefix=app&k=8"
    done

    local tmpfile="/tmp/cache_hit.txt"
    > "$tmpfile"

    echo "Running 100 requests with cached prefix..."
    for i in $(seq 1 100); do
        curl -s -o /dev/null -w "%{time_total}\n" "$BASE_URL?prefix=app&k=8" >> "$tmpfile"
    done

    local count=$(wc -l < "$tmpfile")
    local stats=$(cat "$tmpfile" | calc_stats)

    echo "  Requests: $count"
    echo "$stats" | awk '{printf "  Avg: %.1fms | Min: %.1fms | Max: %.1fms | p95: %.1fms\n", $1, $2, $3, $4}'
    echo "cache_hit,$count,$stats" >> "$RESULTS_FILE"
}

test_concurrent() {
    echo ""
    echo "=== Test 3: Concurrent Load ==="
    echo "Users | Req/sec | Avg(ms) | p95(ms)"
    echo "------|---------|---------|--------"

    for users in 10 50 100 200; do
        flush_cache

        local tmpfile="/tmp/concurrent_${users}.txt"
        > "$tmpfile"
        local start=$(date +%s)

        for i in $(seq 1 $((users * 5))); do
            prefix=$(echo app net py do wh yo ba ca ma vi | tr ' ' '\n' | shuf -n 1)
            curl -s -o /dev/null -w "%{time_total}\n" "$BASE_URL?prefix=$prefix&k=8" >> "$tmpfile" &
            if (( i % users == 0 )); then wait; fi
        done
        wait

        local end=$(date +%s)
        local elapsed=$((end - start + 1))
        local count=$(wc -l < "$tmpfile")
        local rps=$((count / elapsed))
        local stats=$(cat "$tmpfile" | calc_stats)

        echo "$stats" | awk -v u="$users" -v r="$rps" '{printf "  %4d | %7d | %7.1f | %7.1f\n", u, r, $1, $4}'
        echo "concurrent_${users},$count,$stats,,$rps" >> "$RESULTS_FILE"
    done
}

main() {
    echo "========================================"
    echo "   Autocomplete Load Testing Script"
    echo "========================================"

    check_server
    echo "test,requests,avg_ms,min_ms,max_ms,p95_ms,rps" > "$RESULTS_FILE"

    test_cold_start
    test_cache_hit
    test_concurrent

    echo ""
    echo "=== SUMMARY ==="
    column -t -s',' "$RESULTS_FILE" 2>/dev/null || cat "$RESULTS_FILE"
}

main "$@"
