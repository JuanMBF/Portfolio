from ittk import sysinfo
from ittk.models import DiskInfo, Severity


def test_healthy_machine_has_single_ok_finding(system_info):
    findings = sysinfo.analyze(system_info())

    assert [f.severity for f in findings] == [Severity.OK]


def test_disk_thresholds(system_info):
    info = system_info(disks=[DiskInfo("C:\\", 100, 96, 96.0), DiskInfo("D:\\", 100, 88, 88.0)])

    findings = sysinfo.analyze(info)

    assert [(f.severity, f.message) for f in findings] == [
        (Severity.CRITICAL, "Disco C:\\ al 96 %"),
        (Severity.WARNING, "Disco D:\\ al 88 %"),
    ]


def test_memory_and_cpu_pressure(system_info):
    findings = sysinfo.analyze(system_info(memory_percent=97.0, cpu_percent=99.0))

    severities = {f.message.split()[0]: f.severity for f in findings}
    assert severities == {"Memoria": Severity.CRITICAL, "CPU": Severity.WARNING}


def test_long_uptime_suggests_restart(system_info):
    findings = sysinfo.analyze(system_info(uptime_hours=24 * 45))

    assert "45 días" in findings[0].message
    assert "Reiniciar" in findings[0].advice


def test_missing_ip_is_critical(system_info):
    findings = sysinfo.analyze(system_info(ip_addresses={}))

    assert findings[0].severity is Severity.CRITICAL


def test_collect_returns_real_data():
    info = sysinfo.collect()

    assert info.hostname
    assert info.cpu_cores > 0
    assert 0 <= info.memory_percent <= 100
