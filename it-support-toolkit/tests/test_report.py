import json

from ittk.models import Finding, Severity
from ittk.report import Report, to_html, to_json, to_markdown


def make_report(system_info, checks, fixed_time, findings=None):
    return Report(
        system=system_info(),
        checks=checks(),
        findings=findings or [Finding(Severity.OK, "Conectividad a internet correcta")],
        generated_at=fixed_time,
    )


def test_worst_severity(system_info, checks, fixed_time):
    report = make_report(system_info, checks, fixed_time, [
        Finding(Severity.WARNING, "a"), Finding(Severity.CRITICAL, "b"), Finding(Severity.OK, "c"),
    ])

    assert report.worst_severity is Severity.CRITICAL


def test_markdown_contains_findings_system_and_checks(system_info, checks, fixed_time):
    md = to_markdown(make_report(system_info, checks, fixed_time))

    assert md.startswith("## Diagnóstico de pc-aula-204")
    assert "02/03/2026 10:30" in md
    assert "- ✔ **Conectividad a internet correcta**" in md
    assert "- Disco C:\\: 200.0 / 476.0 GB (42 %)" in md
    assert "| DNS | www.google.com | ✔ ok | 12 ms |" in md


def test_json_is_valid_and_has_status(system_info, checks, fixed_time):
    data = json.loads(to_json(make_report(system_info, checks, fixed_time)))

    assert data["status"] == "ok"
    assert data["system"]["hostname"] == "pc-aula-204"
    assert len(data["checks"]) == 4
    assert data["findings"][0]["severity"] == "ok"


def test_html_escapes_untrusted_values(system_info, checks, fixed_time):
    report = make_report(system_info, checks, fixed_time)
    report.system.hostname = "<script>alert(1)</script>"

    page = to_html(report)

    assert "<script>alert(1)</script>" not in page
    assert "&lt;script&gt;" in page
    assert '<html lang="es">' in page
