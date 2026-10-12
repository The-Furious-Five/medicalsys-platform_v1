# Seccion 13 — Reporte de vulnerabilidades

## Alcance y herramientas

Se usó Trivy 0.69.3 con `--scanners vuln --severity HIGH,CRITICAL` sobre el filesystem Java/Maven y las 14 imágenes Docker `medicalsys/*:local`. Para línea base se reconstruyeron las imágenes de `origin/develop` (`baef748f948e7ab9f7048d95fa3b00764c0d1a4a`) con etiquetas temporales `:develop-baseline`. Se ejecutó OWASP ZAP Baseline desde `ghcr.io/zaproxy/zaproxy:stable` contra `/actuator/health` antes y después; no se encontró definición OpenAPI para API scan.

## Resultado inicial

El baseline de Trivy filesystem sobre develop encontró 3 vulnerabilidades en Bouncy Castle 1.78.1: 2 críticas y 1 alta. El Trivy de las 14 imágenes encontró 43 ocurrencias críticas y 96 altas. Los reportes individuales están en `escaneos/antes/`.

ZAP Baseline sobre el gateway de develop exploró 8 URLs y reportó 0 FAIL, 4 advertencias de severidad baja por falta de `Cross-Origin-Resource-Policy` y 2 advertencias informativas de contenido no almacenable. La superficie escaneada cubre principalmente Actuator, no el API autenticado completo.

## Correcciones realizadas

- Se restringieron los puertos publicados al gateway y se configuró la red interna `medicalsys-interna`.
- El gateway valida el `jti` activo en Redis, falla cerrado si Redis no está disponible y agrega headers de seguridad.
- CORS usa orígenes exactos; los usuarios demo quedan deshabilitados por defecto; rutas internas de identidad dejan de ser públicas; Actuator oculta detalles salvo a usuarios autorizados.
- WhatsApp exige firma HMAC y el proveedor Meta no arranca sin secreto configurado.
- Se actualizó Bouncy Castle a 1.85, Tomcat a 11.0.25, Jackson a 3.1.7/2.21.7 y lz4-java a 1.11.4. La etapa JRE actualiza paquetes Ubuntu.

## Resultado posterior y comparación

| Herramienta/alcance | Severidad | Antes (develop) | Después (árbol corregido) | Corrección / PR |
|---|---|---:|---:|---|
| Trivy filesystem | Crítica | 2 | 0 | Bouncy Castle 1.85; PR pendiente de crear |
| Trivy filesystem | Alta | 1 | 0 | Bouncy Castle 1.85; PR pendiente de crear |
| Trivy imágenes (14) | Crítica | 43 | 0 | Tomcat 11.0.25 y Bouncy Castle 1.85; PR pendiente de crear |
| Trivy imágenes (14) | Alta | 96 | 0 | Jackson 3.1.7/2.21.7, LZ4 1.11.4 y actualización Ubuntu; PR pendiente de crear |
| ZAP Baseline — CORP faltante | Baja | 4 | 4 | Advertencia abierta; sin PR |
| ZAP Baseline — contenido no almacenable | Informativa | 2 | 5 | Advertencia abierta, sin impacto HIGH/CRITICAL; sin PR |
| ZAP Baseline — FAIL | — | 0 | 0 | Sin alertas FAIL |

```text
Trivy imágenes: HIGH/CRITICAL por ocurrencia
Críticas  Antes  ██████████████████████ 43   Después  0
Altas     Antes  ████████████████████████████████████████████████ 96   Después  0
```

Los resultados finales corresponden a Trivy con severidades HIGH/CRITICAL; no significan que no existan hallazgos de severidad media/baja ni sustituyen un escaneo autenticado. La comparación por paquete y la evidencia bruta están en `escaneos/antes/README.md` y `escaneos/despues/README.md`.

## Estado de la verificación

El gateway corregido devolvió HTTP 200 en `/actuator/health`; `localhost:8088` no fue alcanzable; el preflight CORS permitió `http://localhost:4200` y rechazó `https://malicioso.example` con 403. El stack completo no pasó el smoke test: Aiven resuelve al host de ejemplo `your-service.aivencloud.com`, por lo que los microservicios que usan MySQL no completan Flyway; `minio-init` también falló. Los recorridos integrados de cierre de sesión hacia pacientes/historial y del webhook HTTP siguen pendientes. DEP-01 requiere el túnel Cloudflare y acceso desde una red externa.

No se creó un pull request en GitHub. Las correcciones existen como cambios locales en el repositorio; los identificadores de PR deben completarse cuando se abra el PR correspondiente.
