from __future__ import annotations

from dataclasses import asdict, dataclass, field
from enum import Enum


class Severity(str, Enum):
    OK = "ok"
    WARNING = "warning"
    CRITICAL = "critical"


SEVERITY_ICONS = {Severity.OK: "✔", Severity.WARNING: "⚠", Severity.CRITICAL: "✘"}


@dataclass
class Finding:
    """Un hallazgo del diagnóstico, con una recomendación concreta para el técnico."""

    severity: Severity
    message: str
    advice: str = ""


@dataclass
class CheckResult:
    name: str
    target: str
    ok: bool
    latency_ms: float | None = None
    detail: str = ""


@dataclass
class DiskInfo:
    mountpoint: str
    total_gb: float
    used_gb: float
    percent: float


@dataclass
class SystemInfo:
    hostname: str
    os: str
    architecture: str
    python: str
    cpu_model: str
    cpu_cores: int
    cpu_percent: float
    memory_total_gb: float
    memory_used_gb: float
    memory_percent: float
    uptime_hours: float
    disks: list[DiskInfo] = field(default_factory=list)
    ip_addresses: dict[str, list[str]] = field(default_factory=dict)

    def to_dict(self) -> dict:
        return asdict(self)
