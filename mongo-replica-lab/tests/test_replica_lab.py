from datetime import datetime, timedelta, timezone

import pytest

from replica_lab import (
    WriterStats,
    build_parser,
    chunks,
    fingerprint,
    format_table,
    generate_events,
    replica_set_uri,
    replication_lag_seconds,
)

T0 = datetime(2026, 3, 2, 12, 0, tzinfo=timezone.utc)


def test_generated_events_are_deterministic_and_sequential():
    first = generate_events(100, "run1")
    second = generate_events(100, "run1")

    assert first == second
    assert [e["seq"] for e in first] == list(range(100))
    assert all(e["run_id"] == "run1" for e in first)


def test_different_seeds_produce_different_data():
    assert generate_events(50, "r", seed=1) != generate_events(50, "r", seed=2)


def test_chunks_cover_every_item_once():
    batches = list(chunks(list(range(10)), 4))

    assert batches == [[0, 1, 2, 3], [4, 5, 6, 7], [8, 9]]


def test_fingerprint_ignores_order_but_detects_differences():
    assert fingerprint([3, 1, 2]) == fingerprint([1, 2, 3])
    assert fingerprint([1, 2, 3]) != fingerprint([1, 2, 4])
    assert fingerprint([1, 2, 3]) != fingerprint([1, 2])


def test_replication_lag_is_relative_to_primary():
    members = [
        {"name": "mongo1:27017", "stateStr": "PRIMARY", "health": 1, "optimeDate": T0},
        {"name": "mongo2:27017", "stateStr": "SECONDARY", "health": 1, "optimeDate": T0 - timedelta(seconds=3)},
        {"name": "mongo3:27017", "stateStr": "SECONDARY", "health": 0, "optimeDate": T0 - timedelta(hours=1)},
    ]

    assert replication_lag_seconds(members) == {"mongo1:27017": 0.0, "mongo2:27017": 3.0}


def test_replication_lag_without_primary_is_empty():
    members = [{"name": "mongo2:27017", "stateStr": "SECONDARY", "health": 1, "optimeDate": T0}]

    assert replication_lag_seconds(members) == {}


def test_longest_gap_between_successful_writes():
    stats = WriterStats(success_times=[0.0, 0.02, 0.04, 8.5, 8.52])

    assert stats.longest_gap() == pytest.approx(8.46)


def test_longest_gap_with_few_writes_is_zero():
    assert WriterStats(success_times=[1.0]).longest_gap() == 0.0


def test_format_table_aligns_columns():
    table = format_table(["Nodo", "Rol"], [["mongo1", "PRIMARY"], ["mongo2", "SECONDARY"]])
    lines = table.splitlines()

    assert len({len(line) for line in lines}) == 1
    assert "| mongo2 | SECONDARY |" in table


def test_replica_set_uri():
    assert replica_set_uri(["a:1", "b:2"], "rs0") == "mongodb://a:1,b:2/?replicaSet=rs0"


def test_cli_defaults():
    args = build_parser().parse_args(["load"])

    assert args.count == 10_000
    assert args.batch == 1_000
    assert build_parser().parse_args(["failover"]).mode == "external"
