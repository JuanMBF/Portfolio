#!/usr/bin/env bash
# Simula una caída abrupta del nodo primario (SIGKILL) mientras una aplicación escribe,
# mide cuánto tarda el replica set en recuperarse y después reincorpora el nodo.
set -euo pipefail
cd "$(dirname "$0")/.."

LAB="${LAB:-docker compose run --rm -T lab}"

PRIMARY=$($LAB primary)
NODE="${PRIMARY%%:*}"

$LAB failover --mode external &
LAB_PID=$!

sleep "${WARMUP:-3}"
echo ">> docker compose kill $NODE   (caída abrupta, sin apagado ordenado)"
docker compose kill "$NODE" >/dev/null 2>&1

wait "$LAB_PID"

echo
echo ">> Reincorporando $NODE al replica set..."
docker compose start "$NODE" >/dev/null 2>&1
for _ in $(seq 1 60); do
  # Espera a que vuelva como secundario y sin retraso de replicación.
  if $LAB status 2>/dev/null | grep -Eq "$NODE.*SECONDARY.*OK.*\| 0\.0 s"; then
    break
  fi
  sleep 1
done
$LAB status
