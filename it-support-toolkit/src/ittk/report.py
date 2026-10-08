"""Genera el reporte de diagnóstico en Markdown, HTML o JSON para adjuntar a un ticket."""

from __future__ import annotations

import html
import json
from dataclasses import asdict, dataclass, field
from datetime import datetime

from .models import SEVERITY_ICONS, CheckResult, Finding, Severity, SystemInfo


@dataclass
class Report:
    system: SystemInfo
    checks: list[CheckResult]
    findings: list[Finding]
    generated_at: datetime = field(default_factory=lambda: datetime.now().astimezone())

    @property
    def worst_severity(self) -> Severity:
        order = [Severity.OK, Severity.WARNING, Severity.CRITICAL]
        return max((f.severity for f in self.findings), key=order.index, default=Severity.OK)


def _latency(check: CheckResult) -> str:
    return f"{check.latency_ms:.0f} ms" if check.latency_ms is not None else "-"


def to_markdown(report: Report) -> str:
    s = report.system
    lines = [
        f"## Diagnóstico de {s.hostname}",
        f"Generado el {report.generated_at:%d/%m/%Y %H:%M} con IT Support Toolkit.",
        "",
        "### Hallazgos",
        *(f"- {SEVERITY_ICONS[f.severity]} **{f.message}**" + (f": {f.advice}" if f.advice else "") for f in report.findings),
        "",
        "### Equipo",
        f"- Sistema: {s.os} ({s.architecture})",
        f"- CPU: {s.cpu_model}, {s.cpu_cores} núcleos, uso {s.cpu_percent:.0f} %",
        f"- Memoria: {s.memory_used_gb} / {s.memory_total_gb} GB ({s.memory_percent:.0f} %)",
        f"- Encendido hace: {s.uptime_hours:.1f} h",
        *(f"- Disco {d.mountpoint}: {d.used_gb} / {d.total_gb} GB ({d.percent:.0f} %)" for d in s.disks),
        *(f"- IP ({iface}): {', '.join(ips)}" for iface, ips in s.ip_addresses.items()),
        "",
        "### Conectividad",
        "| Prueba | Destino | Resultado | Latencia |",
        "|---|---|---|---|",
        *(f"| {c.name} | {c.target} | {'✔' if c.ok else '✘'} {c.detail} | {_latency(c)} |" for c in report.checks),
    ]
    return "\n".join(lines) + "\n"


def to_json(report: Report) -> str:
    data = {
        "generated_at": report.generated_at.isoformat(),
        "status": report.worst_severity.value,
        "system": report.system.to_dict(),
        "checks": [asdict(c) for c in report.checks],
        "findings": [{**asdict(f), "severity": f.severity.value} for f in report.findings],
    }
    return json.dumps(data, ensure_ascii=False, indent=2)


_HTML_TEMPLATE = """<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Diagnóstico de {hostname}</title>
<style>
  :root {{ --bg:#f5f6fa; --card:#fff; --text:#111827; --muted:#6b7280; --border:#e5e7eb;
          --ok:#0f7a2e; --warning:#9a5b00; --critical:#c42f2f; }}
  @media (prefers-color-scheme: dark) {{
    :root {{ --bg:#0b0f17; --card:#131a24; --text:#e6e9ef; --muted:#8b95a7; --border:#253042;
            --ok:#4ade80; --warning:#fbbf24; --critical:#f87171; }}
  }}
  body {{ margin:0; padding:24px 16px; background:var(--bg); color:var(--text);
         font:15px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif; }}
  main {{ max-width:860px; margin:0 auto; }}
  h1 {{ margin:0 0 4px; font-size:1.6rem; }}
  h2 {{ font-size:1rem; margin:0 0 12px; }}
  .muted {{ color:var(--muted); }}
  .card {{ background:var(--card); border:1px solid var(--border); border-radius:12px; padding:18px; margin-top:16px; }}
  .status {{ display:inline-block; margin-top:8px; padding:2px 10px; border-radius:999px; font-weight:600;
            border:1px solid currentColor; }}
  .ok {{ color:var(--ok); }} .warning {{ color:var(--warning); }} .critical {{ color:var(--critical); }}
  ul {{ margin:0; padding-left:20px; }}
  li + li {{ margin-top:6px; }}
  table {{ width:100%; border-collapse:collapse; }}
  th, td {{ text-align:left; padding:8px; border-bottom:1px solid var(--border); }}
  th {{ color:var(--muted); font-size:.8rem; text-transform:uppercase; }}
  .table-wrap {{ overflow-x:auto; }}
</style>
</head>
<body>
<main>
  <h1>Diagnóstico de {hostname}</h1>
  <div class="muted">Generado el {generated} con IT Support Toolkit</div>
  <span class="status {status}">{status_label}</span>

  <section class="card">
    <h2>Hallazgos</h2>
    <ul>{findings}</ul>
  </section>

  <section class="card">
    <h2>Equipo</h2>
    <ul>{system}</ul>
  </section>

  <section class="card">
    <h2>Conectividad</h2>
    <div class="table-wrap">
      <table>
        <thead><tr><th>Prueba</th><th>Destino</th><th>Resultado</th><th>Latencia</th></tr></thead>
        <tbody>{checks}</tbody>
      </table>
    </div>
  </section>
</main>
</body>
</html>
"""

_STATUS_LABELS = {Severity.OK: "✔ Todo en orden", Severity.WARNING: "⚠ Requiere atención", Severity.CRITICAL: "✘ Problema crítico"}


def to_html(report: Report) -> str:
    e = html.escape
    s = report.system
    findings = "".join(
        f'<li class="{f.severity.value}"><strong>{SEVERITY_ICONS[f.severity]} {e(f.message)}</strong>'
        + (f'<br><span class="muted">{e(f.advice)}</span>' if f.advice else "")
        + "</li>"
        for f in report.findings
    )
    system_items = [
        f"Sistema: {s.os} ({s.architecture})",
        f"CPU: {s.cpu_model}, {s.cpu_cores} núcleos, uso {s.cpu_percent:.0f} %",
        f"Memoria: {s.memory_used_gb} / {s.memory_total_gb} GB ({s.memory_percent:.0f} %)",
        f"Encendido hace {s.uptime_hours:.1f} h",
        *(f"Disco {d.mountpoint}: {d.used_gb} / {d.total_gb} GB ({d.percent:.0f} %)" for d in s.disks),
        *(f"IP ({iface}): {', '.join(ips)}" for iface, ips in s.ip_addresses.items()),
    ]
    checks = "".join(
        f'<tr><td>{e(c.name)}</td><td>{e(c.target)}</td>'
        f'<td class="{"ok" if c.ok else "critical"}">{"✔" if c.ok else "✘"} {e(c.detail)}</td>'
        f"<td>{_latency(c)}</td></tr>"
        for c in report.checks
    )
    return _HTML_TEMPLATE.format(
        hostname=e(s.hostname),
        generated=f"{report.generated_at:%d/%m/%Y %H:%M}",
        status=report.worst_severity.value,
        status_label=_STATUS_LABELS[report.worst_severity],
        findings=findings,
        system="".join(f"<li>{e(item)}</li>" for item in system_items),
        checks=checks,
    )


RENDERERS = {"md": to_markdown, "html": to_html, "json": to_json}
