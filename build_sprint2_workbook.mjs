import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { Workbook, SpreadsheetFile } from "@oai/artifact-tool";

const outputDir = fileURLToPath(new URL("./outputs/sprint2_20260828/", import.meta.url));
const outputPath = path.join(outputDir, "MedicalSys_Sprint_2_Backlog_Frontend_Integracion.xlsx");

const C = {
  navy: "#0F172A",
  blue: "#2563EB",
  cyan: "#0EA5E9",
  teal: "#0F766E",
  green: "#16A34A",
  amber: "#F59E0B",
  red: "#DC2626",
  purple: "#7C3AED",
  slate: "#475569",
  muted: "#64748B",
  line: "#CBD5E1",
  paleBlue: "#EFF6FF",
  paleCyan: "#ECFEFF",
  paleGreen: "#F0FDF4",
  paleAmber: "#FFFBEB",
  paleRed: "#FEF2F2",
  palePurple: "#F5F3FF",
  white: "#FFFFFF",
};

const d = (month, day) => new Date(Date.UTC(2026, month - 1, day, 12, 0, 0));
const D1 = d(8, 28);
const D2 = d(8, 29);
const D3 = d(8, 30);
const D4 = d(8, 31);
const D5 = d(9, 1);

const stories = [
  ["HU-01", "Acceso y tenant", "Como integrante de una organización, quiero iniciar sesión y conservar un contexto de tenant válido para acceder únicamente a los datos de mi clínica.", "Acceso seguro y reproducible al flujo MVP.", "P0 — Crítica", 5, "Obligatoria", "Álvaro", "ms-identidad, ms-tenencia, ms-gateway", "UUID demo único; Redis/JTI; JWT", "Login por gateway; token con iss/exp/JTI; logout invalida sesión; un ADMIN de A no opera sobre B.", "Por hacer", "Video/captura de login, pruebas multi-tenant y revocación."],
  ["HU-02", "Pacientes", "Como recepcionista, quiero registrar, buscar y consultar pacientes desde el frontend para usarlos en agenda, documentos y facturación.", "Paciente real reutilizable durante todo el recorrido.", "P0 — Crítica", 8, "Obligatoria", "Nicole", "ms-pacientes + frontend", "HU-01; contratos internos", "CRUD mínimo y búsqueda por tenant; validaciones; DTO interno compatible con Agenda y Facturación; errores comunes.", "Por hacer", "Frontend funcional + pruebas de aislamiento y contratos."],
  ["HU-03", "Profesionales", "Como recepcionista, quiero registrar y seleccionar profesionales activos de mi organización para asignarlos a citas.", "Profesional válido y tenant-scoped disponible en agenda.", "P0 — Crítica", 5, "Obligatoria", "Jhon", "ms-profesionales + frontend", "HU-01; ms-catalogos", "Listado y alta mínima; seguridad por tenant; especialidad válida; proyección interna mínima para Agenda.", "Por hacer", "Pantalla de profesionales + pruebas de roles/tenant."],
  ["HU-04", "Agenda", "Como recepcionista, quiero consultar disponibilidad y agendar una cita para un paciente con un profesional y servicio elegidos.", "Cita válida sin solapamientos y lista para atención.", "P0 — Crítica", 8, "Obligatoria", "Álvaro", "ms-agenda + frontend", "HU-02; HU-03; ms-catalogos", "Ruta v1 congelada; tenant/actor desde JWT; validaciones remotas; conflicto 409; correlation ID propagado.", "Por hacer", "Reserva desde UI y pruebas de solapamiento/integración."],
  ["HU-05", "Atención clínica", "Como profesional, quiero abrir una atención, registrar diagnóstico y firmarla para incorporarla al historial médico.", "Registro clínico trazable y protegido.", "P0 — Crítica", 13, "Obligatoria", "Franco", "ms-historial, ms-criptografia, ms-catalogos + frontend", "HU-04; contratos Crypto/Catálogos", "Encuentro ligado a cita/paciente/profesional; diagnóstico de catálogo; cifrado remoto; firma; no persiste texto clínico en claro.", "Por hacer", "Atención completa desde UI + evidencia de cifrado y pruebas."],
  ["HU-06", "Orden de estudio", "Como profesional, quiero solicitar un estudio de imagen y, si corresponde, coordinar una cita de seguimiento para continuar el diagnóstico.", "Orden de rayos X visible y vinculada al flujo clínico.", "P0 — Crítica", 8, "Obligatoria", "Franco", "ms-historial, ms-catalogos, ms-agenda + frontend", "HU-05; HU-04", "Tipo de estudio válido; orden vinculada al encuentro; estado consultable; cita de imagen creada cuando corresponda.", "Por hacer", "Orden y cita de seguimiento visibles en la aplicación."],
  ["HU-07", "Documentos clínicos", "Como recepcionista o técnico, quiero cargar un estudio y asociarlo al paciente y a la orden para que quede disponible en el expediente.", "Archivo íntegro en MinIO y metadata consistente.", "P0 — Crítica", 8, "Obligatoria", "Nicole", "ms-documental, MinIO + frontend", "HU-02; HU-06", "Carga real; SHA-256; disponibilidad solo tras verificar objeto; vínculo a paciente/orden; aislamiento y limpieza de fallos.", "Por hacer", "Carga/consulta desde UI + pruebas de hash y objetos huérfanos."],
  ["HU-08", "Entrega manual", "Como recepcionista, quiero preparar el mensaje y acceder al contacto del paciente para enviar manualmente el aviso del examen por WhatsApp.", "Handoff manual demostrable sin construir notificaciones.", "P1 — Alta", 3, "Obligatoria", "Nicole", "frontend, ms-pacientes, ms-documental", "HU-02; HU-07", "Vista previa; copiar mensaje o abrir enlace de WhatsApp; no existe envío automático ni persistencia engañosa de entrega.", "Por hacer", "Captura del mensaje preparado y confirmación manual."],
  ["HU-09", "Facturación demo", "Como recepcionista, quiero generar y consultar una factura demo por el servicio prestado para cerrar administrativamente la atención.", "Factura coherente, identificada como simulada.", "P0 — Crítica", 8, "Obligatoria", "Franco", "ms-facturacion + frontend", "HU-02; HU-04; ms-tenencia", "Paciente y tenant remotos; servicio/total/correlativo; idempotencia; leyenda DEMO; sin conexión real al SIN.", "Por hacer", "Factura generada desde UI + pruebas de totales/idempotencia."],
  ["HU-10", "Gateway e integración", "Como usuario del MVP, quiero ejecutar todo el recorrido a través del gateway para no depender de puertos internos ni dobles locales.", "Punto único de entrada y comunicación integrada real.", "P0 — Crítica", 8, "Obligatoria", "Nicole", "ms-gateway, Compose, todos los servicios", "HU-01 a HU-09", "Rutas v1; JWT obligatorio; issuer/JTI; correlation ID; perfil integrated; solo gateway publicado; fallos downstream controlados.", "Por hacer", "Smoke test y recorrido E2E usando únicamente el gateway."],
  ["HU-11", "Experiencia frontend", "Como usuario, quiero una interfaz consistente, clara y con transiciones útiles para completar el flujo sin aprender detalles técnicos.", "Frontend presentable y comprensible durante la demo.", "P1 — Alta", 5, "Obligatoria", "Jhon", "frontend transversal", "HU-01 a HU-10", "Flujo aprobado por Interaction Designer; sistema visual aprobado; movimiento con propósito; estados loading/empty/error; navegación por rol.", "Por hacer", "Mapa de flujo, kit visual, guía de movimiento y revisión UX final."],
  ["HU-12", "Contratos y errores", "Como equipo, quiero contratos v1 y respuestas de error uniformes para integrar servicios y frontend sin adivinar rutas ni JSON.", "Fuente única de verdad y menos regresiones.", "P0 — Crítica", 5, "Obligatoria", "Juanjo", "docs/contracts + todos los servicios", "Decisiones de rutas y DTO", "Matriz método/ruta/productor/consumidor/rol; OpenAPI por servicio; error de seis campos; X-Correlation-Id único.", "Por hacer", "Contratos versionados y tests de compatibilidad."],
  ["HU-13", "Pruebas y evidencia", "Como equipo, quiero pruebas automatizadas y una evidencia reproducible del recorrido para saber que el MVP funciona en conjunto.", "Demo confiable, repetible y verificable.", "P0 — Crítica", 8, "Obligatoria", "Nicole", "tests, CI local, script E2E", "HU-01 a HU-12", "Mockito/JDK reparado; tests no omitidos silenciosamente; script seed→login→flujo→factura; checklist 15/15; evidencias.", "Por hacer", "Reporte de tests, script E2E y carpeta de evidencias."],
  ["HU-14", "Operación local", "Como integrante del equipo, quiero levantar y diagnosticar la plataforma con un solo procedimiento para reducir el tiempo perdido antes de la demo.", "Arranque limpio, salud visible y documentación útil.", "P1 — Alta", 5, "Objetivo ampliado", "Nicole", "Compose, Actuator, README", "HU-10; HU-13", "Healthchecks/readiness; imágenes fijadas; logs manejables; README de arranque, smoke y apagado; ensayo desde cero.", "Por hacer", "Runbook probado en otra máquina o instalación limpia."],
];

const task = (id, hu, area, detail, owner, dayLabel, date, priority, hours, dependency, evidence, done, status = "Por hacer") =>
  [id, hu, area, detail, owner, dayLabel, date, priority, hours, dependency, evidence, done, status];

const tasks = [
  task("T-001", "HU-01", "ms-identidad", "Unificar el UUID demo mediante variable compartida y eliminar el valor divergente del seeder/configuración.", "Álvaro", "Día 1 — Vie", D1, "P0 — Crítica", 3, "Acordar UUID con Franco", "Token y Tenencia referencian la misma organización", "Prueba automática y login válido"),
  task("T-002", "HU-01", "ms-tenencia", "Alinear el tenant demo y añadir comprobación interna de existencia/estado de la organización.", "Franco", "Día 1 — Vie", D1, "P0 — Crítica", 3, "T-001", "Seed y endpoint interno coherentes", "Smoke test confirma organizationId"),
  task("T-003", "HU-01", "ms-identidad", "Aplicar aislamiento por tenant en altas, búsquedas, edición y asignación de roles.", "Álvaro", "Día 1 — Vie", D1, "P0 — Crítica", 6, "Claims JWT", "Casos A→A permitidos y A→B rechazados", "Tests 403/404 entre tenants"),
  task("T-004", "HU-01", "ms-identidad", "Exponer y documentar una estrategia de sesión revocable basada en JTI/Redis para el gateway.", "Álvaro", "Día 2 — Sáb", D2, "P0 — Crítica", 5, "Redis disponible", "Contrato de validación/introspección", "Logout invalida token en prueba integrada"),
  task("T-005", "HU-01", "frontend", "Implementar login, almacenamiento seguro de sesión, cierre de sesión y protección de rutas por rol.", "Álvaro", "Día 2 — Sáb", D2, "P0 — Crítica", 7, "T-004; HU-11", "Pantalla login y rutas protegidas", "Sin token no entra; logout redirige"),

  task("T-006", "HU-02", "ms-pacientes", "Agregar organizationId al modelo/consultas y aplicar tenant-scoping en todos los casos de uso.", "Nicole", "Día 1 — Vie", D1, "P0 — Crítica", 6, "HU-01", "Migración y repositorios tenant-scoped", "Tests de paciente homónimo en dos tenants"),
  task("T-007", "HU-02", "ms-pacientes", "Congelar las rutas y DTO mínimos internos consumidos por Agenda y Facturación.", "Nicole", "Día 1 — Vie", D1, "P0 — Crítica", 4, "HU-12", "Contrato /internal/v1 versionado", "Tests de contrato verdes"),
  task("T-008", "HU-02", "ms-pacientes", "Eliminar filtros/ApiError/handlers duplicados y adoptar el error común con un solo correlation ID.", "Nicole", "Día 2 — Sáb", D2, "P1 — Alta", 4, "HU-12", "Infraestructura web única", "Errores contienen seis campos"),
  task("T-009", "HU-02", "frontend", "Crear formulario, búsqueda y ficha resumida de paciente con validaciones y estados de UI.", "Nicole", "Día 2 — Sáb", D2, "P0 — Crítica", 8, "T-006; HU-11", "Pantallas de paciente integradas", "Paciente creado y recuperado por gateway"),
  task("T-010", "HU-02", "pruebas", "Cubrir duplicados, complemento documental, búsquedas, roles, tenant y DTO internos.", "Nicole", "Día 3 — Dom", D3, "P0 — Crítica", 5, "T-006 a T-009", "Suite de pacientes", "Tests unitarios/integración verdes"),

  task("T-011", "HU-03", "ms-profesionales", "Agregar autenticación, autorización por roles y filtro obligatorio por organización.", "Jhon", "Día 1 — Vie", D1, "P0 — Crítica", 6, "HU-01", "Endpoints protegidos", "A no consulta/modifica profesionales de B"),
  task("T-012", "HU-03", "ms-profesionales", "Crear proyección interna mínima tenant-scoped para validación desde Agenda.", "Jhon", "Día 1 — Vie", D1, "P0 — Crítica", 4, "HU-12", "DTO interno ID/tenant/especialidad/activo", "Agenda valida sin datos extra"),
  task("T-013", "HU-03", "ms-profesionales", "Validar especialidad y referencias externas contra Catálogos con timeout y correlation ID.", "Jhon", "Día 2 — Sáb", D2, "P1 — Alta", 4, "ms-catalogos", "Cliente remoto controlado", "Código inválido produce error común"),
  task("T-014", "HU-03", "frontend", "Crear listado, alta mínima y selector de profesional activo para Agenda.", "Jhon", "Día 2 — Sáb", D2, "P0 — Crítica", 7, "T-011; HU-11", "Pantallas y selector reutilizable", "Profesional aparece al agendar"),
  task("T-015", "HU-03", "pruebas", "Añadir pruebas de tenant, roles, ciclo de vida, DTO interno, error común y correlación.", "Jhon", "Día 3 — Dom", D3, "P0 — Crítica", 5, "T-011 a T-014", "Suite de profesionales", "Suite verde con cobertura de seguridad"),

  task("T-016", "HU-04", "ms-agenda", "Congelar una sola ruta pública v1 y documentar aliases temporales, si fueran necesarios.", "Álvaro", "Día 1 — Vie", D1, "P0 — Crítica", 3, "HU-12", "Ruta normativa de citas", "Gateway, frontend y pruebas usan la misma ruta"),
  task("T-017", "HU-04", "ms-agenda", "Tomar organizationId/userId/roles del JWT y retirar tenant/actor falsificables de body/query.", "Álvaro", "Día 1 — Vie", D1, "P0 — Crítica", 5, "HU-01", "Comandos sin actor manipulable", "Prueba rechaza spoofing"),
  task("T-018", "HU-04", "ms-agenda", "Corregir ruta/DTO hacia Pacientes y aplicar timeout y X-Correlation-Id a clientes externos.", "Álvaro", "Día 2 — Sáb", D2, "P0 — Crítica", 5, "T-007", "Validación remota de paciente", "Agenda falla controladamente si Pacientes cae"),
  task("T-019", "HU-04", "ms-agenda", "Integrar la validación tenant-scoped de profesional activo y especialidad.", "Álvaro", "Día 2 — Sáb", D2, "P0 — Crítica", 4, "T-012", "Validación remota de profesional", "Profesional de otro tenant es rechazado"),
  task("T-020", "HU-04", "frontend", "Crear vista de disponibilidad, selección paciente/profesional/servicio y confirmación de cita.", "Álvaro", "Día 3 — Dom", D3, "P0 — Crítica", 8, "T-018; T-019; HU-11", "Flujo de agenda", "Cita visible tras confirmar"),
  task("T-021", "HU-04", "pruebas", "Cubrir solapamientos, estados, referencias remotas, tenant, roles y caída de dependencias.", "Álvaro", "Día 3 — Dom", D3, "P0 — Crítica", 5, "T-017 a T-020", "Suite de agenda integrada", "409 correcto y correlation ID estable"),

  task("T-022", "HU-05", "ms-historial", "Congelar contratos de encuentro, diagnóstico, firma y consulta clínica para el frontend.", "Franco", "Día 1 — Vie", D1, "P0 — Crítica", 3, "HU-12", "OpenAPI de atención clínica", "Requests/responses revisados por consumidor"),
  task("T-023", "HU-05", "ms-catalogos", "Exponer rutas requeridas por Historial para diagnósticos/medicamentos y completar seeds MVP.", "Juanjo", "Día 1 — Vie", D1, "P0 — Crítica", 5, "Contrato Historial→Catálogos", "Catálogos disponibles en modo remoto", "Historial valida códigos reales"),
  task("T-024", "HU-05", "ms-criptografia", "Alinear rutas y JSON remotos con Historial e implementar claves efectivas por tenant.", "Juanjo", "Día 2 — Sáb", D2, "P0 — Crítica", 7, "Contrato Historial→Crypto", "Encrypt/decrypt tenant-scoped", "Roundtrip remoto y separación por tenant"),
  task("T-025", "HU-05", "ms-historial", "Activar clientes remotos de Catálogos/Criptografía y eliminar cifrado local efímero del perfil integrado.", "Franco", "Día 2 — Sáb", D2, "P0 — Crítica", 6, "T-023; T-024", "Historial integrado real", "Servicio no arranca/continúa con doble oculto"),
  task("T-026", "HU-05", "ms-historial", "Validar cita, paciente y profesional antes de crear encuentro y firmar diagnóstico.", "Franco", "Día 3 — Dom", D3, "P0 — Crítica", 5, "HU-02 a HU-04", "Referencias clínicas verificadas", "IDs ajenos/inexistentes rechazados"),
  task("T-027", "HU-05", "frontend", "Crear pantalla de atención: contexto de cita, narrativa, diagnóstico, firma y confirmación.", "Franco", "Día 3 — Dom", D3, "P0 — Crítica", 8, "T-022 a T-026; HU-11", "Flujo clínico completo", "Atención firmada visible en historial"),
  task("T-028", "HU-05", "pruebas", "Probar permisos de profesional, inmutabilidad, cifrado remoto, errores y ausencia de texto clínico en claro.", "Franco", "Día 4 — Lun", D4, "P0 — Crítica", 5, "T-025 a T-027", "Suite clínica", "Tests y verificación DB verdes"),

  task("T-029", "HU-06", "ms-catalogos", "Exponer la ruta única de tipos de estudio y sembrar Rayos X para la demo.", "Juanjo", "Día 1 — Vie", D1, "P0 — Crítica", 4, "HU-12", "Tipo de estudio consultable", "Historial consume la ruta acordada"),
  task("T-030", "HU-06", "ms-historial", "Implementar/ajustar creación y consulta de orden de estudio vinculada al encuentro.", "Franco", "Día 3 — Dom", D3, "P0 — Crítica", 5, "T-029; HU-05", "Orden persistida", "Orden contiene paciente, encuentro, tipo y estado"),
  task("T-031", "HU-06", "ms-agenda", "Permitir crear la cita de imagen/seguimiento desde la orden sin romper el contrato de Agenda.", "Álvaro", "Día 3 — Dom", D3, "P1 — Alta", 5, "T-030; HU-04", "Cita asociada al propósito de estudio", "Aparece en agenda y evita solapamiento"),
  task("T-032", "HU-06", "frontend", "Añadir solicitud de estudio, selección de tipo y acción opcional para agendar seguimiento.", "Franco", "Día 4 — Lun", D4, "P0 — Crítica", 6, "T-030; T-031; HU-11", "UI de orden y seguimiento", "Orden y cita se visualizan"),
  task("T-033", "HU-06", "pruebas", "Cubrir tipo inválido, transición de estados, vínculo al encuentro y seguimiento.", "Franco", "Día 4 — Lun", D4, "P1 — Alta", 4, "T-030 a T-032", "Suite de órdenes", "Casos positivos/negativos verdes"),

  task("T-034", "HU-07", "ms-documental", "Verificar existencia, tamaño y hash del objeto antes de marcar un documento AVAILABLE.", "Nicole", "Día 2 — Sáb", D2, "P0 — Crítica", 5, "MinIO", "Estado coherente con objeto", "No se confirma objeto ausente/corrupto"),
  task("T-035", "HU-07", "MinIO/Compose", "Crear usuario/bucket con privilegio mínimo, fijar versión y retirar credenciales root de la aplicación.", "Nicole", "Día 2 — Sáb", D2, "P1 — Alta", 4, "Compose", "Credenciales limitadas", "Carga/lectura funcionan sin root"),
  task("T-036", "HU-07", "ms-documental", "Asegurar vínculo tenant-scoped a paciente/orden y compensar fallos que generen objetos huérfanos.", "Nicole", "Día 3 — Dom", D3, "P0 — Crítica", 5, "HU-02; HU-06", "Metadata y limpieza consistentes", "Rollback/cleanup probado"),
  task("T-037", "HU-07", "frontend", "Crear carga, progreso, asociación, consulta de metadata y enlace de descarga/visualización.", "Nicole", "Día 3 — Dom", D3, "P0 — Crítica", 8, "T-034 a T-036; HU-11", "Pantalla documental", "Documento aparece ligado a orden/paciente"),
  task("T-038", "HU-07", "pruebas", "Cubrir MIME/tamaño/hash, tenant, objeto faltante, reintento y limpieza de error.", "Nicole", "Día 4 — Lun", D4, "P0 — Crítica", 5, "T-034 a T-037", "Suite documental", "Tests y verificación MinIO verdes"),

  task("T-039", "HU-08", "UX/alcance", "Documentar el handoff manual: qué datos se muestran, mensaje sugerido y qué NO confirma el sistema.", "Franco", "Día 1 — Vie", D1, "P1 — Alta", 2, "HU-11", "Microflujo aprobado", "No se confunde preparación con envío"),
  task("T-040", "HU-08", "frontend", "Mostrar teléfono, vista previa y acciones Copiar/Abrir WhatsApp sin API automática.", "Nicole", "Día 4 — Lun", D4, "P1 — Alta", 5, "HU-02; HU-07", "Handoff manual", "Mensaje listo y datos correctos"),
  task("T-041", "HU-08", "pruebas/evidencia", "Verificar permisos, ausencia de envío automático y registrar evidencia de la confirmación manual.", "Jhon", "Día 5 — Mar", D5, "P1 — Alta", 2, "T-040", "Checklist de entrega manual", "No se registra estado ENTREGADO falso"),

  task("T-042", "HU-09", "ms-facturacion", "Alinear ruta/DTO remoto de Pacientes y activar PATIENTS_MODE remoto en perfil integrated.", "Franco", "Día 1 — Vie", D1, "P0 — Crítica", 4, "T-007", "Cliente Pacientes compatible", "Factura obtiene datos reales"),
  task("T-043", "HU-09", "ms-facturacion", "Activar validación remota de Tenencia y quitar la simulación silenciosa del perfil integrated.", "Franco", "Día 2 — Sáb", D2, "P0 — Crítica", 3, "T-002", "Cliente Tenencia remoto", "Tenant inactivo bloquea emisión"),
  task("T-044", "HU-09", "ms-facturacion", "Añadir idempotencia y trazabilidad a emisión; conservar totales BigDecimal y correlativo.", "Franco", "Día 3 — Dom", D3, "P1 — Alta", 5, "T-042; T-043", "Emisión segura a reintentos", "Misma clave no duplica factura"),
  task("T-045", "HU-09", "frontend", "Crear generación y consulta de factura con paciente/servicio/total y leyenda visible DEMO.", "Franco", "Día 4 — Lun", D4, "P0 — Crítica", 8, "T-044; HU-11", "Pantalla de facturación", "Factura visible y descargable/imprimible"),
  task("T-046", "HU-09", "pruebas", "Cubrir tenant/paciente remotos, totales, correlativo, idempotencia y modo SIN simulado.", "Franco", "Día 4 — Lun", D4, "P0 — Crítica", 5, "T-042 a T-045", "Suite de facturación", "Sin tests de contenedor omitidos"),

  task("T-047", "HU-10", "ms-gateway", "Corregir URL de Identidad y completar rutas v1 de los diez microservicios.", "Nicole", "Día 1 — Vie", D1, "P0 — Crítica", 4, "HU-12", "Mapa de rutas gateway", "Smoke de ruta por servicio"),
  task("T-048", "HU-10", "ms-gateway", "Reemplazar permitAll por política explícita; validar firma, issuer, expiración y JTI/sesión.", "Nicole", "Día 2 — Sáb", D2, "P0 — Crítica", 6, "T-004", "Gateway protegido", "401 común sin token/revocado"),
  task("T-049", "HU-10", "ms-gateway", "Validar/generar X-Correlation-Id y preservarlo en respuestas, errores y saltos.", "Nicole", "Día 2 — Sáb", D2, "P1 — Alta", 4, "HU-12", "Trazabilidad transversal", "Mismo UUID de borde a downstream"),
  task("T-050", "HU-10", "Compose", "Crear perfiles demo-local, integrated y debug; tres redes; solo gateway publicado en integrated.", "Nicole", "Día 3 — Dom", D3, "P0 — Crítica", 8, "Rutas/clientes remotos", "Compose integrado seguro", "Servicios se resuelven por DNS interno"),
  task("T-051", "HU-10", "integración", "Comprobar fallos controlados al detener Pacientes, Catálogos, Criptografía o Tenencia.", "Nicole", "Día 4 — Lun", D4, "P0 — Crítica", 5, "T-050", "Matriz de resiliencia mínima", "No se sustituyen dependencias con datos inventados"),
  task("T-052", "HU-10", "integración", "Ejecutar el flujo únicamente por gateway y eliminar URLs directas del frontend.", "Nicole", "Día 4 — Lun", D4, "P0 — Crítica", 5, "HU-01 a HU-09", "Frontend usa un base URL", "Network trace no llama puertos internos"),

  task("T-053", "HU-11", "Interaction Design", "Mapear el recorrido login→paciente→profesional→cita→atención→estudio→documento→aviso→factura, con decisiones y feedback de cada acción.", "Franco", "Día 1 — Vie", D1, "P0 — Crítica", 4, "Sprint Goal", "Mapa de flujo y estados", "Equipo lo revisa antes de construir pantallas"),
  task("T-054", "HU-11", "Visual Design", "Definir paleta, tipografía, espaciado, componentes, jerarquía, estados y reglas de accesibilidad visual.", "Nicole", "Día 1 — Vie", D1, "P1 — Alta", 4, "T-053", "Mini design system", "Contraste y componentes aprobados"),
  task("T-055", "HU-11", "Motion Design", "Definir transiciones y microinteracciones con propósito, duración, reducción de movimiento y estados loading/success/error.", "Álvaro", "Día 1 — Vie", D1, "P1 — Alta", 3, "T-053; T-054", "Guía breve de movimiento", "Ninguna animación bloquea o confunde"),
  task("T-056", "HU-11", "frontend", "Crear shell, navegación por rol, cliente HTTP común, manejo de sesión, layout y componentes base.", "Jhon", "Día 1 — Vie", D1, "P0 — Crítica", 8, "T-053 a T-055", "Base frontend compartida", "Todos integran sin duplicar infraestructura"),
  task("T-057", "HU-11", "frontend", "Implementar estados loading/empty/error/success y mostrar correlation ID útil en errores.", "Jhon", "Día 2 — Sáb", D2, "P1 — Alta", 5, "T-056; HU-12", "Patrones de feedback", "Errores no son pantallas en blanco"),
  task("T-058", "HU-11", "revisión UX", "Realizar revisión diaria de flujo, consistencia visual y movimiento; registrar ajustes accionables.", "Franco", "Día 4 — Lun", D4, "P1 — Alta", 3, "Frontend integrado", "Checklist UX consolidado", "Bloqueos de uso corregidos"),
  task("T-059", "HU-11", "revisión UX", "Validar al cierre la coherencia visual y accesibilidad de las pantallas del recorrido.", "Nicole", "Día 5 — Mar", D5, "P1 — Alta", 3, "T-054; frontend completo", "Aprobación visual", "Sin inconsistencias P0/P1"),
  task("T-060", "HU-11", "revisión UX", "Validar al cierre las transiciones, feedback y preferencia reduced-motion.", "Álvaro", "Día 5 — Mar", D5, "P1 — Alta", 3, "T-055; frontend completo", "Aprobación de movimiento", "Animaciones útiles y opcionales"),

  task("T-061", "HU-12", "contratos", "Crear matriz normativa método/ruta/productor/consumidor/rol y resolver rutas contradictorias.", "Juanjo", "Día 1 — Vie", D1, "P0 — Crítica", 4, "Revisión conjunta", "routes-v1.md", "Gateway/frontend/tests usan la matriz"),
  task("T-062", "HU-12", "contratos", "Definir schema común de error y política única de X-Correlation-Id.", "Juanjo", "Día 1 — Vie", D1, "P0 — Crítica", 3, "Guía de inducción", "error-v1.md", "Seis campos base en todos los servicios"),
  task("T-063", "HU-12", "contratos", "Actualizar OpenAPI/contratos de Identidad y Agenda y validar requests/responses con frontend.", "Álvaro", "Día 2 — Sáb", D2, "P0 — Crítica", 4, "T-061; T-062", "Contratos de Álvaro", "Ejemplos ejecutables"),
  task("T-064", "HU-12", "contratos", "Actualizar OpenAPI/contratos de Tenencia, Historial y Facturación.", "Franco", "Día 2 — Sáb", D2, "P0 — Crítica", 5, "T-061; T-062", "Contratos de Franco", "Consumidores revisados"),
  task("T-065", "HU-12", "contratos", "Actualizar OpenAPI/contratos de Pacientes, Documental y Gateway.", "Nicole", "Día 2 — Sáb", D2, "P0 — Crítica", 5, "T-061; T-062", "Contratos de Nicole", "Consumidores revisados"),
  task("T-066", "HU-12", "contratos", "Actualizar OpenAPI/contratos de Profesionales y completar tests del error común.", "Jhon", "Día 2 — Sáb", D2, "P0 — Crítica", 4, "T-061; T-062", "Contrato Profesionales", "Error/correlation conformes"),
  task("T-067", "HU-12", "contratos", "Actualizar contratos de Catálogos/Criptografía y añadir pruebas consumidor-productor críticas.", "Juanjo", "Día 3 — Dom", D3, "P0 — Crítica", 6, "T-023; T-024", "Contratos y tests", "Cambios incompatibles fallan antes del merge"),

  task("T-068", "HU-13", "infra de test", "Reparar Mockito/Byte Buddy con JDK 21/21+ y definir secretos/variables seguros de test.", "Juanjo", "Día 2 — Sáb", D2, "P0 — Crítica", 5, "POMs de servicios", "Configuración común documentada", "Suites antes rotas ya ejecutan"),
  task("T-069", "HU-13", "pruebas", "Ejecutar y reparar tests de Catálogos y Criptografía, incluyendo contratos remotos.", "Juanjo", "Día 4 — Lun", D4, "P0 — Crítica", 5, "T-068; T-067", "Reporte Juanjo", "0 fallos y sin skips injustificados"),
  task("T-070", "HU-13", "pruebas", "Ejecutar y reparar tests de Identidad y Agenda, incluyendo tenant y revocación.", "Álvaro", "Día 4 — Lun", D4, "P0 — Crítica", 5, "T-068", "Reporte Álvaro", "0 fallos y casos P0 cubiertos"),
  task("T-071", "HU-13", "pruebas", "Ejecutar y reparar tests de Tenencia, Historial y Facturación con bases reales.", "Franco", "Día 5 — Mar", D5, "P0 — Crítica", 6, "T-068", "Reporte Franco", "Testcontainers ejecuta, no aparece Skipped"),
  task("T-072", "HU-13", "pruebas", "Ejecutar y reparar tests de Pacientes, Documental y Gateway.", "Nicole", "Día 5 — Mar", D5, "P0 — Crítica", 6, "T-068", "Reporte Nicole", "0 fallos y cobertura de gateway"),
  task("T-073", "HU-13", "pruebas", "Ejecutar y reparar tests de Profesionales y apoyar pruebas de componentes frontend.", "Jhon", "Día 4 — Lun", D4, "P0 — Crítica", 5, "T-068", "Reporte Jhon", "0 fallos y UI crítica verificada"),
  task("T-074", "HU-13", "E2E", "Automatizar seed→login→paciente→profesional→cita→atención→orden→documento→factura por gateway.", "Nicole", "Día 5 — Mar", D5, "P0 — Crítica", 8, "HU-01 a HU-12", "Script E2E reproducible", "Recorrido completo termina en verde"),
  task("T-075", "HU-13", "demo", "Ejecutar checklist 15/15, capturar evidencia y registrar defectos con responsable y severidad.", "Jhon", "Día 5 — Mar", D5, "P0 — Crítica", 5, "T-074", "Carpeta de evidencias", "Cada paso tiene evidencia o bug abierto"),

  task("T-076", "HU-14", "observabilidad", "Normalizar liveness/readiness incluyendo base y agregar healthchecks de Compose.", "Nicole", "Día 5 — Mar", D5, "P1 — Alta", 5, "HU-10", "Estado de salud útil", "Gateway espera servicios saludables"),
  task("T-077", "HU-14", "operación", "Completar README raíz con perfiles, variables, arranque, seed, smoke, E2E y apagado limpio.", "Jhon", "Día 5 — Mar", D5, "P1 — Alta", 4, "T-074", "Runbook raíz", "Otra persona reproduce el flujo"),
  task("T-078", "HU-14", "operación", "Fijar versiones de infraestructura y configurar rotación/límites básicos de logs.", "Juanjo", "Día 5 — Mar", D5, "P1 — Alta", 3, "Compose", "Compose reproducible", "No usa imágenes latest"),
  task("T-079", "HU-14", "ensayo", "Levantar desde cero con perfil integrated y realizar ensayo cronometrado con plan de contingencia.", "Franco", "Día 5 — Mar", D5, "P1 — Alta", 4, "T-074; T-076; T-077", "Acta de ensayo", "Demo completa sin puertos internos"),
];

const daily = [
  [D1, "Viernes 28", "Contratos, seguridad P0 y base frontend", "Alinear tenant; tenant isolation; ruta Agenda; guía motion", "Flujo Interaction; contratos clínicos; clientes Facturación; tenant Tenencia", "Catálogos/estudios; matriz rutas/errores", "Paciente tenant; rutas gateway; design system", "Seguridad Profesionales; frontend shell", "Contratos congelados, login base y frontend arrancando"],
  [D2, "Sábado 29", "Integraciones remotas y primeras pantallas", "JTI; Agenda remota; login; OpenAPI", "Crypto/Catálogos remotos; Tenencia remota; OpenAPI", "Crypto por tenant; infra Mockito", "Gateway JWT; Pacientes UI; Documental/MinIO; OpenAPI", "Profesionales UI; feedback común; OpenAPI", "Paciente/profesional/login operables; dependencias remotas activas"],
  [D3, "Domingo 30", "Cerrar cita, atención, orden y documento", "Agenda UI/tests; seguimiento de estudio", "Atención/orden/UI clínica; Facturación idempotente", "Contratos y tests consumidor-productor", "Compose integrated; Documento UI; pruebas Pacientes", "Pruebas Profesionales; soporte de componentes", "Vertical slice hasta documento en perfil integrated"],
  [D4, "Lunes 31", "Facturación, entrega manual e integración completa", "Tests Identidad/Agenda", "Factura UI/tests; estudio; tests clínicos; revisión Interaction", "Tests Catálogos/Crypto", "Gateway E2E; handoff WhatsApp; tests Documental", "Tests Profesionales; revisión frontend y apoyo E2E", "Flujo completo ejecutado una vez; defectos P0 visibles"],
  [D5, "Martes 1", "Regresión, evidencia y ensayo", "Cierre motion; corregir regresiones", "Tests 3 servicios; ensayo y contingencia", "Versiones/logs; apoyo correcciones", "Tests 3 servicios; E2E; visual final; healthchecks", "Checklist 15/15; README; evidencias", "Cero P0 abiertos o decisión explícita de recorte"],
];

const roles = [
  ["Tech Lead", "Franco", "Decisiones técnicas, secuencia de integración, revisión de PR críticos y control de deuda que amenaza el Sprint Goal.", "Define reglas técnicas y aprueba contratos P0.", "Desbloquea dependencias y revisa integración diaria.", "Aprueba arquitectura ejecutada y plan de contingencia.", "No reemplaza al dueño de cada microservicio."],
  ["Interaction Designer", "Franco", "Diseña el flujo: qué ocurre después de cada pantalla/acción, qué espera el usuario y cómo se recupera de errores.", "Entrega mapa del recorrido y estados por acción.", "Revisa que cada slice siga el flujo acordado.", "Valida que una persona complete la demo sin explicación técnica.", "Prioriza claridad y feedback sobre cantidad de pantallas."],
  ["Visual Designer", "Nicole", "Cuida color, tipografía, jerarquía, componentes, accesibilidad y percepción de consistencia.", "Entrega mini design system y ejemplos de estados.", "Revisa pantallas nuevas y corrige desviaciones visibles.", "Da aprobación visual y de contraste del recorrido.", "Rol sorteado entre Juanjo, Álvaro, Nicole y Jhon."],
  ["Motion Designer", "Álvaro", "Define transiciones y microinteracciones que explican cambios de estado sin distraer ni bloquear.", "Entrega guía de movimiento, duraciones y reduced-motion.", "Revisa loading, éxito, error y cambios entre vistas.", "Aprueba coherencia y accesibilidad del movimiento.", "Rol sorteado entre Juanjo, Álvaro, Nicole y Jhon."],
];

const risks = [
  ["R-01", "Capacidad mayor a 40 h para algunos responsables", "Alta", "Alta", "Mantener HU-01 a HU-13 como meta, pero recortar primero HU-14 y mejoras P2; no recortar seguridad P0.", "Franco", "Abierto"],
  ["R-02", "Los modos locales ocultan contratos rotos", "Alta", "Alta", "Usar perfil integrated desde el Día 2 y probar caída de dependencias.", "Nicole", "Abierto"],
  ["R-03", "Frontend inicia antes de congelar rutas/JSON", "Media", "Alta", "Contrato v1 y ejemplos ejecutables antes de conectar cada pantalla.", "Juanjo", "Abierto"],
  ["R-04", "Sobrecarga de Nicole por Pacientes, Documental, Gateway, integración y diseño visual", "Alta", "Alta", "Jhon lidera shell frontend/evidencias; cada dueño integra sus pantallas; Franco decide recortes diarios.", "Franco", "Abierto"],
  ["R-05", "Trabajo de fin de semana reduce disponibilidad real", "Media", "Alta", "Daily corto, bloques pequeños, evidencia al cierre y reasignación temprana.", "Franco", "Abierto"],
  ["R-06", "Tests omitidos se interpretan como aprobados", "Alta", "Alta", "Fallará el checklist si Testcontainers/contratos aparecen Skipped sin justificación.", "Juanjo", "Abierto"],
  ["R-07", "Animaciones consumen tiempo o empeoran accesibilidad", "Media", "Media", "Solo movimiento funcional; respetar reduced-motion; cortar adornos antes que flujo.", "Álvaro", "Abierto"],
];

const dod = [
  ["Código", "Implementación en rama acordada, revisada y sin cambios secretos/locales accidentales.", "Todo cambio P0 con revisión del dueño + Tech Lead."],
  ["Contrato", "Ruta, roles, request, response y error están en docs/contracts/OpenAPI.", "Frontend y consumidor usan el contrato publicado."],
  ["Seguridad", "JWT/rol/tenant aplicados; no se confía en headers desde el host.", "Prueba positiva y negativa entre dos organizaciones."],
  ["Integración", "Funciona en perfil integrated y falla controladamente si falta una dependencia.", "No se usan dobles silenciosos en evidencia final."],
  ["Pruebas", "Unitarias, integración y contrato verdes; ningún skip oculto.", "Reporte guardado y reproducible."],
  ["Frontend", "Loading, vacío, éxito y error; navegación y permisos; correlation ID visible en error.", "Revisión Interaction + Visual + Motion cuando aplique."],
  ["Operación", "Health/readiness y arranque limpio documentados.", "Smoke y E2E pasan desde un entorno limpio."],
  ["Evidencia", "Captura/log/request del criterio de aceptación y enlace a defecto si falla.", "Checklist 15/15 actualizado."],
];

const wb = Workbook.create();
const summary = wb.worksheets.add("00_Resumen");
const hus = wb.worksheets.add("01_Historias");
const backlog = wb.worksheets.add("02_Tareas");
const plan = wb.worksheets.add("03_Plan_Diario");
const ux = wb.worksheets.add("04_Roles_UX");
const quality = wb.worksheets.add("05_DoD_Riesgos");

for (const sheet of [summary, hus, backlog, plan, ux, quality]) sheet.showGridLines = false;

function title(sheet, range, text, subtitleRange, subtitle) {
  sheet.getRange(range).merge();
  sheet.getRange(range).values = [[text]];
  sheet.getRange(range).format = { fill: C.navy, font: { color: C.white, bold: true, size: 24 }, verticalAlignment: "center", horizontalAlignment: "left" };
  sheet.getRange(range).format.rowHeight = 38;
  sheet.getRange(subtitleRange).merge();
  sheet.getRange(subtitleRange).values = [[subtitle]];
  sheet.getRange(subtitleRange).format = { fill: C.paleBlue, font: { color: C.slate, italic: true, size: 11 }, verticalAlignment: "center", wrapText: true };
  sheet.getRange(subtitleRange).format.rowHeight = 30;
}

function section(sheet, range, text, fill = C.blue) {
  sheet.getRange(range).merge();
  sheet.getRange(range).values = [[text]];
  sheet.getRange(range).format = { fill, font: { color: C.white, bold: true, size: 13 }, verticalAlignment: "center" };
  sheet.getRange(range).format.rowHeight = 25;
}

function header(range) {
  range.format = { fill: C.blue, font: { color: C.white, bold: true }, wrapText: true, verticalAlignment: "center", horizontalAlignment: "center", borders: { preset: "all", style: "thin", color: C.line } };
  range.format.rowHeight = 32;
}

function bordered(range) {
  range.format.borders = { preset: "all", style: "thin", color: C.line };
  range.format.verticalAlignment = "top";
  range.format.wrapText = true;
}

// 00_Resumen
title(summary, "A1:N2", "MedicalSys · Sprint 2 — Cierre del MVP integrado", "A3:N3", "Plan de cinco días · 28 de agosto al 1 de septiembre de 2026 · Backend corregido + frontend del flujo MVP + integración + pruebas");
section(summary, "A5:N5", "SPRINT GOAL", C.teal);
summary.getRange("A6:N7").merge();
summary.getRange("A6:N7").values = [["Cerrar las historias del flujo MVP con frontend, backend integrado, pruebas y evidencia reproducible."]];
summary.getRange("A6:N7").format = { fill: C.paleGreen, font: { color: C.navy, bold: true, size: 17 }, horizontalAlignment: "center", verticalAlignment: "center", wrapText: true, borders: { preset: "outside", style: "medium", color: C.teal } };

const cards = [
  ["A9:C9", "A10:C11", "Historias", "=COUNTA('01_Historias'!$A$6:$A$19)", C.blue],
  ["D9:F9", "D10:F11", "Story points", "=SUM('01_Historias'!$F$6:$F$19)", C.purple],
  ["G9:I9", "G10:I11", "Tareas", "=COUNTA('02_Tareas'!$A$6:$A$84)", C.cyan],
  ["J9:L9", "J10:L11", "Tareas P0", "=COUNTIF('02_Tareas'!$H$6:$H$84,\"P0 — Crítica\")", C.red],
  ["M9:N9", "M10:N11", "Avance", "=IFERROR(COUNTIF('02_Tareas'!$M$6:$M$84,\"Hecho\")/COUNTA('02_Tareas'!$A$6:$A$84),0)", C.green],
];
for (const [labelRange, valueRange, label, formula, fill] of cards) {
  summary.getRange(labelRange).merge();
  summary.getRange(labelRange).values = [[label]];
  summary.getRange(labelRange).format = { fill, font: { color: C.white, bold: true }, horizontalAlignment: "center", verticalAlignment: "center" };
  summary.getRange(valueRange).merge();
  summary.getRange(valueRange).formulas = [[formula]];
  summary.getRange(valueRange).format = { fill: C.white, font: { color: C.navy, bold: true, size: 20 }, horizontalAlignment: "center", verticalAlignment: "center", borders: { preset: "outside", style: "medium", color: fill } };
}
summary.getRange("M10:N11").format.numberFormat = "0%";

section(summary, "A13:G13", "ALCANCE INCLUIDO", C.blue);
section(summary, "H13:N13", "FUERA DE ALCANCE / NO CONFUNDIR", C.slate);
summary.getRange("A14:G19").merge();
summary.getRange("A14:G19").values = [["• Frontend del recorrido demostrado: login, paciente, profesional, cita, atención/diagnóstico, orden de estudio, documento, aviso manual y factura demo.\n• Reparación de contratos, tenant, JWT, correlation ID y modos remotos que bloquean el recorrido.\n• Integración exclusiva por gateway en perfil integrated.\n• Pruebas unitarias, integración, contrato y E2E; evidencia repetible.\n• Revisión transversal de interacción, interfaz visual y movimiento."]];
summary.getRange("H14:N19").merge();
summary.getRange("H14:N19").values = [["• No se construye frontend para cada endpoint administrativo del backend.\n• No se implementan microservicios de notificaciones, marketing ni auditoría.\n• WhatsApp es un handoff manual: preparar/copiar/abrir, no enviar automáticamente.\n• SIN permanece simulado y la factura debe indicar DEMO.\n• No es despliegue de producción ni reemplaza controles regulatorios."]];
summary.getRange("A14:N19").format = { fill: C.white, font: { color: C.slate, size: 11 }, wrapText: true, verticalAlignment: "top", borders: { preset: "all", style: "thin", color: C.line } };

section(summary, "A21:N21", "ORDEN DE EJECUCIÓN DEL SPRINT", C.purple);
const executionSteps = [
  ["1", "Congelar", "Día 1 · Rutas, DTO, errores, roles, tenant y flujo UX antes de conectar pantallas. Checkpoint: contratos + mapa UX."],
  ["2", "Corregir P0", "Días 1–2 · Tenant demo, seguridad, contratos remotos, gateway y suite de tests. Regla: P0 antes que estética."],
  ["3", "Construir slices", "Días 2–4 · Cada dueño entrega backend + pantalla de su tramo sobre componentes compartidos. La pantalla debe cerrar una HU."],
  ["4", "Integrar", "Días 3–5 · Perfil integrated, solo gateway y sin dobles locales silenciosos. Probar fallos controlados de dependencias."],
  ["5", "Demostrar", "Día 5 · E2E completo, regresión, evidencia 15/15 y ensayo desde cero. Sin evidencia no está Hecho."],
];
for (let i = 0; i < executionSteps.length; i++) {
  const row = 22 + i;
  summary.getRange(`A${row}`).values = [[executionSteps[i][0]]];
  summary.getRange(`B${row}:C${row}`).merge();
  summary.getRange(`B${row}:C${row}`).values = [[executionSteps[i][1]]];
  summary.getRange(`D${row}:N${row}`).merge();
  summary.getRange(`D${row}:N${row}`).values = [[executionSteps[i][2]]];
}
summary.getRange("A22:N26").format = { fill: C.white, font: { color: C.slate, size: 10 }, wrapText: true, verticalAlignment: "center", borders: { preset: "all", style: "thin", color: C.line } };
summary.getRange("A22:A26").format = { fill: C.palePurple, font: { color: C.purple, bold: true, size: 14 }, horizontalAlignment: "center", verticalAlignment: "center", borders: { preset: "all", style: "thin", color: C.line } };
summary.getRange("B22:C26").format.font = { color: C.navy, bold: true, size: 11 };

section(summary, "A28:F28", "CAPACIDAD VS ESFUERZO DESEADO (NO ES COMPROMISO)", C.teal);
summary.getRange("A29:F29").values = [["Responsable", "Horas disponibles", "Horas del alcance", "Utilización", "Microservicios", "Rol transversal"]];
header(summary.getRange("A29:F29"));
const people = [
  ["Juanjo", 40, "ms-criptografia, ms-catalogos", "—"],
  ["Franco", 40, "ms-historial, ms-tenencia, ms-facturacion", "Tech Lead + Interaction"],
  ["Álvaro", 40, "ms-agenda, ms-identidad", "Motion Designer"],
  ["Nicole", 40, "ms-pacientes, ms-documental, ms-gateway", "Visual Designer + Integración"],
  ["Jhon", 40, "ms-profesionales", "Frontend shell + evidencia"],
];
for (let i = 0; i < people.length; i++) {
  const row = 30 + i;
  summary.getRange(`A${row}:B${row}`).values = [[people[i][0], people[i][1]]];
  summary.getRange(`C${row}`).formulas = [[`=SUMIF('02_Tareas'!$E$6:$E$84,A${row},'02_Tareas'!$I$6:$I$84)`]];
  summary.getRange(`D${row}`).formulas = [[`=IFERROR(C${row}/B${row},0)`]];
  summary.getRange(`E${row}:F${row}`).values = [[people[i][2], people[i][3]]];
}
bordered(summary.getRange("A30:F34"));
summary.getRange("B30:C34").format.numberFormat = "0\" h\"";
summary.getRange("D30:D34").format.numberFormat = "0%";
summary.getRange("D30:D34").conditionalFormats.add("cellIs", { operator: "greaterThan", formula: 1, format: { fill: C.paleRed, font: { color: C.red, bold: true } } });

section(summary, "H28:N28", "LECTURA SCRUM DEL PLAN", C.amber);
summary.getRange("H29:N34").merge();
summary.getRange("H29:N34").values = [["Este sprint es intencionalmente ambicioso y no existe una velocidad histórica confiable para convertir story points en compromiso. La prioridad no es maximizar pantallas: es cerrar un recorrido vertical real.\n\nRegla de recorte: 1) HU-14 operación ampliada, 2) adornos/animaciones no funcionales, 3) pantallas administrativas fuera del flujo. No se recortan tenant, JWT, contratos, pruebas del recorrido ni la evidencia.\n\nEl Tech Lead revisa carga y bloqueos al inicio y al cierre de cada día."]];
summary.getRange("H29:N34").format = { fill: C.paleAmber, font: { color: C.slate, size: 11 }, wrapText: true, verticalAlignment: "top", borders: { preset: "outside", style: "medium", color: C.amber } };

summary.getRange("A36:N37").merge();
summary.getRange("A36:N37").values = [["Nota de calendario: se usan cinco días consecutivos reales: vie 28, sáb 29, dom 30, lun 31 de agosto y mar 1 de septiembre de 2026. El 1 de septiembre no cae lunes y el 2 de septiembre cae miércoles."]];
summary.getRange("A36:N37").format = { fill: C.paleBlue, font: { color: C.muted, italic: true, size: 10 }, wrapText: true, verticalAlignment: "center", borders: { preset: "outside", style: "thin", color: C.cyan } };

summary.getRange("A:A").format.columnWidth = 7;
summary.getRange("B:B").format.columnWidth = 13;
summary.getRange("C:C").format.columnWidth = 22;
summary.getRange("D:D").format.columnWidth = 8;
summary.getRange("E:E").format.columnWidth = 13;
summary.getRange("F:F").format.columnWidth = 21;
summary.getRange("G:G").format.columnWidth = 7;
summary.getRange("H:H").format.columnWidth = 13;
summary.getRange("I:I").format.columnWidth = 22;
summary.getRange("J:J").format.columnWidth = 7;
summary.getRange("K:K").format.columnWidth = 13;
summary.getRange("L:L").format.columnWidth = 21;
summary.getRange("M:M").format.columnWidth = 8;
summary.getRange("N:N").format.columnWidth = 20;
summary.freezePanes.freezeRows(3);

// 01_Historias
title(hus, "A1:M2", "Historias de usuario del Sprint 2", "A3:M3", "Las historias obligatorias cierran el flujo MVP; HU-14 es objetivo ampliado y se recorta primero si la capacidad real no alcanza.");
hus.getRange("A5:M5").values = [["HU", "Épica", "Historia de usuario", "Resultado / valor", "Prioridad", "Story points", "Compromiso", "Responsable líder", "Microservicios / área", "Dependencias", "Criterios de aceptación", "Estado", "Evidencia esperada"]];
header(hus.getRange("A5:M5"));
hus.getRange(`A6:M${5 + stories.length}`).values = stories;
bordered(hus.getRange(`A6:M${5 + stories.length}`));
hus.getRange(`F6:F${5 + stories.length}`).format.numberFormat = "0";
hus.getRange(`E6:E${5 + stories.length}`).dataValidation = { rule: { type: "list", values: ["P0 — Crítica", "P1 — Alta", "P2 — Media"] } };
hus.getRange(`G6:G${5 + stories.length}`).dataValidation = { rule: { type: "list", values: ["Obligatoria", "Objetivo ampliado"] } };
hus.getRange(`L6:L${5 + stories.length}`).dataValidation = { rule: { type: "list", values: ["Por hacer", "En curso", "Bloqueada", "En revisión", "Hecho"] } };
hus.getRange(`E6:E${5 + stories.length}`).conditionalFormats.add("containsText", { text: "P0", format: { fill: C.paleRed, font: { color: C.red, bold: true } } });
hus.getRange(`E6:E${5 + stories.length}`).conditionalFormats.add("containsText", { text: "P1", format: { fill: C.paleAmber, font: { color: "#92400E", bold: true } } });
hus.getRange(`G6:G${5 + stories.length}`).conditionalFormats.add("containsText", { text: "ampliado", format: { fill: C.palePurple, font: { color: C.purple, bold: true } } });
hus.getRange(`L6:L${5 + stories.length}`).conditionalFormats.add("containsText", { text: "Hecho", format: { fill: C.paleGreen, font: { color: C.green, bold: true } } });
const huTable = hus.tables.add(`A5:M${5 + stories.length}`, true, "HistoriasSprint2");
huTable.style = "TableStyleMedium2";
huTable.showBandedRows = true;
hus.freezePanes.freezeRows(5);
const huWidths = [10,18,46,30,14,11,16,15,34,25,52,14,32];
huWidths.forEach((w, i) => hus.getRange(`${String.fromCharCode(65+i)}:${String.fromCharCode(65+i)}`).format.columnWidth = w);
hus.getRange(`A6:M${5 + stories.length}`).format.rowHeight = 68;

// 02_Tareas
title(backlog, "A1:M2", "Backlog de tareas — ejecución y responsables", "A3:M3", "Cada tarea está vinculada a una HU, tiene dueño, día, evidencia y criterio de terminado. Actualizar Estado durante el daily.");
backlog.getRange("A5:M5").values = [["Tarea", "HU", "Microservicio / área", "Trabajo a realizar", "Responsable", "Día planificado", "Fecha", "Prioridad", "Horas", "Dependencia", "Entregable / evidencia", "Criterio de terminado", "Estado"]];
header(backlog.getRange("A5:M5"));
backlog.getRange(`A6:M${5 + tasks.length}`).values = tasks;
bordered(backlog.getRange(`A6:M${5 + tasks.length}`));
backlog.getRange(`G6:G${5 + tasks.length}`).setNumberFormat("dd-mmm-yyyy");
backlog.getRange(`I6:I${5 + tasks.length}`).format.numberFormat = "0\" h\"";
backlog.getRange(`E6:E${5 + tasks.length}`).dataValidation = { rule: { type: "list", values: ["Juanjo", "Franco", "Álvaro", "Nicole", "Jhon"] } };
backlog.getRange(`H6:H${5 + tasks.length}`).dataValidation = { rule: { type: "list", values: ["P0 — Crítica", "P1 — Alta", "P2 — Media"] } };
backlog.getRange(`M6:M${5 + tasks.length}`).dataValidation = { rule: { type: "list", values: ["Por hacer", "En curso", "Bloqueada", "En revisión", "Hecho"] } };
backlog.getRange(`H6:H${5 + tasks.length}`).conditionalFormats.add("containsText", { text: "P0", format: { fill: C.paleRed, font: { color: C.red, bold: true } } });
backlog.getRange(`H6:H${5 + tasks.length}`).conditionalFormats.add("containsText", { text: "P1", format: { fill: C.paleAmber, font: { color: "#92400E", bold: true } } });
backlog.getRange(`M6:M${5 + tasks.length}`).conditionalFormats.add("containsText", { text: "Bloqueada", format: { fill: C.paleRed, font: { color: C.red, bold: true } } });
backlog.getRange(`M6:M${5 + tasks.length}`).conditionalFormats.add("containsText", { text: "Hecho", format: { fill: C.paleGreen, font: { color: C.green, bold: true } } });
const taskTable = backlog.tables.add(`A5:M${5 + tasks.length}`, true, "TareasSprint2");
taskTable.style = "TableStyleMedium2";
taskTable.showBandedRows = true;
backlog.freezePanes.freezeRows(5);
const taskWidths = [10,9,22,52,14,16,14,14,9,24,32,34,14];
taskWidths.forEach((w, i) => backlog.getRange(`${String.fromCharCode(65+i)}:${String.fromCharCode(65+i)}`).format.columnWidth = w);
backlog.getRange(`A6:M${5 + tasks.length}`).format.rowHeight = 60;

// 03_Plan_Diario
title(plan, "A1:I2", "Plan diario de cinco días", "A3:I3", "Foco por persona y checkpoint de integración. El cierre diario debe dejar evidencia, bloqueos y decisión de recorte.");
plan.getRange("A5:I5").values = [["Fecha", "Día", "Foco común", "Álvaro", "Franco", "Juanjo", "Nicole", "Jhon", "Checkpoint del día"]];
header(plan.getRange("A5:I5"));
plan.getRange("A6:I10").values = daily;
bordered(plan.getRange("A6:I10"));
plan.getRange("A6:A10").setNumberFormat("dd-mmm-yyyy");
plan.getRange("A6:I10").format.rowHeight = 92;
plan.getRange("A12:I12").merge();
plan.getRange("A12:I12").values = [["Ritual diario (máximo 20 min al inicio + 15 min al cierre)"]];
plan.getRange("A12:I12").format = { fill: C.teal, font: { color: C.white, bold: true, size: 13 }, horizontalAlignment: "center" };
plan.getRange("A13:I16").values = [
  ["Inicio", "Cada persona: qué HU cerraré hoy", "Bloqueos", "Contrato necesario", "Evidencia que produciré", "Tech Lead", "Ordena dependencias", "Roles UX", "Anticipan revisión"],
  ["Mitad", "Integrar temprano", "No esperar al final", "Usar gateway", "Abrir bug con severidad", "Tech Lead", "Decide recorte", "Roles UX", "Revisan slice usable"],
  ["Cierre", "Actualizar Estado", "Adjuntar evidencia", "Registrar pruebas", "Declarar bloqueo", "Tech Lead", "Aprueba P0", "Roles UX", "Aprueban o dejan ajustes"],
  ["Regla", "No más de una tarea En curso por persona", "P0 primero", "Sin contrato no integrar", "Sin evidencia no Hecho", "", "", "", ""],
];
bordered(plan.getRange("A13:I16"));
plan.getRange("A13:I16").format = { fill: C.white, font: { color: C.slate }, wrapText: true, verticalAlignment: "center", borders: { preset: "all", style: "thin", color: C.line } };
const planWidths = [15,15,28,28,30,28,32,28,36];
planWidths.forEach((w, i) => plan.getRange(`${String.fromCharCode(65+i)}:${String.fromCharCode(65+i)}`).format.columnWidth = w);
plan.freezePanes.freezeRows(5);

// 04_Roles_UX
title(ux, "A1:G2", "Roles transversales de producto y experiencia", "A3:G3", "Estos roles piensan la experiencia antes de construir, la revisan durante el sprint y la validan al final. No cambian la propiedad técnica de los servicios.");
ux.getRange("A5:G5").values = [["Rol", "Asignado a", "Responsabilidad", "Inicio del sprint", "Durante el sprint", "Cierre", "Límite / nota"]];
header(ux.getRange("A5:G5"));
ux.getRange(`A6:G${5 + roles.length}`).values = roles;
bordered(ux.getRange(`A6:G${5 + roles.length}`));
ux.getRange("A6:G9").format.rowHeight = 86;
ux.getRange("A11:G11").merge();
ux.getRange("A11:G11").values = [["Sorteo realizado sin repetición entre Juanjo, Álvaro, Nicole y Jhon: Visual Designer → Nicole; Motion Designer → Álvaro."]];
ux.getRange("A11:G11").format = { fill: C.palePurple, font: { color: C.purple, bold: true }, wrapText: true, verticalAlignment: "center", borders: { preset: "outside", style: "medium", color: C.purple } };
ux.getRange("A13:G13").merge();
ux.getRange("A13:G13").values = [["Checklist conjunto para aprobar una pantalla"]];
ux.getRange("A13:G13").format = { fill: C.teal, font: { color: C.white, bold: true, size: 13 }, horizontalAlignment: "center" };
ux.getRange("A14:G19").values = [
  ["1", "Flujo", "Se entiende qué hacer y qué ocurrirá después.", "Interaction", "Franco", "Sí/No", ""],
  ["2", "Feedback", "Botones, carga, éxito y error muestran el estado real.", "Interaction", "Franco", "Sí/No", ""],
  ["3", "Jerarquía", "La acción principal y la información clínica importante destacan.", "Visual", "Nicole", "Sí/No", ""],
  ["4", "Accesibilidad", "Contraste, foco, etiquetas y mensajes son legibles.", "Visual", "Nicole", "Sí/No", ""],
  ["5", "Movimiento", "La transición explica el cambio y no distrae ni retrasa.", "Motion", "Álvaro", "Sí/No", ""],
  ["6", "Reduced motion", "La interfaz sigue funcionando con movimiento reducido.", "Motion", "Álvaro", "Sí/No", ""],
];
bordered(ux.getRange("A14:G19"));
ux.getRange("F14:F19").dataValidation = { rule: { type: "list", values: ["Pendiente", "Sí", "No"] } };
const uxWidths = [20,15,38,30,34,30,34];
uxWidths.forEach((w, i) => ux.getRange(`${String.fromCharCode(65+i)}:${String.fromCharCode(65+i)}`).format.columnWidth = w);
ux.freezePanes.freezeRows(5);

// 05_DoD_Riesgos
title(quality, "A1:G2", "Definition of Done, riesgos y reglas de decisión", "A3:G3", "Una tarea o historia no está Hecha solo porque compila o se mostró una vez: debe cumplir evidencia, contrato, seguridad e integración proporcional a su riesgo.");
section(quality, "A5:G5", "DEFINITION OF DONE", C.teal);
quality.getRange("A6").values = [["Dimensión"]];
quality.getRange("B6:D6").merge();
quality.getRange("B6:D6").values = [["Condición"]];
quality.getRange("E6:G6").merge();
quality.getRange("E6:G6").values = [["Evidencia mínima"]];
header(quality.getRange("A6:G6"));
for (let i = 0; i < dod.length; i++) {
  const row = 7 + i;
  quality.getRange(`A${row}`).values = [[dod[i][0]]];
  quality.getRange(`B${row}:D${row}`).merge();
  quality.getRange(`B${row}:D${row}`).values = [[dod[i][1]]];
  quality.getRange(`E${row}:G${row}`).merge();
  quality.getRange(`E${row}:G${row}`).values = [[dod[i][2]]];
}
bordered(quality.getRange(`A7:G${6 + dod.length}`));
quality.getRange(`A7:G${6 + dod.length}`).format.rowHeight = 48;

section(quality, "A17:G17", "RIESGOS DEL SPRINT", C.red);
quality.getRange("A18:G18").values = [["ID", "Riesgo", "Probabilidad", "Impacto", "Mitigación / regla de recorte", "Dueño", "Estado"]];
header(quality.getRange("A18:G18"));
quality.getRange(`A19:G${18 + risks.length}`).values = risks;
bordered(quality.getRange(`A19:G${18 + risks.length}`));
quality.getRange(`A19:G${18 + risks.length}`).format.rowHeight = 56;
quality.getRange(`C19:D${18 + risks.length}`).dataValidation = { rule: { type: "list", values: ["Baja", "Media", "Alta"] } };
quality.getRange(`G19:G${18 + risks.length}`).dataValidation = { rule: { type: "list", values: ["Abierto", "Mitigado", "Materializado", "Cerrado"] } };
quality.getRange(`C19:D${18 + risks.length}`).conditionalFormats.add("containsText", { text: "Alta", format: { fill: C.paleRed, font: { color: C.red, bold: true } } });
quality.getRange(`G19:G${18 + risks.length}`).conditionalFormats.add("containsText", { text: "Cerrado", format: { fill: C.paleGreen, font: { color: C.green, bold: true } } });

section(quality, "A28:G28", "CRITERIOS DE ÉXITO DEL SPRINT", C.blue);
quality.getRange("A29:G29").values = [["ID", "Resultado", "Condición verificable", "Nivel", "Evidencia", "Dueño", "Estado"]];
header(quality.getRange("A29:G29"));
quality.getRange("A30:G35").values = [
  ["E1", "Recorrido vertical", "Login→paciente→profesional→cita→atención→estudio→documento→aviso manual→factura", "Obligatorio", "E2E por gateway", "Nicole", "Pendiente"],
  ["E2", "Integración real", "Perfil integrated, clientes remotos activos y sin dobles silenciosos", "Obligatorio", "Pruebas de caída", "Nicole", "Pendiente"],
  ["E3", "Seguridad", "JWT/roles/tenant y revocación en rutas privadas", "Obligatorio", "Pruebas A/B y token revocado", "Álvaro", "Pendiente"],
  ["E4", "Contratos", "Rutas/JSON/error/correlation ID congelados y versionados", "Obligatorio", "docs/contracts + contract tests", "Juanjo", "Pendiente"],
  ["E5", "Calidad", "Suites verdes; skips justificados; evidencia 15/15", "Obligatorio", "Reportes y carpeta demo", "Jhon", "Pendiente"],
  ["E6", "Experiencia", "Flujo claro, visual coherente y movimiento funcional/accesible", "Obligatorio", "Aprobación de los tres roles", "Franco", "Pendiente"],
];
bordered(quality.getRange("A30:G35"));
quality.getRange("G30:G35").dataValidation = { rule: { type: "list", values: ["Pendiente", "En curso", "Cumplido", "No cumplido"] } };

const qWidths = [13,25,45,16,38,15,15];
qWidths.forEach((w, i) => quality.getRange(`${String.fromCharCode(65+i)}:${String.fromCharCode(65+i)}`).format.columnWidth = w);
quality.freezePanes.freezeRows(3);

// Workbook-level finishing and validation
const sheets = [summary, hus, backlog, plan, ux, quality];
for (const sheet of sheets) {
  const used = sheet.getUsedRange();
  used.format.font = { name: "Aptos", color: C.navy, size: 10 };
}
// Re-apply title/header fonts after global font family.
summary.getRange("A1:N2").format.font = { name: "Aptos Display", color: C.white, bold: true, size: 24 };
hus.getRange("A1:M2").format.font = { name: "Aptos Display", color: C.white, bold: true, size: 24 };
backlog.getRange("A1:M2").format.font = { name: "Aptos Display", color: C.white, bold: true, size: 24 };
plan.getRange("A1:I2").format.font = { name: "Aptos Display", color: C.white, bold: true, size: 24 };
ux.getRange("A1:G2").format.font = { name: "Aptos Display", color: C.white, bold: true, size: 24 };
quality.getRange("A1:G2").format.font = { name: "Aptos Display", color: C.white, bold: true, size: 24 };

await fs.mkdir(outputDir, { recursive: true });
for (const sheet of sheets) {
  const preview = await wb.render({ sheetName: sheet.name, autoCrop: "all", scale: 1, format: "png" });
  const safe = sheet.name.replaceAll("_", "-");
  await fs.writeFile(path.join(outputDir, `preview-${safe}.png`), new Uint8Array(await preview.arrayBuffer()));
}

const overview = await wb.inspect({ kind: "sheet,table", include: "id,name", maxChars: 6000, tableMaxRows: 4, tableMaxCols: 8 });
console.log("INSPECT_OVERVIEW");
console.log(overview.ndjson ?? overview);
const summaryCheck = await wb.inspect({ kind: "region", sheetId: summary.name, range: "A1:N37", maxChars: 6000 });
console.log("INSPECT_SUMMARY");
console.log(summaryCheck.ndjson ?? summaryCheck);
const storyCheck = await wb.inspect({ kind: "region", sheetId: hus.name, range: "A5:M19", maxChars: 5000 });
console.log("INSPECT_STORIES");
console.log(storyCheck.ndjson ?? storyCheck);
const taskCheck = await wb.inspect({ kind: "region", sheetId: backlog.name, range: "A5:M18", maxChars: 5000 });
console.log("INSPECT_TASKS_SAMPLE");
console.log(taskCheck.ndjson ?? taskCheck);
const formulaErrors = await wb.inspect({ kind: "match", searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A", options: { useRegex: true, maxResults: 100 }, maxChars: 5000 });
console.log("FORMULA_ERRORS");
console.log(formulaErrors.ndjson ?? formulaErrors);

const xlsx = await SpreadsheetFile.exportXlsx(wb);
await xlsx.save(outputPath);
console.log(`OUTPUT=${outputPath}`);
