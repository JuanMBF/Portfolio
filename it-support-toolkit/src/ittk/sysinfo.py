"""Relevamiento del equipo: sistema operativo, CPU, memoria, discos y red."""

from __future__ import annotations

import platform
import socket
import time

import psutil

from .models import DiskInfo, Finding, Severity, SystemInfo

GB = 1024**3

# Umbrales a partir de los cuales un recurso se considera en riesgo.
DISK_WARNING, DISK_CRITICAL = 85.0, 95.0
MEMORY_WARNING, MEMORY_CRITICAL = 85.0, 95.0
CPU_WARNING = 90.0
UPTIME_WARNING_DAYS = 30

# Sistemas de archivos virtuales o de solo lectura que no interesan en un diagnóstico.
IGNORED_FS = {"squashfs", "tmpfs", "devtmpfs", "overlay", "iso9660", "proc", "sysfs"}
# Particiones chicas (EFI, recuperación) suelen estar casi llenas por diseño y generarían falsas alarmas.
MIN_DISK_GB = 1.0


def _cpu_model() -> str:
    model = platform.processor()
    if not model:
        try:
            with open("/proc/cpuinfo", encoding="utf-8") as cpuinfo:
                for line in cpuinfo:
                    if line.startswith("model name"):
                        return line.split(":", 1)[1].strip()
        except OSError:
            pass
    return model or platform.machine()


def _disks() -> list[DiskInfo]:
    disks = []
    seen_devices = set()
    for partition in psutil.disk_partitions(all=False):
        # El mismo dispositivo puede estar montado en varios lugares: se informa una sola vez.
        if partition.fstype in IGNORED_FS or partition.device in seen_devices:
            continue
        try:
            usage = psutil.disk_usage(partition.mountpoint)
        except (PermissionError, OSError):
            continue  # Lectoras vacías, unidades de red desconectadas, etc.
        if usage.total / GB < MIN_DISK_GB:
            continue
        seen_devices.add(partition.device)
        disks.append(DiskInfo(partition.mountpoint, round(usage.total / GB, 1), round(usage.used / GB, 1), usage.percent))
    return disks


def _ip_addresses() -> dict[str, list[str]]:
    result = {}
    for interface, addresses in psutil.net_if_addrs().items():
        ips = [a.address for a in addresses if a.family == socket.AF_INET and not a.address.startswith("127.")]
        if ips:
            result[interface] = ips
    return result


def collect() -> SystemInfo:
    memory = psutil.virtual_memory()
    return SystemInfo(
        hostname=socket.gethostname(),
        os=f"{platform.system()} {platform.release()}",
        architecture=platform.machine(),
        python=platform.python_version(),
        cpu_model=_cpu_model(),
        cpu_cores=psutil.cpu_count(logical=True) or 0,
        cpu_percent=psutil.cpu_percent(interval=0.5),
        memory_total_gb=round(memory.total / GB, 1),
        memory_used_gb=round((memory.total - memory.available) / GB, 1),
        memory_percent=memory.percent,
        uptime_hours=round((time.time() - psutil.boot_time()) / 3600, 1),
        disks=_disks(),
        ip_addresses=_ip_addresses(),
    )


def analyze(info: SystemInfo) -> list[Finding]:
    """Convierte las métricas en hallazgos accionables para el técnico."""
    findings = []

    for disk in info.disks:
        if disk.percent >= DISK_CRITICAL:
            findings.append(Finding(Severity.CRITICAL, f"Disco {disk.mountpoint} al {disk.percent:.0f} %",
                                    "Liberar espacio: temporales, papelera, descargas o ampliar el disco."))
        elif disk.percent >= DISK_WARNING:
            findings.append(Finding(Severity.WARNING, f"Disco {disk.mountpoint} al {disk.percent:.0f} %",
                                    "Planificar limpieza antes de que se llene."))

    if info.memory_percent >= MEMORY_CRITICAL:
        findings.append(Finding(Severity.CRITICAL, f"Memoria RAM al {info.memory_percent:.0f} %",
                                "Revisar qué procesos consumen más memoria o evaluar ampliar la RAM."))
    elif info.memory_percent >= MEMORY_WARNING:
        findings.append(Finding(Severity.WARNING, f"Memoria RAM al {info.memory_percent:.0f} %",
                                "Cerrar aplicaciones que no se estén usando."))

    if info.cpu_percent >= CPU_WARNING:
        findings.append(Finding(Severity.WARNING, f"CPU al {info.cpu_percent:.0f} %",
                                "Buscar el proceso que la está usando en el Administrador de tareas."))

    if info.uptime_hours >= UPTIME_WARNING_DAYS * 24:
        findings.append(Finding(Severity.WARNING, f"El equipo no se reinicia hace {info.uptime_hours / 24:.0f} días",
                                "Reiniciar para aplicar actualizaciones pendientes."))

    if not info.ip_addresses:
        findings.append(Finding(Severity.CRITICAL, "El equipo no tiene dirección IP",
                                "Revisar cable de red o conexión WiFi."))

    if not findings:
        findings.append(Finding(Severity.OK, "Recursos del equipo dentro de valores normales"))
    return findings
