"""Inserta las secciones 11 (Top Ten OWASP) y 13 (reporte del escaneo) en el informe de SIS 321.

Sustituye los parrafos "[Pendiente]" de esas dos secciones por su contenido, copiando los estilos del
propio documento (titulos, parrafos y tablas). No toca ninguna otra seccion. Las cifras de la seccion 13
se leen de los reportes guardados mediante docs/seguridad/escaneos/resumen.py.

Uso: python scripts/fill_report_sections.py <informe.docx> <salida.docx>
"""
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
SCANS = ROOT / "docs" / "seguridad" / "escaneos"
sys.path.insert(0, str(SCANS))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from create_owasp_report import CONCLUSION, INTRO, ITEMS  # noqa: E402
from resumen import summarize  # noqa: E402

P_OPEN = '<w:p w:rsidR="00000000" w:rsidDel="00000000" w:rsidP="00000000" w:rsidRDefault="00000000" w:rsidRPr="00000000">'
R_OPEN = '<w:r w:rsidDel="00000000" w:rsidR="00000000" w:rsidRPr="00000000">'
BORDER = '<w:{side} w:color="000000" w:space="0" w:sz="6" w:val="single"/>'
TABLE_WIDTH = 9345


def run(text, bold=False):
    props = '<w:b w:val="1"/><w:bCs w:val="1"/>' if bold else ""
    return f'{R_OPEN}<w:rPr>{props}<w:rtl w:val="0"/></w:rPr><w:t xml:space="preserve">{escape(text)}</w:t></w:r>'


def para(text, label=None):
    """Parrafo de cuerpo con el mismo formato que el resto del informe; label va en negrita."""
    runs = (run(label + " ", bold=True) if label else "") + run(text)
    return f'{P_OPEN}<w:pPr><w:jc w:val="center"/><w:rPr/></w:pPr>{runs}</w:p>'


def heading2(text):
    return (f'{P_OPEN}<w:pPr><w:pStyle w:val="Heading2"/><w:keepNext w:val="0"/>'
            '<w:spacing w:after="80" w:before="360" w:lineRule="auto"/><w:jc w:val="center"/>'
            '<w:rPr><w:sz w:val="34"/><w:szCs w:val="34"/></w:rPr></w:pPr>'
            f'{R_OPEN}<w:rPr><w:sz w:val="34"/><w:szCs w:val="34"/><w:rtl w:val="0"/></w:rPr>'
            f'<w:t xml:space="preserve">{escape(text)}</w:t></w:r></w:p>')


def table(rows, widths):
    """Tabla con cabecera gris y bordes simples, como las del informe. widths debe sumar TABLE_WIDTH."""
    assert sum(widths) == TABLE_WIDTH, sum(widths)
    grid = "".join(f'<w:gridCol w:w="{w}"/>' for w in widths)
    borders = "".join(BORDER.format(side=s) for s in ("top", "left", "bottom", "right"))
    body = []
    for r, row in enumerate(rows):
        cells = []
        for width, text in zip(widths, row):
            shade = '<w:shd w:fill="f2f2f2" w:val="clear"/>' if r == 0 else ""
            cells.append(
                f'<w:tc><w:tcPr><w:tcW w:w="{width}" w:type="dxa"/><w:tcBorders>{borders}</w:tcBorders>{shade}'
                '<w:tcMar><w:top w:w="40" w:type="dxa"/><w:left w:w="100" w:type="dxa"/>'
                '<w:bottom w:w="40" w:type="dxa"/><w:right w:w="100" w:type="dxa"/></w:tcMar>'
                f'<w:vAlign w:val="top"/></w:tcPr>{P_OPEN}<w:pPr><w:rPr/></w:pPr>{run(str(text), bold=r == 0)}</w:p></w:tc>')
        body.append('<w:tr><w:trPr><w:cantSplit w:val="1"/></w:trPr>' + "".join(cells) + "</w:tr>")
    return ('<w:tbl><w:tblPr><w:tblStyle w:val="Table1"/>'
            f'<w:tblW w:w="{TABLE_WIDTH}" w:type="dxa"/><w:jc w:val="left"/><w:tblLayout w:type="fixed"/>'
            f'<w:tblLook w:val="0600"/></w:tblPr><w:tblGrid>{grid}</w:tblGrid>{"".join(body)}</w:tbl>'
            f'{P_OPEN}<w:pPr><w:rPr/></w:pPr></w:p>')


def image(rel_id, name, width_emu, height_emu, doc_pr_id):
    return (f'{P_OPEN}<w:pPr><w:jc w:val="center"/><w:rPr/></w:pPr>{R_OPEN}<w:rPr><w:rtl w:val="0"/></w:rPr><w:drawing>'
            f'<wp:inline distB="114300" distT="114300" distL="114300" distR="114300"><wp:extent cx="{width_emu}" cy="{height_emu}"/>'
            f'<wp:effectExtent b="0" l="0" r="0" t="0"/><wp:docPr id="{doc_pr_id}" name="{name}"/><a:graphic>'
            '<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr>'
            f'<pic:cNvPr id="0" name="{name}"/><pic:cNvPicPr preferRelativeResize="0"/></pic:nvPicPr><pic:blipFill>'
            f'<a:blip r:embed="{rel_id}"/><a:srcRect b="0" l="0" r="0" t="0"/><a:stretch><a:fillRect/></a:stretch>'
            f'</pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{width_emu}" cy="{height_emu}"/></a:xfrm>'
            '<a:prstGeom prst="rect"/><a:ln/></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline>'
            '</w:drawing></w:r></w:p>')


def section_11():
    parts = [para(INTRO)]
    for title, applies, prevention in ITEMS:
        number, rest = title.split(". ", 1)
        parts.append(heading2(f"11.{number} {rest}"))
        parts.append(para(applies, "¿Aplica en el sistema?:"))
        parts.append(para(prevention, "¿Cómo lo estamos previniendo?:"))
    parts.append(heading2("11.11 Evidencia antes y después de las correcciones"))
    parts.append(para(
        "La tabla resume las pruebas que repetimos sobre la rama develop y sobre la versión corregida, con las "
        "mismas peticiones en ambos casos. La salida completa de cada una está en el repositorio."))
    parts.append(table([
        ["Categoría", "Prueba", "Antes", "Después"],
        ["A01", "Petición directa al puerto 8088 con un rol inventado y sin token", "200, con datos clínicos", "Sin conexión"],
        ["A02", "Preflight de CORS desde un dominio ajeno", "200, origen aceptado", "403"],
        ["A02", "Ruta interna de identidad sin autenticación", "200, con datos del usuario", "401"],
        ["A07", "Mismo token después de cerrar sesión, en historial y pacientes", "200", "401"],
        ["A08", "Mensaje al webhook de WhatsApp sin firma", "200", "401"],
    ], [1200, 4345, 2000, 1800]))
    parts.append(heading2("11.12 Conclusión"))
    parts.append(para(CONCLUSION))
    return "".join(parts)


def section_13(image_xml):
    before, after = summarize(SCANS / "antes"), summarize(SCANS / "despues")

    def zap(totals, risk):
        return sum(count for level, _, count in totals["zap"] if level == risk)

    b_img, a_img, b_fs, a_fs = before["image"], after["image"], before["fs"], after["fs"]
    crit_before = b_img["CRITICAL"] + b_fs["CRITICAL"]
    high_before = b_img["HIGH"] + b_fs["HIGH"]
    crit_after = a_img["CRITICAL"] + a_fs["CRITICAL"]
    high_after = a_img["HIGH"] + a_fs["HIGH"]

    def pkg(totals, names, severity=None):
        return sum(count for (name, _, sev), count in totals["packages"].items()
                   if name in names and (severity is None or sev == severity))

    jackson, bouncy = ("jackson-databind", "jackson-core"), ("bcprov-jdk18on",)
    components = [
        ("Tomcat 11.0.24", "Crítica", ("tomcat-embed-core",), "CRITICAL", "Tomcat 11.0.25"),
        ("Bouncy Castle 1.78.1, 1.81.1 y 1.84", "Crítica", bouncy, "CRITICAL", "Bouncy Castle 1.85"),
        ("Bouncy Castle 1.78.1, 1.81.1 y 1.84", "Alta", bouncy, "HIGH", "Bouncy Castle 1.85"),
        ("Jackson 3.1.5 y 2.21.5", "Alta", jackson, "HIGH", "Jackson 3.1.7 y 2.21.7"),
        ("libssl3 de Ubuntu 22.04", "Alta", ("libssl3",), "HIGH", "Actualización del sistema en el Dockerfile"),
        ("lz4-java 1.10.1", "Alta", ("lz4-java",), "HIGH", "lz4-java 1.11.4"),
    ]

    parts = [
        para("Esta sección presenta el escaneo de vulnerabilidades que hicimos antes y después de las "
             "correcciones. Todas las cifras salen de los reportes que quedaron guardados en el repositorio, en "
             "la carpeta docs/seguridad/escaneos, y se pueden volver a obtener con el script resumen.py de esa "
             "misma carpeta. Los reportes completos van en el Anexo I."),
        heading2("13.1 Herramientas utilizadas y configuración"),
        para("Usamos dos herramientas que se complementan. Trivy revisa lo que el sistema lleva dentro: las "
             "librerías declaradas en los catorce archivos pom.xml y el contenido de las catorce imágenes de "
             "contenedor, incluidos los paquetes del sistema operativo. OWASP ZAP revisa el sistema desde afuera, "
             "con el gateway en ejecución, y analiza de forma pasiva las respuestas que recibe. Las dos se "
             "ejecutaron desde sus imágenes oficiales de Docker, con un único script, de modo que el escaneo "
             "inicial y el posterior usaron exactamente las mismas opciones."),
        table([
            ["Herramienta", "Versión", "Qué analiza", "Configuración"],
            ["Trivy filesystem", "0.69.3", "Dependencias de los 14 pom.xml", "Escáner de vulnerabilidades, severidades alta y crítica"],
            ["Trivy image", "0.69.3", "Las 14 imágenes medicalsys/*", "Escáner de vulnerabilidades, severidades alta y crítica"],
            ["OWASP ZAP Baseline", "2.17.0", "El gateway en ejecución", "Araña de 1 minuto y escaneo pasivo, sin autenticación"],
        ], [2000, 1000, 3000, 3345]),
        para("El escaneo inicial se hizo sobre la rama develop, en el commit baef748, reconstruyendo las imágenes "
             "desde ese punto para que ninguna corrección estuviera incluida. El escaneo posterior se hizo sobre "
             "la rama con las correcciones integradas. No usamos OWASP Dependency-Check porque Trivy filesystem "
             "revisa los mismos archivos, y no ejecutamos el escaneo de API de ZAP porque la plataforma no publica "
             "una especificación OpenAPI. El procedimiento paso a paso está en el documento PR-04."),
        heading2("13.2 Resultados del escaneo inicial"),
        para(f"El primer escaneo encontró {crit_before} hallazgos críticos y {high_before} altos. Casi todos "
             f"estaban en las imágenes: {b_img['CRITICAL']} críticos y {b_img['HIGH']} altos. En los pom.xml "
             f"aparecieron {b_fs['CRITICAL']} críticos y {b_fs['HIGH']} alto, los tres de la librería Bouncy "
             "Castle que usa el servicio de identidad. ZAP no reportó ningún fallo grave, pero sí "
             f"{zap(before, 'Low')} alertas de severidad baja y {zap(before, 'Informational')} informativas."),
        table([
            ["Herramienta", "Crítica", "Alta", "Baja", "Informativa"],
            ["Trivy filesystem", b_fs["CRITICAL"], b_fs["HIGH"], "—", "—"],
            ["Trivy imágenes (14)", b_img["CRITICAL"], b_img["HIGH"], "—", "—"],
            ["OWASP ZAP Baseline", 0, 0, zap(before, "Low"), zap(before, "Informational")],
        ], [3345, 1500, 1500, 1500, 1500]),
        para("El número parece alto, pero conviene leerlo con cuidado. Trivy cuenta una ocurrencia por cada "
             "imagen que contiene la librería vulnerable, y como los catorce servicios comparten la misma base de "
             "Spring Boot, una sola librería desactualizada aparece hasta catorce veces. Al agruparlos, todos los "
             "hallazgos de las imágenes se reducen a cinco componentes."),
        table([
            ["Componente", "Severidad", "Ocurrencias", "Identificadores CVE"],
            ["Tomcat 11.0.24", "Crítica", pkg(before, ("tomcat-embed-core",)), "CVE-2026-65182, CVE-2026-65905, CVE-2026-68525"],
            ["Jackson 3.1.5 y 2.21.5", "Alta", pkg(before, jackson), "CVE-2026-68497, CVE-2026-89407, CVE-2026-89425, CVE-2026-91776, CVE-2026-91777"],
            ["libssl3 de Ubuntu 22.04", "Alta", pkg(before, ("libssl3",)), "CVE-2026-84782"],
            ["Bouncy Castle 1.78.1, 1.81.1 y 1.84", "Crítica y alta", pkg(before, bouncy), "CVE-2025-14813, CVE-2026-8763, CVE-2026-13506"],
            ["lz4-java 1.10.1", "Alta", pkg(before, ("lz4-java",)), "CVE-2026-106451"],
        ], [2700, 1400, 1300, 3945]),
        para("Las alertas de ZAP fueron la ausencia de la cabecera Cross-Origin-Resource-Policy en cuatro "
             "direcciones del gateway y una nota informativa sobre contenido que no se guarda en caché."),
        heading2("13.3 Correcciones realizadas"),
        para("Como el origen eran pocos componentes, la corrección consistió en subir cada uno a la versión que "
             "ya no tiene la vulnerabilidad. En los pom.xml fijamos de forma explícita las versiones de Tomcat, "
             "Jackson, Bouncy Castle y lz4-java, porque varias venían heredadas de Spring Boot. Para la librería "
             "del sistema operativo, los Dockerfile ahora aplican las actualizaciones de seguridad de Ubuntu al "
             "construir cada imagen. La alerta de ZAP se resolvió agregando la cabecera que faltaba en el filtro "
             "del gateway, junto con el resto de cabeceras de seguridad que describimos en la sección 11."),
        table([
            ["Hallazgo", "Corrección", "Dónde se aplicó"],
            ["Tomcat 11.0.24", "Versión 11.0.25", "pom.xml de los 14 servicios"],
            ["Jackson 3.1.5 y 2.21.5", "Versiones 3.1.7 y 2.21.7", "pom.xml de los 14 servicios"],
            ["Bouncy Castle", "Versión 1.85", "pom.xml de identidad, gateway y documental"],
            ["lz4-java 1.10.1", "Versión 1.11.4", "pom.xml de agenda, marketing, notificaciones y pacientes"],
            ["libssl3", "Actualización de paquetes del sistema", "Dockerfile de los 14 servicios"],
            ["Cabecera CORP ausente", "Cross-Origin-Resource-Policy: same-origin", "Filtro del gateway"],
        ], [2700, 3200, 3445]),
        heading2("13.4 Resultados del escaneo posterior"),
        para("Con las correcciones integradas reconstruimos las catorce imágenes y repetimos el escaneo sin "
             f"cambiar ninguna opción. Trivy reportó {crit_after} hallazgos críticos y {high_after} altos, tanto "
             "en los pom.xml como en las imágenes, y ZAP terminó sin alertas."),
        table([
            ["Herramienta", "Crítica", "Alta", "Baja", "Informativa"],
            ["Trivy filesystem", a_fs["CRITICAL"], a_fs["HIGH"], "—", "—"],
            ["Trivy imágenes (14)", a_img["CRITICAL"], a_img["HIGH"], "—", "—"],
            ["OWASP ZAP Baseline", 0, 0, zap(after, "Low"), zap(after, "Informational")],
        ], [3345, 1500, 1500, 1500, 1500]),
        heading2("13.5 Tabla comparativa"),
        table(
            [["Hallazgo", "Severidad", "Antes", "Después", "Corrección"]]
            + [["Trivy filesystem: Bouncy Castle 1.78.1", "Crítica", b_fs["CRITICAL"], a_fs["CRITICAL"], "Bouncy Castle 1.85"],
               ["Trivy filesystem: Bouncy Castle 1.78.1", "Alta", b_fs["HIGH"], a_fs["HIGH"], "Bouncy Castle 1.85"]]
            + [[f"Trivy imágenes: {name}", label, pkg(before, names, sev), pkg(after, names, sev), fix]
               for name, label, names, sev, fix in components]
            + [["ZAP: cabecera CORP ausente", "Baja", zap(before, "Low"), zap(after, "Low"), "Cabecera agregada en el gateway"],
               ["ZAP: contenido no almacenable", "Informativa", zap(before, "Informational"), zap(after, "Informational"), "Sin corrección dirigida"],
               ["Total crítico y alto", "", crit_before + high_before, crit_after + high_after, ""]],
            [3145, 1300, 900, 1000, 3000]),
        para("Todos los hallazgos críticos y altos quedaron corregidos, por lo que no fue necesario registrar "
             "ningún riesgo aceptado. Las correcciones de dependencias entraron al repositorio con el pull "
             "request 14; la cabecera que resolvió la alerta de ZAP se agregó después y va en un pull request "
             "posterior."),
        heading2("13.6 Gráfico por severidad"),
        image_xml,
        para("Figura 13.1. Hallazgos por herramienta y severidad, antes y después de las correcciones."),
        heading2("13.7 Limitaciones del escaneo"),
        para("El resultado es bueno, pero no queremos presentarlo como más de lo que es. Trivy se ejecutó solo "
             "para las severidades alta y crítica, así que pueden existir hallazgos medios y bajos que no "
             "aparecen aquí. ZAP hizo un escaneo pasivo y sin iniciar sesión, y solo alcanzó ocho direcciones del "
             "gateway, por lo que no evalúa la API que está detrás de la autenticación; esa parte la cubrimos con "
             "las pruebas dirigidas de la sección 11. La alerta informativa de ZAP dejó de aparecer sin que "
             "hiciéramos un cambio pensado para ella, y por eso no la contamos como una corrección. Por último, "
             "los escaneos se hicieron en un entorno local y no sobre el sistema publicado."),
    ]
    return "".join(parts)


def replace_placeholder(xml, marker, content):
    """Sustituye el parrafo que contiene el marcador [Pendiente] por el contenido generado."""
    at = xml.index(marker)
    start = max(xml.rfind("<w:p>", 0, at), xml.rfind("<w:p ", 0, at))
    end = xml.index("</w:p>", at) + len("</w:p>")
    return xml[:start] + content + xml[end:]


def main(source, target):
    work = Path(tempfile.mkdtemp())
    with zipfile.ZipFile(source) as archive:
        names = archive.namelist()
        archive.extractall(work)

    chart = SCANS / "hallazgos-por-severidad.png"
    shutil.copy(chart, work / "word" / "media" / "hallazgos-por-severidad.png")
    rels_path = work / "word" / "_rels" / "document.xml.rels"
    rels = rels_path.read_text(encoding="utf-8")
    rel_id = "rIdEscaneo1"
    rels = rels.replace("</Relationships>", (
        f'<Relationship Id="{rel_id}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" '
        'Target="media/hallazgos-por-severidad.png"/></Relationships>'))
    rels_path.write_text(rels, encoding="utf-8")

    # La imagen mide 1440 x 720 px (9 x 4,5 in a 160 ppp); se inserta a 6 pulgadas de ancho.
    drawing = image(rel_id, "hallazgos-por-severidad.png", 5486400, 2743200, 900)

    doc_path = work / "word" / "document.xml"
    xml = doc_path.read_text(encoding="utf-8")
    xml = replace_placeholder(xml, "[Pendiente] Al menos dos vulnerabilidades del OWASP Top 10:2025", section_11())
    xml = replace_placeholder(xml, "[Pendiente] Herramientas, resultados antes y después", section_13(drawing))
    doc_path.write_text(xml, encoding="utf-8")

    names.append("word/media/hallazgos-por-severidad.png")
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in names:
            if not name.endswith("/"):
                archive.write(work / name, name)
    shutil.rmtree(work)
    print(f"Secciones 11 y 13 insertadas en {target}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
