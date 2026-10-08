from datetime import datetime, timezone

import pytest

from ittk.models import CheckResult, DiskInfo, SystemInfo


@pytest.fixture
def system_info():
    def make(**overrides):
        data = dict(
            hostname="pc-aula-204",
            os="Windows 11",
            architecture="AMD64",
            python="3.12.4",
            cpu_model="Intel Core i5",
            cpu_cores=8,
            cpu_percent=12.0,
            memory_total_gb=16.0,
            memory_used_gb=6.0,
            memory_percent=37.5,
            uptime_hours=20.0,
            disks=[DiskInfo("C:\\", 476.0, 200.0, 42.0)],
            ip_addresses={"Wi-Fi": ["192.168.0.15"]},
        )
        data.update(overrides)
        return SystemInfo(**data)

    return make


@pytest.fixture
def checks():
    def make(dns=True, ip=True, https=True, http=True):
        return [
            CheckResult("DNS", "www.google.com", dns, 12.0 if dns else None, "ok" if dns else "no resuelve"),
            CheckResult("Internet (IP)", "8.8.8.8:53", ip, 20.0 if ip else None, ""),
            CheckResult("HTTPS", "www.google.com:443", https, 25.0 if https else None, ""),
            CheckResult("HTTP", "https://www.google.com/generate_204", http, 80.0 if http else None, ""),
        ]

    return make


@pytest.fixture
def fixed_time():
    return datetime(2026, 3, 2, 10, 30, tzinfo=timezone.utc)
