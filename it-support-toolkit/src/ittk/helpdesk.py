"""Cliente mínimo de la API de HelpDesk Pro para abrir un ticket con el diagnóstico adjunto."""

from __future__ import annotations

import json
import urllib.error
import urllib.request

MAX_DESCRIPTION = 4000


class HelpDeskError(Exception):
    pass


class HelpDeskClient:
    def __init__(self, base_url: str, timeout: float = 10.0):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.token: str | None = None

    def _request(self, method: str, path: str, body: dict) -> dict:
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        request = urllib.request.Request(
            f"{self.base_url}{path}", data=json.dumps(body).encode(), headers=headers, method=method
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:  # noqa: S310
                return json.loads(response.read().decode())
        except urllib.error.HTTPError as error:
            raise HelpDeskError(self._problem_detail(error)) from error
        except urllib.error.URLError as error:
            raise HelpDeskError(f"No se pudo conectar con {self.base_url}: {error.reason}") from error

    @staticmethod
    def _problem_detail(error: urllib.error.HTTPError) -> str:
        """La API responde errores en formato RFC 7807: usamos su 'detail' si está."""
        try:
            problem = json.loads(error.read().decode())
            return problem.get("detail") or problem.get("title") or f"HTTP {error.code}"
        except (ValueError, AttributeError):
            return f"HTTP {error.code}"

    def login(self, email: str, password: str) -> dict:
        response = self._request("POST", "/api/auth/login", {"email": email, "password": password})
        self.token = response["token"]
        return response["user"]

    def create_ticket(self, title: str, description: str, category: str, priority: str) -> dict:
        if len(description) > MAX_DESCRIPTION:
            description = description[: MAX_DESCRIPTION - 30].rstrip() + "\n\n…(reporte recortado)"
        return self._request("POST", "/api/tickets", {
            "title": title,
            "description": description,
            "category": category,
            "priority": priority,
        })
