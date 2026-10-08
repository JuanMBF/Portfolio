"""Punto de entrada de la línea de comandos: `ittk <comando>`."""

from __future__ import annotations

import argparse
import getpass
import json
import os
import sys
from dataclasses import asdict
from datetime import datetime
from pathlib import Path

from . import __version__, network, sysinfo
from .helpdesk import HelpDeskClient, HelpDeskError
from .models import SEVERITY_ICONS, CheckResult, Finding, Severity
from .report import RENDERERS, Report

EXIT_CODES = {Severity.OK: 0, Severity.WARNING: 1, Severity.CRITICAL: 2}


# --------------------------------------------------------------------------- salida en terminal


class Style:
    """Colores ANSI solo si la salida es una terminal y no se pidió NO_COLOR."""

    enabled = sys.stdout.isatty() and "NO_COLOR" not in os.environ
    CODES = {"ok": "32", "warning": "33", "critical": "31", "bold": "1", "dim": "2"}

    @classmethod
    def paint(cls, text: str, style: str) -> str:
        return f"\033[{cls.CODES[style]}m{text}\033[0m" if cls.enabled else text


def print_title(text: str) -> None:
    print(Style.paint(text, "bold"))


def print_finding(finding: Finding) -> None:
    icon = Style.paint(SEVERITY_ICONS[finding.severity], finding.severity.value)
    print(f"  {icon} {finding.message}")
    if finding.advice:
        print(Style.paint(f"    → {finding.advice}", "dim"))


def print_checks(checks: list[CheckResult]) -> None:
    name_width = max(len(c.name) for c in checks)
    target_width = max(len(c.target) for c in checks)
    for c in checks:
        icon = Style.paint("✔", "ok") if c.ok else Style.paint("✘", "critical")
        latency = f"{c.latency_ms:>6.0f} ms" if c.latency_ms is not None else " " * 9
        print(f"  {icon} {c.name:<{name_width}}  {c.target:<{target_width}}  {latency}  {Style.paint(c.detail, 'dim')}")


def worst(findings: list[Finding]) -> Severity:
    order = [Severity.OK, Severity.WARNING, Severity.CRITICAL]
    return max((f.severity for f in findings), key=order.index, default=Severity.OK)


# --------------------------------------------------------------------------- comandos


def cmd_sysinfo(args) -> int:
    info = sysinfo.collect()
    findings = sysinfo.analyze(info)
    if args.json:
        print(json.dumps({"system": info.to_dict(), "findings": _findings_json(findings)}, ensure_ascii=False, indent=2))
        return EXIT_CODES[worst(findings)]

    print_title(f"Equipo: {info.hostname}")
    rows = [
        ("Sistema", f"{info.os} ({info.architecture})"),
        ("CPU", f"{info.cpu_model} · {info.cpu_cores} núcleos · uso {info.cpu_percent:.0f} %"),
        ("Memoria", f"{info.memory_used_gb} / {info.memory_total_gb} GB ({info.memory_percent:.0f} %)"),
        ("Encendido", f"hace {info.uptime_hours:.1f} h"),
        *((f"Disco {d.mountpoint}", f"{d.used_gb} / {d.total_gb} GB ({d.percent:.0f} %)") for d in info.disks),
        *((f"IP {iface}", ", ".join(ips)) for iface, ips in info.ip_addresses.items()),
    ]
    width = max(len(label) for label, _ in rows)
    for label, value in rows:
        print(f"  {label:<{width}}  {value}")
    print()
    print_title("Hallazgos")
    for finding in findings:
        print_finding(finding)
    return EXIT_CODES[worst(findings)]


def cmd_netcheck(args) -> int:
    checks = network.run_connectivity_checks()
    verdict = network.diagnose(checks)
    if args.json:
        print(json.dumps({"checks": [asdict(c) for c in checks], "diagnosis": _findings_json([verdict])[0]},
                         ensure_ascii=False, indent=2))
        return EXIT_CODES[verdict.severity]

    print_title("Pruebas de conectividad")
    print_checks(checks)
    print()
    print_title("Diagnóstico")
    print_finding(verdict)
    return EXIT_CODES[verdict.severity]


def cmd_ports(args) -> int:
    try:
        ports = network.parse_ports(args.ports)
    except ValueError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 2
    results = network.check_ports(args.host, ports, args.timeout)
    print_title(f"Puertos en {args.host}")
    print_checks(results)
    open_ports = sum(r.ok for r in results)
    print(f"\n  {open_ports} de {len(results)} puertos aceptan conexiones.")
    return 0


def build_report() -> Report:
    info = sysinfo.collect()
    checks = network.run_connectivity_checks()
    findings = [network.diagnose(checks), *sysinfo.analyze(info)]
    # Si hay problemas, el "todo OK" de los recursos no aporta: se deja solo lo relevante.
    if any(f.severity is not Severity.OK for f in findings):
        findings = [f for f in findings if f.severity is not Severity.OK]
    return Report(system=info, checks=checks, findings=findings)


def cmd_report(args) -> int:
    report = build_report()
    content = RENDERERS[args.format](report)

    output = args.output
    if output is None and args.format == "html":
        output = f"reporte-{report.system.hostname}-{datetime.now():%Y%m%d-%H%M}.html"

    if output:
        Path(output).write_text(content, encoding="utf-8")
        print(f"Reporte guardado en {output}")
    else:
        print(content)
    return EXIT_CODES[report.worst_severity]


def suggest_category(report: Report) -> str:
    """Si falla la red, es un ticket de redes; si no, cualquier problema viene de los recursos del equipo."""
    if network.diagnose(report.checks).severity is not Severity.OK:
        return "NETWORK"
    if report.worst_severity is not Severity.OK:
        return "HARDWARE"
    return "OTHER"


def suggest_priority(report: Report) -> str:
    return {Severity.CRITICAL: "HIGH", Severity.WARNING: "MEDIUM", Severity.OK: "LOW"}[report.worst_severity]


def cmd_ticket(args) -> int:
    password = os.environ.get("ITTK_PASSWORD") or getpass.getpass("Contraseña de HelpDesk Pro: ")
    print("Generando diagnóstico...")
    report = build_report()
    category = args.category or suggest_category(report)
    priority = args.priority or suggest_priority(report)

    description = (args.description.strip() + "\n\n" if args.description else "") + RENDERERS["md"](report)
    client = HelpDeskClient(args.api)
    try:
        user = client.login(args.email, password)
        ticket = client.create_ticket(args.title, description, category, priority)
    except HelpDeskError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 2

    print(f"Ticket #{ticket['id']} creado por {user['fullName']} "
          f"(categoría {category}, prioridad {priority}, SLA hasta {format_due(ticket['slaDueAt'])}).")
    return 0


def format_due(value: str) -> str:
    """La API devuelve instantes ISO-8601 en UTC (con nanosegundos); se muestran como fecha y hora legibles."""
    try:
        return f"{datetime.fromisoformat(value[:19]):%d/%m/%Y %H:%M} UTC"
    except ValueError:
        return value


def _findings_json(findings: list[Finding]) -> list[dict]:
    return [{**asdict(f), "severity": f.severity.value} for f in findings]


# --------------------------------------------------------------------------- parser


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="ittk",
        description="IT Support Toolkit: diagnósticos de soporte técnico desde la terminal.",
        epilog="Códigos de salida: 0 = todo OK, 1 = advertencias, 2 = problemas críticos o error.",
    )
    parser.add_argument("--version", action="version", version=f"%(prog)s {__version__}")
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("sysinfo", help="relevamiento del equipo: SO, CPU, RAM, discos, IPs")
    p.add_argument("--json", action="store_true", help="salida en JSON")
    p.set_defaults(func=cmd_sysinfo)

    p = sub.add_parser("netcheck", help="prueba DNS, conectividad e internet y da un diagnóstico")
    p.add_argument("--json", action="store_true", help="salida en JSON")
    p.set_defaults(func=cmd_netcheck)

    p = sub.add_parser("ports", help="verifica qué servicios responden en un host")
    p.add_argument("host")
    p.add_argument("--ports", default=",".join(map(str, network.DEFAULT_PORTS)),
                   help="lista separada por comas, admite rangos (ej.: 22,80,8000-8010)")
    p.add_argument("--timeout", type=float, default=1.0)
    p.set_defaults(func=cmd_ports)

    p = sub.add_parser("report", help="genera un reporte completo para adjuntar a un ticket")
    p.add_argument("--format", choices=sorted(RENDERERS), default="md")
    p.add_argument("--output", "-o", help="archivo de salida (por defecto: pantalla, o un .html con fecha)")
    p.set_defaults(func=cmd_report)

    p = sub.add_parser("ticket", help="abre un ticket en HelpDesk Pro con el diagnóstico adjunto")
    p.add_argument("--api", default=os.environ.get("ITTK_API", "http://localhost:8080"))
    p.add_argument("--email", required=True)
    p.add_argument("--title", required=True)
    p.add_argument("--description", help="texto propio antes del diagnóstico")
    p.add_argument("--category", choices=["HARDWARE", "SOFTWARE", "NETWORK", "ACCOUNT_ACCESS", "EMAIL",
                                          "VIRTUAL_CLASSROOM", "VIDEOCONFERENCE", "OTHER"])
    p.add_argument("--priority", choices=["LOW", "MEDIUM", "HIGH", "CRITICAL"])
    p.set_defaults(func=cmd_ticket)
    return parser


def _ensure_utf8_output() -> None:
    """En Windows, al redirigir la salida a un archivo se usa cp1252 y los íconos (✔ ✘) fallarían."""
    for stream in (sys.stdout, sys.stderr):
        encoding = (getattr(stream, "encoding", None) or "").lower().replace("-", "")
        if encoding != "utf8" and hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    _ensure_utf8_output()
    args = build_parser().parse_args(argv)
    try:
        return args.func(args)
    except KeyboardInterrupt:
        print("\nCancelado.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
