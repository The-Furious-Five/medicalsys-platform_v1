# Evidencia inicial de seguridad — VULN-01

Fecha de escaneo: 2026-10-12. Herramientas: Trivy 0.69.3 (severidades HIGH y CRITICAL, scanner de vulnerabilidades) y OWASP ZAP Baseline (imagen `ghcr.io/zaproxy/zaproxy:stable`). La referencia solicitada fue `origin/develop` (`baef748f948e7ab9f7048d95fa3b00764c0d1a4a`); las imágenes se construyeron en un directorio temporal y se etiquetaron `medicalsys/*:develop-baseline`, sin sustituir las imágenes corregidas `:local`.

## Trivy filesystem

El análisis de los 14 POM del árbol limpio de `origin/develop` encontró 3 vulnerabilidades: 2 críticas y 1 alta, todas en `org.bouncycastle:bcprov-jdk18on` 1.78.1.

| CVE | Severidad | Paquete y versión |
|---|---|---|
| CVE-2025-14813 | Crítica | bcprov-jdk18on 1.78.1 |
| CVE-2026-8763 | Crítica | bcprov-jdk18on 1.78.1 |
| CVE-2026-13506 | Alta | bcprov-jdk18on 1.78.1 |

Reporte: `trivy-fs-antes-develop-20261012.json`.

## Trivy de imágenes

Se construyeron y analizaron las 14 imágenes de `origin/develop` con `--scanners vuln --severity HIGH,CRITICAL`. Resultado agregado: **43 críticas y 96 altas (139 ocurrencias)**.

| Hallazgo principal | Severidad | Ocurrencias |
|---|---:|---:|
| Tomcat 11.0.24 (CVE-2026-65182, CVE-2026-65905, CVE-2026-68525) | Crítica | 39 |
| Bouncy Castle 1.78.1/1.81.1/1.84 (CVE-2025-14813, CVE-2026-8763, CVE-2026-13506) | Crítica/Alta | 7 |
| Jackson 2.21.5 y 3.1.5 (CVE-2026-68497, CVE-2026-89407, CVE-2026-89425, CVE-2026-91776, CVE-2026-91777) | Alta | 75 |
| libssl3 Ubuntu 22.04 3.0.2-0ubuntu1.29 (CVE-2026-84782) | Alta | 14 |
| lz4-java 1.10.1 (CVE-2026-106451) | Alta | 4 |

Los conteos son ocurrencias por imagen/paquete, no CVE únicos. Los 14 JSON `medicalsys-*-develop-baseline.json` contienen el detalle individual.

## OWASP ZAP Baseline

Se ejecutó ZAP Baseline contra el gateway de `origin/develop` en `http://host.docker.internal:8081/actuator/health`. El gateway de referencia se inició temporalmente como contenedor independiente; la exploración cubrió 8 URLs, principalmente Actuator. Resultado: **0 FAIL**, 2 familias de advertencias: 4 ocurrencias de `Cross-Origin-Resource-Policy Header Missing or Invalid` (bajo) y 2 de `Non-Storable Content` (informativa).

Reportes: `zap-baseline-antes-20261012.json` y `zap-baseline-antes-20261012.html`. No se encontró especificación OpenAPI en `origin/develop`, por lo que no se ejecutó API scan. La cobertura ZAP no representa una evaluación autenticada de todos los microservicios.

## Cómo reproducir y resumir

Los comandos exactos están en `../escanear.sh` y en el procedimiento PR-04. El resumen por severidad de esta carpeta se obtiene con:

```
python docs/seguridad/escaneos/resumen.py docs/seguridad/escaneos/antes
```
