from pathlib import Path
from docx import Document
from docx.shared import Inches, Pt, RGBColor

OUT = Path(__file__).resolve().parents[1] / "docs" / "seguridad"
OUT.mkdir(parents=True, exist_ok=True)

def setup(title, intro):
    doc = Document()
    sec = doc.sections[0]
    sec.top_margin = Inches(.7)
    sec.bottom_margin = Inches(.7)
    sec.left_margin = Inches(.8)
    sec.right_margin = Inches(.8)
    normal = doc.styles["Normal"]
    normal.font.name = "Aptos"
    normal.font.size = Pt(10)
    normal.font.color.rgb = RGBColor(35, 48, 62)
    doc.styles["Title"].font.name = "Aptos Display"
    doc.styles["Title"].font.size = Pt(24)
    doc.styles["Title"].font.color.rgb = RGBColor(20, 63, 88)
    doc.add_paragraph(title, style="Title")
    doc.add_paragraph(intro)
    return doc

def bullets(doc, values):
    for value in values:
        doc.add_paragraph(value, style="List Bullet")

pr = setup("Procedimiento de gestion de vulnerabilidades",
    "Este procedimiento establece una secuencia repetible para detectar, priorizar, corregir y cerrar vulnerabilidades de MedicalSys. Se aplica al backend medicalsys-platform_v1 y al frontend medicalsys_frontend antes de una entrega y en cada ciclo periódico.")
pr.add_heading("1. Preparar el entorno", level=1)
pr.add_paragraph("Usar una rama de trabajo actualizada y conservar el estado inicial del codigo. Mantener secretos reales en un gestor de secretos o en .env ignorado por Git; nunca incluirlos en reportes.")
pr.add_paragraph("Desde la raiz del backend, cargar el entorno local y comprobar Compose:")
pr.add_paragraph("Copy-Item .env.example .env # completar credenciales de desarrollo fuera del control de versiones\ndocker compose config --quiet\ndocker compose up -d --build\ndocker compose ps", style="No Spacing")
pr.add_heading("2. Ejecutar los escaneos", level=1)
pr.add_paragraph("Guardar salida fechada en docs/seguridad/escaneos/antes/ o despues/. Para el escaneo posterior repetir exactamente las herramientas, versiones, alcance y opciones del escaneo inicial.")
pr.add_paragraph("OWASP ZAP Baseline contra el gateway:")
pr.add_paragraph("docker run --rm --network host -v \"${PWD}/docs/seguridad/escaneos/antes:/zap/wrk:rw\" ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t http://localhost:8080 -J zap-baseline.json -r zap-baseline.html", style="No Spacing")
pr.add_paragraph("Si se dispone de una especificacion OpenAPI, ejecutar tambien el API scan indicando la URL del documento y conservar JSON y HTML. En Windows Docker Desktop, usar el modo de red accesible al contenedor y documentar cualquier diferencia de conectividad.")
pr.add_paragraph("Trivy sobre cada imagen local y sobre el filesystem con los POM:")
pr.add_paragraph("docker image ls medicalsys/*:local --format '{{.Repository}}:{{.Tag}}'\ntrivy image --severity HIGH,CRITICAL --format json --output trivy-image.json medicalsys/ms-gateway:local\ntrivy fs --scanners vuln --severity HIGH,CRITICAL --format json --output trivy-fs.json .", style="No Spacing")
pr.add_paragraph("Enumerar y escanear todos los POM; repetir trivy image por cada etiqueta local medicalsys/*:local. Registrar version de Trivy, fecha UTC, commit, comandos, salida y errores.")
pr.add_heading("3. Clasificar y asignar", level=1)
pr.add_paragraph("Deduplicar por CVE o identificador ZAP y componente afectado. Registrar severidad original, CVSS, vector, explotabilidad, exposicion, impacto en confidencialidad/integridad/disponibilidad y responsable. Una severidad del scanner no se cambia sin justificar el contexto.")
table = pr.add_table(rows=1, cols=2)
table.style = "Light Shading Accent 1"
table.rows[0].cells[0].text = "Severidad"
table.rows[0].cells[1].text = "Plazo objetivo de correccion"
for a,b in [("Critica (CVSS 9.0-10.0)","72 horas"),("Alta (CVSS 7.0-8.9)","7 dias"),("Media (CVSS 4.0-6.9)","30 dias"),("Baja (CVSS 0.1-3.9)","90 dias")]:
    cells=table.add_row().cells; cells[0].text=a; cells[1].text=b
pr.add_paragraph("Si no hay CVSS, el responsable de seguridad asigna una severidad documentada basada en impacto y explotabilidad. Las excepciones requieren aprobacion del propietario del sistema, justificacion, mitigaciones y fecha de expiracion.")
pr.add_heading("4. Corregir y revisar", level=1)
bullets(pr,["Crear un issue por hallazgo o grupo de hallazgos relacionados, con responsable y fecha objetivo.","Actualizar dependencias a una version corregida compatible; minimizar cambios no relacionados.","Toda correccion requiere revision de otra persona, analisis de impacto y evidencia automatizada o manual pertinente.","Proteger secretos, eliminar credenciales expuestas y rotarlas cuando corresponda; no basta con borrarlas del ultimo commit.","Usar OWASP Top 10 como referencia para revisar controles de acceso, configuracion, criptografia, integridad y autenticacion."])
pr.add_heading("5. Reescanear y cerrar", level=1)
pr.add_paragraph("Repetir los escaneos con configuracion equivalente y guardar la salida en la carpeta despues. Vincular cada hallazgo con el commit o PR que lo corrige. Cerrar solo si el escaneo confirma la ausencia, una prueba demuestra la mitigacion o existe una aceptacion de riesgo aprobada y vigente. Registrar falsos positivos con evidencia.")
pr.add_heading("6. Evidencia y comandos del proyecto", level=1)
pr.add_paragraph("Conservar reportes originales, resumen por severidad antes/despues, inventario de imagenes, versiones de herramientas, commit escaneado, fecha, incidencias, responsables y relacion hallazgo-correccion. No completar conteos sin resultados observados.")
pr.add_paragraph("Para actualizar imagenes antes del escaneo: docker compose build --pull. Para verificar el estado: docker compose ps. El pipeline de dependencias debe cubrir todos los pom.xml de services y package-lock.json del frontend; reportar por separado cada manifiesto.")
pr.save(OUT / "PR-04.docx")

po = setup("Politica de gestion de vulnerabilidades y desarrollo seguro",
    "MedicalSys mantiene un ciclo continuo de identificacion y tratamiento de vulnerabilidades para reducir el riesgo en el software, sus dependencias y su despliegue. Esta politica aplica al codigo backend y frontend, imagenes, configuracion de infraestructura y datos de desarrollo.")
po.add_heading("1. Responsabilidades", level=1)
po.add_paragraph("El propietario tecnico coordina los escaneos y asigna responsables. Cada responsable corrige los hallazgos dentro del plazo objetivo. Un revisor distinto al autor verifica los cambios. El propietario del sistema aprueba cualquier riesgo residual con fecha de expiracion.")
po.add_heading("2. Escaneos", level=1)
bullets(po,["En cada version candidata: OWASP ZAP Baseline contra el gateway; API scan si existe una especificacion OpenAPI accesible.","En cada version candidata: Trivy sobre todas las imagenes medicalsys/*:local y filesystem/dependencias, incluyendo pom.xml y package-lock.json.","Al menos mensualmente y ante una alerta relevante: repetir escaneos de dependencias e imagenes.","Conservar reportes fechados antes y despues de cada remediacion, configuracion y version de las herramientas."])
po.add_heading("3. Priorizacion y plazos", level=1)
po.add_paragraph("Usar CVSS cuando este disponible y considerar exposicion, explotabilidad, datos tratados e impacto sobre pacientes. Objetivos maximos: critica 72 horas; alta 7 dias; media 30 dias; baja 90 dias. Si no es posible cumplirlos, aplicar mitigaciones y solicitar aceptacion de riesgo con vencimiento.")
po.add_heading("4. Desarrollo seguro y revision", level=1)
bullets(po,["Todo cambio de codigo requiere revision por una persona distinta del autor antes de integrarse.","Las revisiones consideran OWASP Top 10, especialmente control de acceso, configuracion, autenticacion, integridad y registro seguro.","Los endpoints internos no se publican al navegador; la red y la autenticacion de servicio limitan su alcance.","Los cambios de dependencias deben justificar version, compatibilidad y resultado del escaneo.","Las correcciones de seguridad se verifican mediante reescaneo y pruebas enfocadas en el hallazgo."])
po.add_heading("5. Secretos y datos", level=1)
bullets(po,["No guardar secretos en codigo, reportes, commits ni archivos de ejemplo. Usar el gestor de secretos del entorno y valores ficticios en .env.example.","Restringir acceso a secretos, rotarlos tras exposicion y evitar que aparezcan en logs.","Usar datos sinteticos en desarrollo y pruebas; no copiar historias clinicas o identificadores reales."])
po.add_heading("6. Excepciones y cumplimiento", level=1)
po.add_paragraph("El propietario del sistema registra hallazgo, razon, impacto, controles compensatorios, responsable y fecha de expiracion. La excepcion no elimina el hallazgo: se revisa en el siguiente ciclo y se cierra al corregirlo.")
po.add_paragraph("La ejecucion operativa, asignacion, remediacion, reescaneo y cierre se realizan conforme al Procedimiento de gestion de vulnerabilidades PR-04.")
po.save(OUT / "PO-07.docx")
