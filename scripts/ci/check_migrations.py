#!/usr/bin/env python3
"""Reglas de las migraciones Flyway de todos los servicios.

1. Nombre: V<version>__<descripcion>.sql o R__<descripcion>.sql. Flyway ignora en silencio un archivo
   mal nombrado, y la migracion nunca se aplica.
2. Version unica por servicio, contando db/migration y db/demo: en el entorno compartido se aplican
   juntas y Flyway se niega a arrancar si dos comparten version.
3. Con --base <ref>: una migracion versionada que ya existe en la rama base no se modifica, ni se
   borra, ni se renombra. Ya esta aplicada en la base compartida; si cambia su checksum, Flyway
   rechaza el arranque del servicio. El cambio va en una migracion nueva. Las repetibles (R__) si
   pueden cambiar: Flyway las vuelve a aplicar.

Uso: python3 scripts/ci/check_migrations.py [--base origin/develop]
"""
import argparse
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
NAME = re.compile(r"^(V(?P<version>\d+(?:[._]\d+)*)__|R__)[A-Za-z0-9_]+\.sql$")
LOCATIONS = ("migration", "demo")


def migration_files():
    for service in sorted((ROOT / "services").iterdir()):
        for location in LOCATIONS:
            folder = service / "src" / "main" / "resources" / "db" / location
            if folder.is_dir():
                for path in sorted(folder.iterdir()):
                    yield service.name, path


def git(*args):
    return subprocess.run(["git", "-C", str(ROOT), *args], check=True, capture_output=True).stdout


def versioned_in_base(base):
    """Rutas de migraciones versionadas que existen en la rama base."""
    listing = git("ls-tree", "-r", "--name-only", base, "--", "services").decode()
    for path in listing.splitlines():
        parts = pathlib.PurePosixPath(path).parts
        if len(parts) >= 7 and parts[-3] == "db" and parts[-2] in LOCATIONS and parts[-1].startswith("V"):
            yield path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", help="rama contra la que se comparan las migraciones ya aplicadas")
    args = parser.parse_args()

    errors = []
    versions = {}
    count = 0
    for service, path in migration_files():
        count += 1
        relative = path.relative_to(ROOT)
        match = NAME.match(path.name)
        if not match:
            errors.append(f"{relative}: el nombre debe ser V<version>__<descripcion>.sql o R__<descripcion>.sql")
            continue
        version = match.group("version")
        if version is None:
            continue
        key = (service, tuple(int(part) for part in re.split(r"[._]", version)))
        if key in versions:
            errors.append(f"{relative}: la version V{version} ya la usa {versions[key]}")
        else:
            versions[key] = relative

    if args.base:
        for path in versioned_in_base(args.base):
            current = ROOT / path
            if not current.exists():
                errors.append(f"{path}: se borro o renombro una migracion ya aplicada en {args.base}")
            # hash-object aplica los mismos filtros que git (fin de linea), asi que solo cuenta el contenido.
            elif git("rev-parse", f"{args.base}:{path}") != git("hash-object", "--", path):
                errors.append(f"{path}: se modifico una migracion ya aplicada en {args.base}; "
                              "el cambio va en una migracion nueva")

    if errors:
        print("Migraciones con problemas:")
        for error in errors:
            print(f"  - {error}")
        return 1
    scope = f", ninguna versionada de {args.base} modificada" if args.base else ""
    print(f"{count} migraciones revisadas: nombres validos, versiones unicas por servicio{scope}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
