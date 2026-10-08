import io
import json
import urllib.error

import pytest

from ittk import helpdesk
from ittk.helpdesk import HelpDeskClient, HelpDeskError


class FakeResponse(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


@pytest.fixture
def requests(monkeypatch):
    """Intercepta urlopen y devuelve respuestas encoladas, guardando cada request enviada."""
    sent, queue = [], []

    def fake_urlopen(request, timeout):
        sent.append(request)
        response = queue.pop(0)
        if isinstance(response, Exception):
            raise response
        return FakeResponse(json.dumps(response).encode())

    monkeypatch.setattr(helpdesk.urllib.request, "urlopen", fake_urlopen)
    return sent, queue


def test_login_and_create_ticket_with_bearer_token(requests):
    sent, queue = requests
    queue += [
        {"token": "abc123", "user": {"fullName": "Sofía Ramírez"}},
        {"id": 17, "slaDueAt": "2026-03-02T18:00:00Z"},
    ]
    client = HelpDeskClient("http://localhost:8080/")

    user = client.login("sofia@helpdesk.dev", "User123!")
    ticket = client.create_ticket("Sin internet", "diagnóstico", "NETWORK", "HIGH")

    assert user["fullName"] == "Sofía Ramírez"
    assert ticket["id"] == 17
    assert sent[0].full_url == "http://localhost:8080/api/auth/login"
    assert sent[1].get_header("Authorization") == "Bearer abc123"
    assert json.loads(sent[1].data) == {
        "title": "Sin internet", "description": "diagnóstico", "category": "NETWORK", "priority": "HIGH",
    }


def test_long_descriptions_are_trimmed_to_api_limit(requests):
    sent, queue = requests
    queue.append({"id": 1})

    HelpDeskClient("http://api").create_ticket("t", "x" * 10_000, "OTHER", "LOW")

    description = json.loads(sent[0].data)["description"]
    assert len(description) <= helpdesk.MAX_DESCRIPTION
    assert description.endswith("(reporte recortado)")


def test_api_errors_use_problem_detail(requests):
    _, queue = requests
    body = io.BytesIO(json.dumps({"title": "Credenciales inválidas", "detail": "Email o contraseña incorrectos"}).encode())
    queue.append(urllib.error.HTTPError("http://api/api/auth/login", 401, "Unauthorized", {}, body))

    with pytest.raises(HelpDeskError, match="Email o contraseña incorrectos"):
        HelpDeskClient("http://api").login("a@b.c", "mal")


def test_connection_errors_are_explained(requests):
    _, queue = requests
    queue.append(urllib.error.URLError("Connection refused"))

    with pytest.raises(HelpDeskError, match="No se pudo conectar con http://api"):
        HelpDeskClient("http://api").login("a@b.c", "x")
