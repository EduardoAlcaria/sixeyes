"""Host vitals for the dashboard: CPU, RAM, GPU, network and per-drive I/O and health.

Rates come from a sampler thread diffing psutil counters every 2 s, so a request never
waits on a measurement. Disk health and GPU come from external tools (PowerShell storage
cmdlets, nvidia-smi) that are slow or absent, so they are refreshed rarely in the
background and every field they feed is optional.
"""
import json
import os
import shutil
import subprocess
import threading
import time

import psutil

_STARTED_AT = time.time()
_state: dict = {}
_disk_meta: dict = {"partitions": {}, "physical": {}}
_gpu: dict | None = None

_NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)


def _powershell_json(script: str, timeout: int = 30):
    try:
        out = subprocess.run(
            ["powershell", "-NoProfile", "-NonInteractive", "-Command",
             script + " | ConvertTo-Json -Depth 3 -Compress"],
            capture_output=True, text=True, timeout=timeout, creationflags=_NO_WINDOW,
        ).stdout.strip()
        if not out:
            return []
        data = json.loads(out)
        return data if isinstance(data, list) else [data]
    except Exception as e:
        print(f"[hostinfo] powershell failed: {e}")
        return []


def _refresh_disk_meta() -> None:
    """Drive letter -> physical disk, plus each disk's model, health and temperature.

    Reliability counters (temperature, wear) need admin on most machines; without it
    they come back empty and the UI shows health alone."""
    if os.name != "nt":
        return
    parts = _powershell_json(
        "Get-Partition | Where-Object DriveLetter | Select-Object DriveLetter,DiskNumber")
    disks = _powershell_json(
        "Get-PhysicalDisk | Select-Object DeviceId,FriendlyName,MediaType,HealthStatus,BusType")
    counters = _powershell_json(
        "Get-PhysicalDisk | Get-StorageReliabilityCounter -ErrorAction SilentlyContinue | "
        "Select-Object DeviceId,Temperature,Wear")
    temps = {str(c.get("DeviceId")): c for c in counters}

    physical = {}
    for d in disks:
        dev = str(d.get("DeviceId"))
        rc = temps.get(dev, {})
        physical[dev] = {
            "model": (d.get("FriendlyName") or "").strip(),
            "mediaType": str(d.get("MediaType") or ""),
            "bus": str(d.get("BusType") or ""),
            "health": str(d.get("HealthStatus") or ""),
            "temperature": rc.get("Temperature") or None,
            "wear": rc.get("Wear"),
        }
    _disk_meta["partitions"] = {
        f"{p['DriveLetter']}:": str(p.get("DiskNumber")) for p in parts if p.get("DriveLetter")
    }
    _disk_meta["physical"] = physical


def _refresh_gpu() -> None:
    global _gpu
    exe = shutil.which("nvidia-smi")
    if not exe:
        _gpu = None
        return
    try:
        line = subprocess.run(
            [exe, "--query-gpu=name,utilization.gpu,temperature.gpu,memory.used,memory.total",
             "--format=csv,noheader,nounits"],
            capture_output=True, text=True, timeout=5, creationflags=_NO_WINDOW,
        ).stdout.strip().splitlines()[0]
        name, util, temp, used, total = [x.strip() for x in line.split(",")]
        _gpu = {"name": name, "usage": float(util), "temperature": float(temp),
                "memUsedMb": float(used), "memTotalMb": float(total)}
    except Exception:
        _gpu = None


def _slow_loop() -> None:
    while True:
        _refresh_disk_meta()
        time.sleep(300)


def _gpu_loop() -> None:
    while True:
        _refresh_gpu()
        time.sleep(10)


def _sampler() -> None:
    prev_t = time.time()
    prev_net = psutil.net_io_counters()
    prev_disk = psutil.disk_io_counters(perdisk=True) or {}
    psutil.cpu_percent(None)
    while True:
        time.sleep(2)
        now = time.time()
        dt = max(now - prev_t, 0.001)
        net = psutil.net_io_counters()
        disk = psutil.disk_io_counters(perdisk=True) or {}
        rates = {}
        for name, c in disk.items():
            p = prev_disk.get(name)
            if p is not None:
                rates[name] = {
                    "read": max(c.read_bytes - p.read_bytes, 0) / dt,
                    "write": max(c.write_bytes - p.write_bytes, 0) / dt,
                }
        vm = psutil.virtual_memory()
        _state.update({
            "cpu": psutil.cpu_percent(None),
            "cpuCount": psutil.cpu_count(),
            "ramUsed": vm.used,
            "ramTotal": vm.total,
            "netRx": max(net.bytes_recv - prev_net.bytes_recv, 0) / dt,
            "netTx": max(net.bytes_sent - prev_net.bytes_sent, 0) / dt,
            "diskRates": rates,
        })
        prev_t, prev_net, prev_disk = now, net, disk


threading.Thread(target=_sampler, daemon=True).start()
threading.Thread(target=_slow_loop, daemon=True).start()
threading.Thread(target=_gpu_loop, daemon=True).start()


def _drives() -> list[dict]:
    rates = _state.get("diskRates", {})
    out = []
    for part in psutil.disk_partitions(all=False):
        mount = part.mountpoint
        if not os.path.isdir(mount):
            continue
        try:
            usage = psutil.disk_usage(mount)
        except OSError:
            continue
        letter = os.path.splitdrive(mount)[0] or mount
        disk_no = _disk_meta["partitions"].get(letter)
        phys = _disk_meta["physical"].get(disk_no, {}) if disk_no is not None else {}
        rate = rates.get(f"PhysicalDrive{disk_no}", {}) if disk_no is not None else {}
        out.append({
            "drive": letter,
            "path": mount,
            "fs": part.fstype,
            "total": usage.total,
            "used": usage.used,
            "free": usage.free,
            "readBps": round(rate.get("read", 0)),
            "writeBps": round(rate.get("write", 0)),
            **phys,
        })
    return out


def vitals() -> dict:
    from app.services import torrent_service

    return {
        "cpu": _state.get("cpu"),
        "cpuCount": _state.get("cpuCount"),
        "ramUsed": _state.get("ramUsed"),
        "ramTotal": _state.get("ramTotal"),
        "netRx": round(_state.get("netRx", 0)),
        "netTx": round(_state.get("netTx", 0)),
        "gpu": _gpu,
        "drives": _drives(),
        "agentUptime": int(time.time() - _STARTED_AT),
        "systemUptime": int(time.time() - psutil.boot_time()),
        "dhtNodes": torrent_service.dht_nodes(),
    }
