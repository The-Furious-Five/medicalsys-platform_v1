"""Grafico por severidad de los hallazgos, antes y despues (seccion 13 del informe).

Toma las cifras de los reportes guardados mediante resumen.py, de modo que el grafico no puede
desviarse de la evidencia. Requiere matplotlib.

Uso: python docs/seguridad/escaneos/grafico.py
Genera docs/seguridad/escaneos/hallazgos-por-severidad.png
"""
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

from resumen import summarize

HERE = Path(__file__).resolve().parent
before, after = summarize(HERE / "antes"), summarize(HERE / "despues")


def zap_count(totals, risk):
    return sum(count for level, _, count in totals["zap"] if level == risk)


groups = [
    ("Trivy imágenes\nCrítica", before["image"]["CRITICAL"], after["image"]["CRITICAL"]),
    ("Trivy imágenes\nAlta", before["image"]["HIGH"], after["image"]["HIGH"]),
    ("Trivy pom.xml\nCrítica", before["fs"]["CRITICAL"], after["fs"]["CRITICAL"]),
    ("Trivy pom.xml\nAlta", before["fs"]["HIGH"], after["fs"]["HIGH"]),
    ("ZAP\nBaja", zap_count(before, "Low"), zap_count(after, "Low")),
    ("ZAP\nInformativa", zap_count(before, "Informational"), zap_count(after, "Informational")),
]

positions = range(len(groups))
width = 0.38
figure, axis = plt.subplots(figsize=(9, 4.5))
bars_before = axis.bar([p - width / 2 for p in positions], [g[1] for g in groups], width,
                       label="Antes (develop)", color="#b5473c")
bars_after = axis.bar([p + width / 2 for p in positions], [g[2] for g in groups], width,
                      label="Después (corregido)", color="#2f7d6b")
axis.bar_label(bars_before, padding=2, fontsize=9)
axis.bar_label(bars_after, padding=2, fontsize=9)
axis.set_xticks(list(positions))
axis.set_xticklabels([g[0] for g in groups], fontsize=9)
axis.set_ylabel("Hallazgos (ocurrencias)")
axis.set_title("MedicalSys — hallazgos por herramienta y severidad, antes y después")
axis.spines[["top", "right"]].set_visible(False)
axis.legend(frameon=False)
figure.tight_layout()
figure.savefig(HERE / "hallazgos-por-severidad.png", dpi=160)
print("hallazgos-por-severidad.png generado:", [(g[0].replace("\n", " "), g[1], g[2]) for g in groups])
