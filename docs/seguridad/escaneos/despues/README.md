# Evidencia posterior de seguridad — VULN-02

Fecha de escaneo: 2026-10-12. Versión escaneada: rama `feature/OWASP` con `origin/develop` (`baef748`, incluye RBAC-04) fusionado y las correcciones HARD-01 a HARD-04. Las 14 imágenes `medicalsys/*:local` se reconstruyeron desde ese árbol antes de escanear.

Herramientas y configuración, idénticas a las de la línea base (`../antes/README.md`): Trivy 0.69.3 con `--scanners vuln --severity HIGH,CRITICAL`; OWASP ZAP 2.17.0 Baseline (`zap-baseline.py`, araña de 1 minuto, escaneo pasivo) desde `ghcr.io/zaproxy/zaproxy:stable`. Todo se ejecutó con `../escanear.sh despues`.

## Resultados

| Alcance | Reportes | Críticas | Altas |
|---|---:|---:|---:|
| Trivy filesystem: 14 `pom.xml` | `trivy-fs-despues-20261012.json` | 0 | 0 |
| Trivy imágenes: 14 `medicalsys/*:local` | `trivy-image-<servicio>-despues-20261012.json` | 0 | 0 |

ZAP Baseline contra `http://host.docker.internal:8080/actuator/health`: 8 URLs exploradas, **0 FAIL y ninguna alerta en el reporte**. Reportes: `zap-baseline-despues-20261012.json` y `.html`; `zap.yaml` es el plan que ZAP ejecutó.

El resumen se reproduce con:

```
python docs/seguridad/escaneos/resumen.py docs/seguridad/escaneos/despues
```

## Comparación con la línea base

| Hallazgo | Severidad | Antes (develop) | Después | Corrección |
|---|---|---:|---:|---|
| Trivy filesystem: Bouncy Castle 1.78.1 | 2 críticas, 1 alta | 3 | 0 | `bcprov-jdk18on` 1.85 |
| Trivy imágenes: Tomcat 11.0.24 | Crítica | 39 | 0 | `tomcat.version` 11.0.25 |
| Trivy imágenes: Bouncy Castle 1.78.1 / 1.81.1 / 1.84 | 4 críticas, 3 altas | 7 | 0 | `bcprov-jdk18on` 1.85 |
| Trivy imágenes: Jackson 3.1.5 y 2.21.5 | Alta | 75 | 0 | `jackson-bom` 3.1.7 y `jackson-2-bom` 2.21.7 |
| Trivy imágenes: libssl3 3.0.2-0ubuntu1.29 | Alta | 14 | 0 | `apt-get upgrade` en la etapa final del `Dockerfile` |
| Trivy imágenes: lz4-java 1.10.1 | Alta | 4 | 0 | `lz4-java` 1.11.4 |
| ZAP: `Cross-Origin-Resource-Policy` ausente | Baja | 4 | 0 | Cabecera `Cross-Origin-Resource-Policy: same-origin` en el gateway |
| ZAP: contenido no almacenable | Informativa | 2 | 0 | Sin cambio de código dirigido; ver nota |
| ZAP: FAIL | — | 0 | 0 | — |

Los conteos de imágenes son ocurrencias por imagen y paquete, no CVE únicos.

## Notas y limitaciones

- La base de vulnerabilidades de Trivy se actualizó entre ambos escaneos (el reescaneo descargó la del 12/10/2026 por la tarde). El reescaneo usa por tanto una base igual o más reciente que la línea base, lo que juega en contra del resultado, no a favor.
- Alerta informativa de ZAP (regla 10049): la consola de ZAP la lista con 0 instancias en el reescaneo y el reporte no la incluye. No se hizo ningún cambio dirigido a ella y no es una vulnerabilidad; no debe presentarse como una corrección.
- La validez de la comparación de ZAP se comprobó repitiendo la línea base el mismo día y con el mismo comando contra el gateway de `develop`: volvió a dar 4 alertas bajas y 2 informativas, igual que el reporte guardado en `../antes/`.
- ZAP Baseline es un escaneo pasivo y sin autenticar: explora lo que la araña alcanza desde `/actuator/health` (8 URLs). No es una evaluación de la API autenticada. La plataforma no publica especificación OpenAPI, así que no se ejecutó API scan. Los controles de acceso, sesión y firma se verificaron con pruebas dirigidas en `docs/seguridad/evidencias/`.
- Solo se escanearon severidades HIGH y CRITICAL con Trivy; puede haber hallazgos medios y bajos.
- No se ejecutó OWASP Dependency-Check; Trivy filesystem cubre los mismos `pom.xml`.
- Los escaneos se hicieron sobre un entorno local con base de datos desechable, no sobre el despliegue público.
