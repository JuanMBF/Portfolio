"""Pruebas de conectividad: DNS, TCP y HTTP, ejecutadas en paralelo."""

from __future__ import annotations

import socket
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from .models import CheckResult, Finding, Severity

DNS_HOST = "www.google.com"
PUBLIC_IP = "8.8.8.8"
HTTP_URL = "https://www.google.com/generate_204"

WELL_KNOWN_PORTS = {
    21: "FTP", 22: "SSH", 25: "SMTP", 53: "DNS", 80: "HTTP", 110: "POP3", 143: "IMAP",
    443: "HTTPS", 445: "SMB", 587: "SMTP (envío)", 993: "IMAPS", 1433: "SQL Server",
    3306: "MySQL", 3389: "Escritorio remoto", 5432: "PostgreSQL", 8080: "HTTP alternativo",
    27017: "MongoDB",
}
DEFAULT_PORTS = [22, 53, 80, 443, 3389]


def _elapsed_ms(start: float) -> float:
    return round((time.perf_counter() - start) * 1000, 1)


def check_dns(host: str = DNS_HOST) -> CheckResult:
    start = time.perf_counter()
    try:
        address = socket.gethostbyname(host)
        return CheckResult("DNS", host, True, _elapsed_ms(start), f"resuelve a {address}")
    except OSError as error:
        return CheckResult("DNS", host, False, None, f"no resuelve ({error})")


def check_tcp(host: str, port: int, timeout: float = 2.0, name: str = "TCP") -> CheckResult:
    start = time.perf_counter()
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return CheckResult(name, f"{host}:{port}", True, _elapsed_ms(start), "conexión aceptada")
    except OSError as error:
        return CheckResult(name, f"{host}:{port}", False, None, _describe_socket_error(error))


def check_http(url: str = HTTP_URL, timeout: float = 4.0) -> CheckResult:
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(url, timeout=timeout) as response:  # noqa: S310 (URL fija o elegida por el técnico)
            return CheckResult("HTTP", url, response.status < 400, _elapsed_ms(start), f"HTTP {response.status}")
    except urllib.error.HTTPError as error:
        return CheckResult("HTTP", url, False, _elapsed_ms(start), f"HTTP {error.code}")
    except (urllib.error.URLError, OSError) as error:
        return CheckResult("HTTP", url, False, None, f"sin respuesta ({getattr(error, 'reason', error)})")


def _describe_socket_error(error: OSError) -> str:
    if isinstance(error, socket.timeout):
        return "sin respuesta (timeout): posible firewall"
    if isinstance(error, ConnectionRefusedError):
        return "conexión rechazada: el puerto está cerrado"
    if isinstance(error, socket.gaierror):
        return "no se pudo resolver el nombre"
    return str(error)


def run_connectivity_checks() -> list[CheckResult]:
    """Corre las cuatro pruebas en paralelo: el diagnóstico tarda lo que la más lenta, no la suma."""
    tasks = [
        lambda: check_dns(DNS_HOST),
        lambda: check_tcp(PUBLIC_IP, 53, name="Internet (IP)"),
        lambda: check_tcp(DNS_HOST, 443, name="HTTPS"),
        lambda: check_http(HTTP_URL),
    ]
    with ThreadPoolExecutor(max_workers=len(tasks)) as pool:
        return list(pool.map(lambda task: task(), tasks))


def diagnose(checks: list[CheckResult]) -> Finding:
    """Interpreta el conjunto de pruebas como lo haría un técnico de mesa de ayuda."""
    by_name = {c.name: c.ok for c in checks}
    dns, ip, https, http = (by_name.get(k, False) for k in ("DNS", "Internet (IP)", "HTTPS", "HTTP"))

    if all((dns, ip, https, http)):
        return Finding(Severity.OK, "Conectividad a internet correcta")
    if not any((dns, ip, https, http)):
        return Finding(Severity.CRITICAL, "Sin conexión a internet",
                       "Revisar cable o WiFi, reiniciar el router y verificar que el equipo tenga IP.")
    if ip and not dns:
        return Finding(Severity.CRITICAL, "Hay conexión pero falla la resolución de nombres (DNS)",
                       "Probar con otro servidor DNS (por ejemplo 8.8.8.8) o revisar la configuración de red.")
    if dns and not (https or http):
        return Finding(Severity.WARNING, "Resuelve nombres pero no puede navegar",
                       "Posible firewall bloqueando el tráfico web.")
    if https and not http:
        return Finding(Severity.WARNING, "La conexión directa funciona pero la navegación web falla",
                       "Posible proxy que bloquea el sitio o pide autenticación: revisar la configuración de proxy.")
    return Finding(Severity.WARNING, "Conectividad parcial",
                   "Algunas pruebas fallaron: repetir el diagnóstico y revisar el detalle.")


def parse_ports(text: str) -> list[int]:
    """Acepta '22,80,443' y rangos cortos como '8000-8010'."""
    ports: set[int] = set()
    for part in filter(None, (p.strip() for p in text.split(","))):
        if "-" in part:
            start, end = (int(x) for x in part.split("-", 1))
            if end < start or end - start > 1024:
                raise ValueError(f"Rango inválido: {part} (máximo 1024 puertos por rango)")
            ports.update(range(start, end + 1))
        else:
            ports.add(int(part))
    invalid = [p for p in ports if not 1 <= p <= 65535]
    if invalid:
        raise ValueError(f"Puertos fuera de rango: {invalid}")
    return sorted(ports)


def check_ports(host: str, ports: list[int], timeout: float = 1.0) -> list[CheckResult]:
    with ThreadPoolExecutor(max_workers=min(50, len(ports)) or 1) as pool:
        return list(pool.map(lambda p: check_tcp(host, p, timeout, WELL_KNOWN_PORTS.get(p, "TCP")), ports))
