#!/usr/bin/env python3
"""Servicios que el CI tiene que construir y probar.

Cada servicio es un proyecto Maven independiente, asi que basta con probar los que cambiaron: el repo
es privado y los minutos de GitHub Actions son limitados. Se prueban todos si se pide a mano, si no hay
un commit base con el cual comparar o si cambio el propio CI.

Uso: python3 scripts/ci/changed_services.py <sha_base|""> <sha_head> [--all]
Escribe services=<lista JSON> y any=<true|false> en $GITHUB_OUTPUT (o en la salida estandar).
"""
import json
import os
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
EVERYTHING = (".github/workflows/backend-ci.yml", "scripts/ci/")


def all_services():
    return sorted(path.name for path in (ROOT / "services").iterdir() if (path / "pom.xml").is_file())


def changed_files(base, head):
    diff = subprocess.run(["git", "-C", str(ROOT), "diff", "--name-only", f"{base}...{head}"],
                          check=True, capture_output=True, text=True)
    return diff.stdout.splitlines()


def main():
    args = [arg for arg in sys.argv[1:] if arg != "--all"]
    base, head = (args + ["", "HEAD"])[:2]
    services = all_services()

    if "--all" in sys.argv or not base or set(base) == {"0"}:
        selected = services
    else:
        files = changed_files(base, head)
        if any(path.startswith(EVERYTHING) for path in files):
            selected = services
        else:
            touched = {path.split("/")[1] for path in files if path.startswith("services/") and path.count("/") > 1}
            selected = [service for service in services if service in touched]

    lines = [f"services={json.dumps(selected)}", f"any={'true' if selected else 'false'}"]
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as handle:
            handle.write("\n".join(lines) + "\n")
    print("\n".join(lines))
    print(f"Servicios a probar: {', '.join(selected) if selected else 'ninguno'}", file=sys.stderr)


if __name__ == "__main__":
    main()
