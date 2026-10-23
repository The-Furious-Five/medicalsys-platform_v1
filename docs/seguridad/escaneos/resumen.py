"""Resumen por severidad de una carpeta de reportes de escaneo (PR-04, Anexo A).

Lee los JSON de Trivy (filesystem e imagenes) y de ZAP de la carpeta indicada y cuenta los
hallazgos. Las cifras del informe (seccion 13) deben salir de aqui, no de una lectura manual.

Uso: python docs/seguridad/escaneos/resumen.py docs/seguridad/escaneos/<antes|despues>
"""
import json
import sys
from collections import Counter
from pathlib import Path


def trivy(path):
    """Devuelve (Counter por severidad, Counter por (paquete, version, severidad)) o None si no es Trivy."""
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or "ArtifactType" not in data:
        return None
    severities, packages = Counter(), Counter()
    for result in data.get("Results") or []:
        for vuln in result.get("Vulnerabilities") or []:
            severities[vuln["Severity"]] += 1
            packages[(vuln["PkgName"].split(":")[-1], vuln["InstalledVersion"], vuln["Severity"])] += 1
    return data["ArtifactType"], severities, packages


def zap(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or "site" not in data:
        return None
    return [(alert["riskdesc"].split(" ")[0], alert["name"], int(alert["count"]))
            for site in data["site"] for alert in site.get("alerts", [])]


def summarize(folder):
    """Totales de la carpeta: {'fs': Counter, 'image': Counter, 'packages': Counter, 'zap': [...], 'images': n}."""
    totals = {"fs": Counter(), "image": Counter(), "packages": Counter(), "zap": [], "images": 0, "files": []}
    for path in sorted(Path(folder).glob("*.json")):
        report = trivy(path)
        if report:
            kind, severities, packages = report
            key = "image" if kind == "container_image" else "fs"
            totals[key] += severities
            totals["images"] += key == "image"
            if key == "image":
                totals["packages"] += packages
            totals["files"].append((path.name, key, dict(severities)))
            continue
        alerts = zap(path)
        if alerts is not None:
            totals["zap"] = alerts
    return totals


if __name__ == "__main__":
    totals = summarize(sys.argv[1])
    for name, kind, severities in totals["files"]:
        print(f"{name:60} {kind:6} CRITICAL={severities.get('CRITICAL', 0):3} HIGH={severities.get('HIGH', 0):3}")
    print()
    print(f"Trivy filesystem  CRITICAL={totals['fs']['CRITICAL']}  HIGH={totals['fs']['HIGH']}")
    print(f"Trivy imagenes ({totals['images']})  CRITICAL={totals['image']['CRITICAL']}  HIGH={totals['image']['HIGH']}")
    for (package, version, severity), count in sorted(totals["packages"].items(), key=lambda item: -item[1]):
        print(f"   {count:3}  {severity:8} {package} {version}")
    print("ZAP:")
    for risk, name, count in totals["zap"]:
        print(f"   {count:3}  {risk:13} {name}")
