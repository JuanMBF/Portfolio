import socket

import pytest

from ittk import network
from ittk.models import Severity


@pytest.mark.parametrize(
    ("flags", "severity", "text"),
    [
        ({}, Severity.OK, "correcta"),
        ({"dns": False, "ip": False, "https": False, "http": False}, Severity.CRITICAL, "Sin conexión"),
        ({"dns": False, "https": False, "http": False}, Severity.CRITICAL, "DNS"),
        ({"https": False, "http": False}, Severity.WARNING, "no puede navegar"),
        ({"ip": False, "http": False}, Severity.WARNING, "proxy"),
        ({"ip": False}, Severity.WARNING, "parcial"),
    ],
)
def test_diagnose(checks, flags, severity, text):
    finding = network.diagnose(checks(**flags))

    assert finding.severity is severity
    assert text in finding.message + finding.advice


def test_parse_ports_accepts_lists_ranges_and_removes_duplicates():
    assert network.parse_ports("443, 22,80,22,8000-8002") == [22, 80, 443, 8000, 8001, 8002]


@pytest.mark.parametrize("text", ["0", "70000", "90-80", "1-5000", "http"])
def test_parse_ports_rejects_invalid_input(text):
    with pytest.raises(ValueError):
        network.parse_ports(text)


@pytest.mark.parametrize("text", ["http", "22,http", "80-", "-80", "80-90-100", "²"])
def test_parse_ports_names_the_part_that_is_not_a_port(text):
    with pytest.raises(ValueError, match="Puerto inválido: '" + text.split(",")[-1]):
        network.parse_ports(text)


@pytest.mark.parametrize("text", ["", " ", ",", " , "])
def test_parse_ports_rejects_an_empty_list(text):
    with pytest.raises(ValueError, match="ningún puerto"):
        network.parse_ports(text)


def test_check_tcp_reports_open_port(monkeypatch):
    class FakeSocket:
        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

    monkeypatch.setattr(socket, "create_connection", lambda address, timeout: FakeSocket())

    result = network.check_tcp("intranet", 443, name="HTTPS")

    assert result.ok
    assert result.target == "intranet:443"
    assert result.latency_ms is not None


@pytest.mark.parametrize(
    ("error", "detail"),
    [
        (ConnectionRefusedError(), "puerto está cerrado"),
        (socket.timeout(), "firewall"),
        (socket.gaierror(), "resolver el nombre"),
    ],
)
def test_check_tcp_explains_failures(monkeypatch, error, detail):
    def fail(address, timeout):
        raise error

    monkeypatch.setattr(socket, "create_connection", fail)

    result = network.check_tcp("intranet", 22)

    assert not result.ok
    assert detail in result.detail


def test_check_dns_failure(monkeypatch):
    def fail(host):
        raise socket.gaierror("Name or service not known")

    monkeypatch.setattr(socket, "gethostbyname", fail)

    assert not network.check_dns("no-existe.local").ok


def test_check_ports_labels_known_services(monkeypatch):
    monkeypatch.setattr(network, "check_tcp", lambda host, port, timeout, name: network.CheckResult(name, f"{host}:{port}", port == 443))

    results = network.check_ports("servidor", [443, 3389, 12345])

    assert [r.name for r in results] == ["HTTPS", "Escritorio remoto", "TCP"]
    assert [r.ok for r in results] == [True, False, False]
