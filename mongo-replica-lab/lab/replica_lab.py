"""Herramientas para probar un replica set de MongoDB.

Comandos:
    status      Estado de cada nodo, rol y retraso de replicación.
    load        Inserta N documentos en el primario y verifica que lleguen a todos los nodos.
    verify      Compara cantidad de documentos y una huella (hash) entre nodos.
    primary     Imprime el host del primario actual.
    failover    Mide qué ve una aplicación que escribe mientras se cae el primario.

Ejemplos:
    python replica_lab.py load --count 10000
    python replica_lab.py failover --mode stepdown
"""

from __future__ import annotations

import argparse
import hashlib
import math
import os
import random
import sys
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

from pymongo import MongoClient, WriteConcern
from pymongo.errors import AutoReconnect, ConnectionFailure, PyMongoError

DATABASE = "lab"
COLLECTION = "events"
DEFAULT_HOSTS = "mongo1:27017,mongo2:27017,mongo3:27017"

COURSES = ["Programación I", "Bases de Datos", "Redes", "Testing", "Ingeniería de Datos", "Seguridad"]
ACTIONS = ["login", "view_material", "submit_assignment", "join_class", "download", "logout"]


# --------------------------------------------------------------------------- utilidades puras


def hosts_from_env() -> list[str]:
    return [h.strip() for h in os.environ.get("MONGO_HOSTS", DEFAULT_HOSTS).split(",") if h.strip()]


def replica_set_uri(hosts: list[str], replica_set: str) -> str:
    return f"mongodb://{','.join(hosts)}/?replicaSet={replica_set}"


def generate_events(count: int, run_id: str, seed: int = 42) -> list[dict]:
    """Genera eventos sintéticos de un campus virtual. Con la misma semilla, siempre los mismos datos."""
    rng = random.Random(seed)
    start = datetime(2026, 3, 2, 8, 0, tzinfo=timezone.utc)
    return [
        {
            "run_id": run_id,
            "seq": i,
            "student_id": f"A{rng.randint(1, 2500):05d}",
            "course": rng.choice(COURSES),
            "action": rng.choice(ACTIONS),
            "duration_ms": rng.randint(20, 5000),
            "created_at": start + timedelta(seconds=i * 7),
        }
        for i in range(count)
    ]


def chunks(items: list, size: int):
    for i in range(0, len(items), size):
        yield items[i : i + size]


def fingerprint(seqs: list[int]) -> str:
    """Huella corta de un conjunto de documentos: si dos nodos tienen exactamente lo mismo, coincide."""
    digest = hashlib.sha256(",".join(map(str, sorted(seqs))).encode()).hexdigest()
    return digest[:12]


def replication_lag_seconds(members: list[dict]) -> dict[str, float]:
    """Retraso de cada miembro respecto del primario, a partir de la salida de replSetGetStatus."""
    primary = next((m for m in members if m.get("stateStr") == "PRIMARY"), None)
    if primary is None:
        return {}
    reference = primary["optimeDate"]
    return {
        m["name"]: max(0.0, (reference - m["optimeDate"]).total_seconds())
        for m in members
        if m.get("optimeDate") is not None and m.get("health", 0) == 1
    }


def format_table(headers: list[str], rows: list[list]) -> str:
    widths = [max(len(str(x)) for x in col) for col in zip(headers, *rows)]
    line = "+" + "+".join("-" * (w + 2) for w in widths) + "+"

    def render(row):
        return "| " + " | ".join(str(cell).ljust(w) for cell, w in zip(row, widths)) + " |"

    return "\n".join([line, render(headers), line, *(render(r) for r in rows), line])


@dataclass
class WriterStats:
    """Lo que vio una "aplicación" que escribe sin parar durante el failover."""

    acknowledged: list[int] = field(default_factory=list)
    errors: int = 0
    success_times: list[float] = field(default_factory=list)

    def longest_gap(self) -> float:
        """Mayor tiempo entre dos escrituras exitosas consecutivas: lo que el usuario 'siente' como corte."""
        times = self.success_times
        return max((b - a for a, b in zip(times, times[1:])), default=0.0)


# --------------------------------------------------------------------------- conexión


class Lab:
    def __init__(self, hosts: list[str], replica_set: str, timeout_ms: int = 5000):
        self.hosts = hosts
        self.replica_set = replica_set
        self.timeout_ms = timeout_ms
        self.client = MongoClient(
            replica_set_uri(hosts, replica_set),
            serverSelectionTimeoutMS=timeout_ms,
            retryWrites=True,
        )

    def direct(self, host: str) -> MongoClient:
        """Conexión directa a un nodo puntual (sin descubrir el replica set)."""
        return MongoClient(
            f"mongodb://{host}/?directConnection=true&readPreference=secondaryPreferred",
            serverSelectionTimeoutMS=2000,
            connectTimeoutMS=2000,
        )

    def collection(self):
        return self.client[DATABASE].get_collection(COLLECTION, write_concern=WriteConcern(w="majority"))

    def members(self) -> list[dict]:
        return self.client.admin.command("replSetGetStatus")["members"]

    def node_role(self, host: str) -> str | None:
        try:
            with self.direct(host) as client:
                hello = client.admin.command("hello")
        except PyMongoError:
            return None
        if hello.get("isWritablePrimary"):
            return "PRIMARY"
        return "SECONDARY" if hello.get("secondary") else "OTHER"

    def primary_host(self, candidates: list[str] | None = None) -> str | None:
        for host in candidates or self.hosts:
            if self.node_role(host) == "PRIMARY":
                return host
        return None

    def node_snapshot(self, host: str, run_id: str) -> tuple[int, str] | None:
        try:
            with self.direct(host) as client:
                seqs = [d["seq"] for d in client[DATABASE][COLLECTION].find({"run_id": run_id}, {"seq": 1, "_id": 0})]
        except PyMongoError:
            return None
        return len(seqs), fingerprint(seqs)


# --------------------------------------------------------------------------- comandos


def cmd_status(lab: Lab, _args) -> int:
    members = lab.members()
    lags = replication_lag_seconds(members)
    rows = [
        [
            m["name"],
            m["stateStr"],
            "OK" if m.get("health") == 1 else "CAÍDO",
            f"{lags[m['name']]:.1f} s" if m["name"] in lags else "-",
            m.get("syncSourceHost") or "-",
        ]
        for m in members
    ]
    print(format_table(["Nodo", "Rol", "Salud", "Retraso", "Replica desde"], rows))
    return 0


def cmd_load(lab: Lab, args) -> int:
    run_id = args.run_id or uuid.uuid4().hex[:8]
    docs = generate_events(args.count, run_id)
    coll = lab.collection()
    if not args.keep:
        coll.drop()
    coll.create_index([("run_id", 1), ("seq", 1)], unique=True)

    print(f"Insertando {args.count:,} documentos en lotes de {args.batch} (writeConcern: majority)...")
    started = time.perf_counter()
    for batch in chunks(docs, args.batch):
        coll.insert_many(batch, ordered=False)
    elapsed = time.perf_counter() - started
    print(f"Listo en {elapsed:.2f} s ({args.count / elapsed:,.0f} docs/s). run_id = {run_id}\n")

    return verify_run(lab, run_id, expected=args.count, wait_seconds=args.wait)


def cmd_verify(lab: Lab, args) -> int:
    run_id = args.run_id
    if run_id is None:
        latest = lab.client[DATABASE][COLLECTION].find_one(sort=[("_id", -1)])
        if latest is None:
            print("No hay datos cargados. Corré primero: load")
            return 1
        run_id = latest["run_id"]
    expected = lab.client[DATABASE][COLLECTION].count_documents({"run_id": run_id})
    return verify_run(lab, run_id, expected=expected, wait_seconds=args.wait)


def verify_run(lab: Lab, run_id: str, expected: int, wait_seconds: float) -> int:
    """Espera a que todos los nodos tengan los mismos documentos y muestra una tabla comparativa."""
    deadline = time.monotonic() + wait_seconds
    while True:
        snapshots = {host: lab.node_snapshot(host, run_id) for host in lab.hosts}
        reachable = {h: s for h, s in snapshots.items() if s is not None}
        in_sync = all(count == expected for count, _ in reachable.values())
        if in_sync or time.monotonic() > deadline:
            break
        time.sleep(0.5)

    rows = []
    for host, snapshot in snapshots.items():
        if snapshot is None:
            rows.append([host, lab.node_role(host) or "SIN CONEXIÓN", "-", "-", "NO"])
        else:
            count, digest = snapshot
            rows.append([host, lab.node_role(host), f"{count:,}", digest, "SÍ" if count == expected else "NO"])
    print(format_table(["Nodo", "Rol", "Documentos", "Huella", "Completo"], rows))

    fingerprints = {digest for _, digest in reachable.values()}
    ok = in_sync and len(fingerprints) == 1 and len(reachable) == len(lab.hosts)
    print("\nResultado:", "todos los nodos tienen exactamente los mismos datos ✔" if ok else "hay diferencias ✘")
    return 0 if ok else 1


def cmd_primary(lab: Lab, _args) -> int:
    primary = lab.primary_host()
    if primary is None:
        print("No hay primario", file=sys.stderr)
        return 1
    print(primary)
    return 0


def cmd_failover(lab: Lab, args) -> int:
    """Mide qué ve una aplicación que escribe sin parar mientras se cae el primario.

    --mode external  espera a que otro proceso tire abajo el primario (ver scripts/crash-primary.sh).
    --mode stepdown  le pide al primario que ceda su rol (mantenimiento planificado).
    """
    primary = lab.primary_host()
    if primary is None:
        print("No hay primario. ¿Está inicializado el replica set?")
        return 1
    survivors = [h for h in lab.hosts if h != primary]
    run_id = f"failover-{uuid.uuid4().hex[:6]}"
    coll = lab.collection()
    stats = WriterStats()
    stop = threading.Event()

    def writer():
        seq = 0
        while not stop.is_set():
            try:
                coll.insert_one({"run_id": run_id, "seq": seq, "created_at": datetime.now(timezone.utc)})
                stats.acknowledged.append(seq)
                stats.success_times.append(time.monotonic())
            except PyMongoError:
                stats.errors += 1
                time.sleep(0.05)
            seq += 1
            time.sleep(args.interval)

    print(f"Primario actual: {primary}. Arranca una aplicación que escribe cada {args.interval * 1000:.0f} ms...")
    thread = threading.Thread(target=writer, daemon=True)
    thread.start()
    try:
        if args.mode == "stepdown":
            time.sleep(args.warmup)
            print(f"{len(stats.acknowledged)} escrituras confirmadas. Pidiendo al primario que ceda su rol...")
            killed_at = time.monotonic()
            try:
                with lab.direct(primary) as client:
                    client.admin.command("replSetStepDown", 30, secondaryCatchUpPeriodSecs=10)
            except (AutoReconnect, ConnectionFailure):
                pass  # Esperado: el nodo cierra las conexiones al dejar de ser primario.
        else:
            print("Esperando la caída del primario (por ejemplo: docker compose kill mongo1)...")
            deadline = time.monotonic() + args.timeout
            while lab.node_role(primary) == "PRIMARY":
                if time.monotonic() > deadline:
                    print(f"El primario no se cayó en {args.timeout} s.")
                    return 1
                time.sleep(0.1)
            killed_at = time.monotonic()
            print(f"Caída detectada después de {len(stats.acknowledged)} escrituras confirmadas.")

        new_primary = None
        while time.monotonic() - killed_at < args.timeout:
            new_primary = lab.primary_host(survivors)
            if new_primary:
                break
            time.sleep(0.1)
        election_time = time.monotonic() - killed_at

        if new_primary is None:
            print(f"No se eligió un nuevo primario en {args.timeout} s.")
            return 1
        print(f"Nuevo primario: {new_primary}. Sigo escribiendo {args.cooldown:.0f} s más...")
        time.sleep(args.cooldown)
    finally:
        stop.set()
        thread.join()

    with lab.direct(new_primary) as client:
        stored = {d["seq"] for d in client[DATABASE][COLLECTION].find({"run_id": run_id}, {"seq": 1, "_id": 0})}
    lost = [seq for seq in stats.acknowledged if seq not in stored]

    print()
    print(
        format_table(
            ["Métrica", "Valor"],
            [
                ["Primario anterior", primary],
                ["Nuevo primario", new_primary],
                ["Tiempo hasta nuevo primario", f"{election_time:.2f} s"],
                ["Corte máximo visto por la app", f"{stats.longest_gap():.2f} s"],
                ["Escrituras confirmadas", len(stats.acknowledged)],
                ["Escrituras con error", stats.errors],
                ["Confirmadas que se perdieron", len(lost)],
            ],
        )
    )
    print("\nResultado:", "ninguna escritura confirmada se perdió ✔" if not lost else f"se perdieron {len(lost)} ✘")
    return 0 if not lost else 1


# --------------------------------------------------------------------------- CLI


def positive_int(text: str) -> int:
    """Tipo de argparse: un entero mayor que cero, validado antes de tocar la base."""
    try:
        value = int(text)
    except ValueError:
        raise argparse.ArgumentTypeError(f"'{text}' no es un número entero") from None
    if value < 1:
        raise argparse.ArgumentTypeError(f"tiene que ser mayor que cero (se recibió {value})")
    return value


def seconds(text: str) -> float:
    """Tipo de argparse: una cantidad de segundos finita y no negativa.

    time.sleep() rechaza un valor negativo o "nan": en failover eso mataba al hilo que escribe después de
    la primera escritura, y el informe igual decía que no se había perdido nada.
    """
    try:
        value = float(text)
    except ValueError:
        raise argparse.ArgumentTypeError(f"'{text}' no es un número") from None
    if not math.isfinite(value) or value < 0:
        raise argparse.ArgumentTypeError(f"tiene que ser 0 o más segundos (se recibió {text})")
    return value


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Laboratorio de replicación de MongoDB")
    parser.add_argument("--replica-set", default=os.environ.get("MONGO_REPLICA_SET", "rs0"))
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("status", help="estado del replica set")

    load = sub.add_parser("load", help="carga documentos y verifica la replicación")
    load.add_argument("--count", type=positive_int, default=10_000)
    load.add_argument("--batch", type=positive_int, default=1_000)
    load.add_argument("--run-id")
    load.add_argument("--keep", action="store_true", help="no borrar los datos anteriores")
    load.add_argument("--wait", type=seconds, default=30, help="segundos máximos para esperar la replicación")

    verify = sub.add_parser("verify", help="compara los datos entre nodos")
    verify.add_argument("--run-id")
    verify.add_argument("--wait", type=seconds, default=10)

    sub.add_parser("primary", help="imprime el host del primario actual")

    failover = sub.add_parser("failover", help="mide la recuperación ante la caída del primario")
    failover.add_argument("--mode", choices=["external", "stepdown"], default="external")
    failover.add_argument("--interval", type=seconds, default=0.02, help="segundos entre escrituras")
    failover.add_argument("--warmup", type=seconds, default=2)
    failover.add_argument("--cooldown", type=seconds, default=3)
    failover.add_argument("--timeout", type=seconds, default=60)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    hosts = hosts_from_env()
    if not hosts:
        print(f"MONGO_HOSTS no tiene ningún host. Ejemplo: MONGO_HOSTS={DEFAULT_HOSTS}", file=sys.stderr)
        return 2
    try:
        lab = Lab(hosts, args.replica_set)
    except (PyMongoError, ValueError) as error:
        # MongoClient valida la URI al crearse, antes de conectar: un puerto mal escrito cae acá.
        print(f"MONGO_HOSTS inválido ({','.join(hosts)}): {error}", file=sys.stderr)
        return 2
    commands = {
        "status": cmd_status,
        "primary": cmd_primary,
        "load": cmd_load,
        "verify": cmd_verify,
        "failover": cmd_failover,
    }
    try:
        return commands[args.command](lab, args)
    except PyMongoError as error:
        print(f"Error de MongoDB: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
