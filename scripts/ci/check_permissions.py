#!/usr/bin/env python3
"""Cada endpoint declara su control de acceso, y es el que dice la matriz.

La hoja endpoint -> permiso de RBAC-01 (docs/seguridad/rbac-01-endpoints-permisos.csv) es la
referencia de la matriz de roles y accesos (RBAC-06) y de la politica PO-02. Los servicios deniegan
por defecto (RBAC-04), asi que un endpoint sin anotacion responde 403; este chequeo lo detecta antes
del merge. Falla si:
- un endpoint no declara exactamente uno de @RequiresPermission, @PublicEndpoint o @InternalEndpoint;
- el control no coincide con la hoja: el permiso de @RequiresPermission, PUBLICO para
  @PublicEndpoint, INTERNO y ruta /internal/ para @InternalEndpoint;
- el endpoint no figura en la hoja, o la columna control_actual de la hoja quedo desactualizada;
- queda algun @RequiresRole;
- ms-identidad, que autoriza con Spring Security, no termina su configuracion en denyAll().

Uso: python3 scripts/ci/check_permissions.py
"""
import csv
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SHEET = ROOT / "docs" / "seguridad" / "rbac-01-endpoints-permisos.csv"
IDENTITY_SECURITY = ROOT / "services/ms-identidad/src/main/java/com/medicalsys/identidad/config/SecurityConfig.java"
METHOD = re.compile(r"((?:\s*@[\w.]+(?:\((?:[^()]|\([^()]*\))*\))?)+)\s*(?:public\s+)?[\w<>\[\],?\s.]+?\s+(\w+)\s*\(")
MAPPING = re.compile(r"@(Get|Post|Put|Patch|Delete|Request)Mapping(?:\(((?:[^()]|\([^()]*\))*)\))?")
PERMISSION = re.compile(r'@RequiresPermission\(\s*"([^"]+)"\s*\)')
CLASS = re.compile(r"^(?:public\s+)?(?:final\s+)?class\s+\w+", re.M)
EXPECTED_CONTROL = {"PERMISO": "@RequiresPermission {permiso}", "PUBLICO": "@PublicEndpoint",
                    "INTERNO": "@InternalEndpoint"}


def paths(args):
    cleaned = re.sub(r'(produces|consumes|params|headers)\s*=\s*(\{[^}]*\}|[A-Za-z_.]+|"[^"]*")', "", args or "")
    return re.findall(r'"([^"]*)"', cleaned) or [""]


def declared(annotations):
    """Controles de acceso que declara un metodo: [(tipo, permiso)]."""
    found = []
    permission = PERMISSION.search(annotations)
    if permission:
        found.append(("PERMISO", permission.group(1)))
    if re.search(r"@PublicEndpoint\b", annotations):
        found.append(("PUBLICO", ""))
    if re.search(r"@InternalEndpoint\b", annotations):
        found.append(("INTERNO", ""))
    return found


def endpoints():
    for path in sorted((ROOT / "services").glob("ms-*/src/main/java/**/*.java")):
        source = path.read_text(encoding="utf-8")
        if "Mapping" not in source or "@interface" in source:
            continue
        declaration = CLASS.search(source)
        if not declaration:
            continue
        head = source[:declaration.start()]
        base = re.search(r'@RequestMapping\s*\(\s*(?:value\s*=\s*|path\s*=\s*)?\{?\s*"([^"]*)"', head[head.rfind("import "):])
        service = path.relative_to(ROOT / "services").parts[0]
        for method in METHOD.finditer(source, declaration.end()):
            annotations = method.group(1)
            mapping = MAPPING.search(annotations)
            if not mapping:
                continue
            verb = mapping.group(1).upper()
            if verb == "REQUEST":
                verb = "/".join(re.findall(r"RequestMethod\.(\w+)", mapping.group(2) or "")) or "ANY"
            for suffix in paths(mapping.group(2)):
                route = (base.group(1) if base else "") + suffix or "/"
                yield service, verb, route, declared(annotations), path.relative_to(ROOT)


def main():
    rows = list(csv.DictReader(SHEET.open(encoding="utf-8")))
    sheet = {(row["servicio"], row["metodo"], row["ruta"]): row for row in rows}
    errors, counts = [], {"PERMISO": 0, "PUBLICO": 0, "INTERNO": 0}

    for service, verb, route, controls, path in endpoints():
        if service == "ms-identidad":
            continue  # Autoriza con Spring Security; se comprueba abajo.
        where = f"{path}: {verb} {route}"
        row = sheet.get((service, verb, route))
        if row is None:
            errors.append(f"{where} no figura en la hoja de RBAC-01")
            continue
        if len(controls) != 1:
            errors.append(f"{where} debe declarar exactamente uno de @RequiresPermission, @PublicEndpoint o "
                          f"@InternalEndpoint (tiene {len(controls)}); sin anotacion se deniega por defecto")
            continue
        kind, permission = controls[0]
        counts[kind] += 1
        if kind != row["tipo_acceso"]:
            errors.append(f"{where} se declara {kind} y la hoja dice {row['tipo_acceso']}")
        elif kind == "PERMISO" and permission != row["permiso"]:
            errors.append(f"{where} exige {permission} y la hoja dice {row['permiso']}")
        elif kind == "INTERNO" and not route.startswith("/internal/"):
            errors.append(f"{where} es @InternalEndpoint fuera de /internal/")
        expected = EXPECTED_CONTROL[row["tipo_acceso"]].format(permiso=row["permiso"])
        if row["control_actual"] != expected:
            errors.append(f"{where}: la hoja dice control_actual={row['control_actual']!r} y deberia ser {expected!r}")

    for path in sorted((ROOT / "services").glob("ms-*/src/main/java/**/*.java")):
        if "@RequiresRole" in path.read_text(encoding="utf-8"):
            errors.append(f"{path.relative_to(ROOT)}: usa @RequiresRole; los endpoints se autorizan por permiso")

    security = IDENTITY_SECURITY.read_text(encoding="utf-8")
    if not re.search(r"\.anyRequest\(\)\s*\.denyAll\(\)", security):
        errors.append(f"{IDENTITY_SECURITY.relative_to(ROOT)}: la ultima regla debe ser anyRequest().denyAll()")
    for row in rows:
        if row["servicio"] == "ms-identidad" and row["tipo_acceso"] == "PERMISO" \
                and row["control_actual"].startswith("authority") and f'"{row["permiso"]}"' not in security:
            errors.append(f"{IDENTITY_SECURITY.relative_to(ROOT)}: no exige {row['permiso']} "
                          f"para {row['metodo']} {row['ruta']}")

    if errors:
        print("Control de acceso distinto de la matriz:")
        print("\n".join(f"  - {error}" for error in errors))
        return 1
    print(f"{sum(counts.values())} endpoints declaran su control y coinciden con la hoja de RBAC-01: "
          f"{counts['PERMISO']} con permiso, {counts['PUBLICO']} publicos y {counts['INTERNO']} internos. "
          "ms-identidad deniega por defecto.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
