#!/usr/bin/env python3
"""El permiso que exige cada endpoint en el codigo es el que dice la matriz.

La hoja endpoint -> permiso de RBAC-01 (docs/seguridad/rbac-01-endpoints-permisos.csv) es la
referencia de la matriz de roles y accesos (RBAC-06) y de la politica PO-02. Este chequeo falla si:
- un @RequiresPermission declara un permiso distinto del que la hoja asigna a su endpoint, o el
  endpoint no figura en la hoja;
- queda algun @RequiresRole: los roles fijos en el codigo se reemplazaron por permisos (RBAC-03).

Informa, sin fallar, los endpoints que la hoja protege y el codigo todavia no (los cubre RBAC-04).

Uso: python3 scripts/ci/check_permissions.py
"""
import csv
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SHEET = ROOT / "docs" / "seguridad" / "rbac-01-endpoints-permisos.csv"
METHOD = re.compile(r"((?:\s*@[\w.]+(?:\((?:[^()]|\([^()]*\))*\))?)+)\s*(?:public\s+)?[\w<>\[\],?\s.]+?\s+(\w+)\s*\(")
MAPPING = re.compile(r"@(Get|Post|Put|Patch|Delete|Request)Mapping(?:\(((?:[^()]|\([^()]*\))*)\))?")
PERMISSION = re.compile(r'@RequiresPermission\(\s*"([^"]+)"\s*\)')
CLASS = re.compile(r"^(?:public\s+)?(?:final\s+)?class\s+\w+", re.M)


def paths(args):
    cleaned = re.sub(r'(produces|consumes|params|headers)\s*=\s*(\{[^}]*\}|[A-Za-z_.]+|"[^"]*")', "", args or "")
    return re.findall(r'"([^"]*)"', cleaned) or [""]


def endpoints():
    for path in sorted((ROOT / "services").glob("ms-*/src/main/java/**/*.java")):
        source = path.read_text(encoding="utf-8")
        if "Controller" not in source or "@interface" in source:
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
            permission = PERMISSION.search(annotations)
            for suffix in paths(mapping.group(2)):
                yield (service, verb, (base.group(1) if base else "") + suffix or "/",
                       permission.group(1) if permission else None, path.relative_to(ROOT))


def main():
    sheet = {(row["servicio"], row["metodo"], row["ruta"]): row["permiso"]
             for row in csv.DictReader(SHEET.open(encoding="utf-8"))}
    errors, pending, checked = [], [], 0
    for service, verb, route, permission, path in endpoints():
        expected = sheet.get((service, verb, route))
        if permission:
            checked += 1
            if expected is None:
                errors.append(f"{path}: {verb} {route} no figura en la hoja de RBAC-01")
            elif permission != expected:
                errors.append(f"{path}: {verb} {route} exige {permission} y la hoja dice {expected}")
        elif expected and service != "ms-identidad":
            # ms-identidad autoriza con Spring Security (SecurityConfig), no con anotaciones.
            pending.append(f"{service} {verb} {route}")

    for path in sorted((ROOT / "services").glob("ms-*/src/main/java/**/*.java")):
        if "@RequiresRole" in path.read_text(encoding="utf-8"):
            errors.append(f"{path.relative_to(ROOT)}: usa @RequiresRole; los endpoints se autorizan por permiso")

    if errors:
        print("Autorizacion distinta de la matriz:")
        print("\n".join(f"  - {error}" for error in errors))
        return 1
    print(f"{checked} endpoints con @RequiresPermission coinciden con la hoja de RBAC-01.")
    if pending:
        print(f"{len(pending)} endpoints con permiso en la hoja todavia sin anotar (RBAC-04).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
