# Evidencia posterior de seguridad — VULN-02

Fecha de escaneo: 2026-10-12. Herramientas/configuración: Trivy 0.69.3, `--scanners vuln --severity HIGH,CRITICAL`; OWASP ZAP Baseline, imagen `ghcr.io/zaproxy/zaproxy:stable`. Las imágenes `medicalsys/*:local` se reconstruyeron desde el árbol corregido, incluidos los últimos overrides de dependencias y actualización de paquetes Ubuntu.

## Resultados finales de Trivy

| Alcance | Reportes | Críticas | Altas |
|---|---:|---:|---:|
| POM del backend corregido | 14 | 0 | 0 |
| Imágenes `medicalsys/*:local` corregidas | 14 | 0 | 0 |

Los 14 JSON `medicalsys-*-final.json` conservan los resultados de imagen. El reporte de filesystem es `trivy-fs-despues-final-20261012.json`. El primer análisis de las imágenes encontró `lz4-java` 1.10.1 en notificaciones; se fijó a 1.11.4, se reconstruyó ese servicio y se repitió su escaneo antes de calcular estos totales.

## ZAP Baseline posterior

Se analizó `http://host.docker.internal:8080/actuator/health`, con 8 URLs exploradas y alcance centrado en el gateway/Actuator. Resultado: **0 FAIL** y 2 familias de advertencias: 4 ocurrencias de `Cross-Origin-Resource-Policy Header Missing or Invalid` (bajo) y 5 de `Non-Storable Content` (informativa). La advertencia informativa aumentó porque la exploración final identificó más respuestas del Actuator sin almacenamiento. El baseline de develop tuvo 2 ocurrencias de esa advertencia. Reportes: `zap-baseline-despues-20261012.json` y `zap-baseline-despues-20261012.html`.

No se encontró definición OpenAPI en el árbol analizado; API scan no aplica. Este ZAP Baseline no es un escaneo autenticado de todos los microservicios.

## Comparación reproducible

La línea base se reconstruyó desde `origin/develop` (`baef748f948e7ab9f7048d95fa3b00764c0d1a4a`) en un directorio temporal con etiquetas `:develop-baseline`. El backend corregido se escaneó desde el árbol local final.

| Hallazgo/alcance | Severidad | Antes (develop) | Después (corregido) | Corrección |
|---|---|---:|---:|---|
| Trivy filesystem: Bouncy Castle | 2 críticas, 1 alta | 3 | 0 | `bcprov-jdk18on` actualizado a 1.85 |
| Trivy imágenes: Tomcat | Crítica | 39 ocurrencias | 0 | Tomcat 11.0.25 |
| Trivy imágenes: Jackson | Alta | 75 ocurrencias | 0 | BOM Jackson 3.1.7 y Jackson 2 BOM 2.21.7 |
| Trivy imágenes: libssl3 | Alta | 14 ocurrencias | 0 | `apt-get upgrade` en la etapa final Ubuntu |
| Trivy imágenes: lz4-java | Alta | 4 ocurrencias | 0 | lz4-java 1.11.4; incluye ms-notificaciones |
| Trivy imágenes: Bouncy Castle | Crítica/Alta | 7 ocurrencias | 0 | bcprov 1.85 en consumidores afectados |
| ZAP Baseline: Cross-Origin-Resource-Policy faltante | Baja | 4 | 4 | Permanece como advertencia; no está en los headers requeridos por HARD-04 |
| ZAP Baseline: contenido no almacenable | Informativa | 2 | 5 | Advertencia informativa; no se considera vulnerabilidad alta/crítica |
| ZAP Baseline: FAIL | — | 0 | 0 | Sin alertas FAIL en los alcances explorados |

**Resumen Trivy (ocurrencias HIGH/CRITICAL):**

```text
Críticas  Antes  ██████████████████████ 43   Después  0
Altas     Antes  ████████████████████████████████████████████████ 96   Después  0
```

Los conteos de imágenes son ocurrencias por imagen/paquete; no representan CVE únicos. Los archivos intermedios `medicalsys-*.json` sin sufijo `-final` se conservan como evidencia histórica de la primera pasada y no se usan para las cifras finales.

## Pruebas dinámicas disponibles y limitaciones

- Gateway corregido: `GET /actuator/health` respondió HTTP 200.
- El host publicó solo `ms-gateway:8080`; el intento a `localhost:8088` no fue alcanzable.
- Preflight CORS: `http://localhost:4200` recibió `Access-Control-Allow-Origin` exacto; `https://malicioso.example` recibió HTTP 403.
- El stack completo no pudo pasar el smoke test: `AIVEN_MYSQL_HOST` apunta a `your-service.aivencloud.com`; servicios con Flyway no conectaron a MySQL. `minio-init` también falló durante su inicialización. Por ello no se pudieron verificar de extremo a extremo login/logout en pacientes/historial ni HTTP del webhook.
- No se encontró OpenAPI para ZAP API scan.

Las tareas de publicación Cloudflare y verificación desde red móvil no forman parte de estos reportes locales.
