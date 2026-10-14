import json

import pytest

from ittk import cli, network, sysinfo
from ittk.models import Severity


@pytest.fixture
def offline(monkeypatch, system_info, checks):
    """Reemplaza los colectores reales por datos fijos: los tests no dependen del equipo ni de la red."""
    state = {"info": system_info(), "checks": checks()}
    monkeypatch.setattr(sysinfo, "collect", lambda: state["info"])
    monkeypatch.setattr(network, "run_connectivity_checks", lambda: state["checks"])
    return state


def test_netcheck_exit_code_reflects_diagnosis(offline, checks, capsys):
    assert cli.main(["netcheck"]) == 0

    offline["checks"] = checks(dns=False, ip=False, https=False, http=False)
    assert cli.main(["netcheck"]) == 2
    assert "Sin conexión a internet" in capsys.readouterr().out


def test_sysinfo_json(offline, capsys):
    assert cli.main(["sysinfo", "--json"]) == 0

    data = json.loads(capsys.readouterr().out)
    assert data["system"]["hostname"] == "pc-aula-204"
    assert data["findings"][0]["severity"] == "ok"


def test_report_writes_html_file(offline, tmp_path):
    output = tmp_path / "reporte.html"

    assert cli.main(["report", "--format", "html", "-o", str(output)]) == 0
    assert "Diagnóstico de pc-aula-204" in output.read_text(encoding="utf-8")


@pytest.mark.parametrize("target", ["no-existe/reporte.md", "."])
def test_report_to_an_unwritable_path_is_an_error_not_a_crash(offline, tmp_path, capsys, target):
    # Una carpeta inexistente o una carpeta en lugar de un archivo terminaban en un traceback de OSError.
    output = tmp_path / target

    assert cli.main(["report", "-o", str(output)]) == 2
    assert f"No se pudo guardar el reporte en {output}" in capsys.readouterr().err


def test_report_hides_ok_findings_when_there_are_problems(offline, system_info):
    offline["info"] = system_info(memory_percent=97.0)

    report = cli.build_report()

    assert all(f.severity is not Severity.OK for f in report.findings)
    assert report.worst_severity is Severity.CRITICAL


@pytest.mark.parametrize(
    ("net_ok", "memory", "category", "priority"),
    [
        (True, 30.0, "OTHER", "LOW"),
        (True, 97.0, "HARDWARE", "HIGH"),
        (False, 30.0, "NETWORK", "HIGH"),
    ],
)
def test_ticket_suggestions(offline, system_info, checks, net_ok, memory, category, priority):
    offline["info"] = system_info(memory_percent=memory)
    offline["checks"] = checks() if net_ok else checks(dns=False, ip=False, https=False, http=False)

    report = cli.build_report()

    assert cli.suggest_category(report) == category
    assert cli.suggest_priority(report) == priority


def test_ticket_command_sends_report(offline, monkeypatch, capsys):
    created = {}

    class FakeClient:
        def __init__(self, api):
            created["api"] = api

        def login(self, email, password):
            created["login"] = (email, password)
            return {"fullName": "Sofía Ramírez"}

        def create_ticket(self, title, description, category, priority):
            created.update(title=title, description=description, category=category, priority=priority)
            return {"id": 42, "slaDueAt": "2026-03-05T10:00:00Z"}

    monkeypatch.setattr(cli, "HelpDeskClient", FakeClient)
    monkeypatch.setenv("ITTK_PASSWORD", "User123!")

    code = cli.main(["ticket", "--api", "http://hd", "--email", "sofia@helpdesk.dev", "--title", "PC lenta",
                     "--description", "Tarda mucho en iniciar"])

    assert code == 0
    assert created["login"] == ("sofia@helpdesk.dev", "User123!")
    assert created["description"].startswith("Tarda mucho en iniciar\n\n## Diagnóstico de pc-aula-204")
    assert "Ticket #42 creado" in capsys.readouterr().out


def test_ports_rejects_bad_input(capsys):
    assert cli.main(["ports", "localhost", "--ports", "99999"]) == 2
    assert "fuera de rango" in capsys.readouterr().err


def test_ports_without_any_port_is_an_error_not_a_crash(capsys):
    # Antes llegaba a imprimir la tabla vacía y fallaba con "max() iterable argument is empty".
    assert cli.main(["ports", "localhost", "--ports", ","]) == 2
    assert "ningún puerto" in capsys.readouterr().err


def test_format_due_handles_api_timestamps():
    assert cli.format_due("2026-10-09T02:49:41.542147213Z") == "09/10/2026 02:49 UTC"
    assert cli.format_due("no-es-fecha") == "no-es-fecha"
