#!/usr/bin/env python3
"""Verifica el Excel original, su traducción a permisos y la migración V10.

python3 scripts/ci/check_role_matrix.py --write  # regenerar V10 tras revisar matriz.json
python3 scripts/ci/check_role_matrix.py          # comprobar sin escribir
"""
import argparse
import hashlib
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / 'docs/seguridad/matriz-roles'
MIGRATION = ROOT / 'services/ms-identidad/src/main/resources/db/migration/V10__approved_role_matrix.sql'
NEW_PERMISSIONS = [
    ('identity.role.assign_initial', 'IDENTITY', 'Asignar un rol delegado exclusivamente al crear una cuenta', 'CONFIDENTIAL'),
    ('billing.invoice.void', 'BILLING', 'Anular una factura; separado de la emision (Cajero 2)', 'CONFIDENTIAL'),
    ('infrastructure.server.manage', 'INFRASTRUCTURE', 'Operacion de servidores por el equipo de Tecnologia (DevOps)', 'RESTRICTED'),
    ('infrastructure.database.manage', 'INFRASTRUCTURE', 'Operacion de bases de datos por el equipo de Tecnologia (DevOps)', 'RESTRICTED'),
    ('patients.self_access.read', 'PATIENTS', 'Consultar las solicitudes de acceso a los propios datos', 'CONFIDENTIAL'),
    ('patients.self_access.confirm', 'PATIENTS', 'Autorizar con OTP el acceso a los propios datos', 'CONFIDENTIAL'),
    ('patients.self_access.revoke', 'PATIENTS', 'Revocar el acceso a los propios datos', 'CONFIDENTIAL'),
]


def quoted(value):
    return "'" + value.replace("'", "''") + "'"


def workbook_rows(path):
    ns = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
    with zipfile.ZipFile(path) as archive:
        strings = [''.join(item.itertext()) for item in ET.fromstring(archive.read('xl/sharedStrings.xml')).findall('s:si', ns)]
        rows = {}
        for row in ET.fromstring(archive.read('xl/worksheets/sheet1.xml')).findall('.//s:row', ns):
            values = {}
            for cell in row.findall('s:c', ns):
                value = cell.find('s:v', ns)
                if value is not None:
                    values[re.sub(r'\d', '', cell.get('r'))] = strings[int(value.text)] if cell.get('t') == 's' else value.text
            rows[int(row.get('r'))] = values
        return rows


def generate(matrix):
    roles = matrix['roles']
    resource_names = {resource['column']: resource['name'] for resource in matrix['resources']}
    codes = ', '.join(quoted(role['code']) for role in roles)
    modules = re.search(r'public enum PermissionModule\s*\{([^}]+)', (ROOT / 'services/ms-identidad/src/main/java/com/medicalsys/identidad/domain/PermissionModule.java').read_text()).group(1)
    modules = ', '.join(quoted(module.strip()) for module in modules.split(',') if module.strip())
    sql = [
        '-- Matriz de Roles.xlsx: 15 roles y 30 recursos. No editar migraciones anteriores.',
        '-- Generada por scripts/ci/check_role_matrix.py --write desde docs/seguridad/matriz-roles/matriz.json.',
        '-- Las dependencias son lecturas de selectores, nunca permisos de escritura en otros modulos.',
        'ALTER TABLE permission DROP CHECK ck_permission_module;',
        f'ALTER TABLE permission ADD CONSTRAINT ck_permission_module CHECK (module IN ({modules}));',
        "ALTER TABLE `role` ADD COLUMN access_model VARCHAR(4) NOT NULL DEFAULT 'MAC';",
        "ALTER TABLE `role` ADD CONSTRAINT ck_role_access_model CHECK (access_model IN ('MAC', 'DAC', 'RBAC'));",
        'INSERT INTO permission (code, module, description, is_sensitive, classification) VALUES',
        ',\n'.join('    (' + ', '.join([quoted(code), quoted(module), quoted(description), 'TRUE', quoted(level)]) + ')' for code, module, description, level in NEW_PERMISSIONS) + ';',
        'INSERT INTO `role` (code, name, description, scope, is_system, is_active, access_model) VALUES',
        ',\n'.join('    (' + ', '.join([quoted(role['code']), quoted(role['name']), quoted('; '.join(resource_names[column] for column in role['resources'])), quoted(role['scope']), 'TRUE', 'TRUE', quoted(role['model'])]) + ')' for role in roles),
        'AS incoming ON DUPLICATE KEY UPDATE name = incoming.name, description = incoming.description,',
        '    scope = incoming.scope, is_system = TRUE, is_active = TRUE, access_model = incoming.access_model;',
        f'DELETE rp FROM role_permission rp JOIN `role` r ON r.id = rp.role_id WHERE r.code IN ({codes});',
    ]
    for role in roles:
        permissions = sorted(set(role['permissions'] + role['dependencies']))
        sql += [f"\n-- {role['name']} ({role['model']})",
                'INSERT INTO role_permission (role_id, permission_id)',
                f"SELECT r.id, p.id FROM `role` r JOIN permission p WHERE r.code = '{role['code']}' AND p.code IN (",
                ',\n'.join('    ' + ', '.join(quoted(p) for p in permissions[index:index+3]) for index in range(0, len(permissions), 3)) + ');']
    sql += [
        '\n-- Las altas de personal pueden asignar solo roles operativos delegados, sin cambiar perfiles existentes.',
        "DELETE d FROM role_delegation d JOIN `role` r ON r.id = d.role_id WHERE r.code = 'ADMINISTRATOR';",
        'INSERT INTO role_delegation (role_id, assignable_role_id)',
        'SELECT a.id, b.id FROM `role` a JOIN `role` b',
        "WHERE a.code = 'ADMINISTRATOR' AND b.code IN ('PHYSICIAN', 'MEDICAL_INTERN', 'NURSE', 'LAB_TECHNICIAN',",
        "    'RECEPTIONIST', 'BILLING_CLERK', 'BILLING_SUPERVISOR', 'MARKETING_MANAGER', 'PATIENT');",
        '-- Emitir y anular son funciones incompatibles en una misma cuenta.',
        'INSERT IGNORE INTO role_incompatibility (role_id, incompatible_role_id, reason)',
        "SELECT LEAST(a.id, b.id), GREATEST(a.id, b.id), 'Separacion entre emision y anulacion de facturas'",
        "FROM `role` a JOIN `role` b WHERE a.code = 'BILLING_CLERK' AND b.code = 'BILLING_SUPERVISOR';",
        '-- Las asignaciones de plataforma no quedan vinculadas a una organizacion.',
        "UPDATE user_role ur JOIN `role` r ON r.id = ur.role_id SET ur.organization_public_id = NULL WHERE r.scope = 'PLATFORM';",
    ]
    return '\n'.join(sql) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    matrix = json.loads((DIRECTORY / 'matriz.json').read_text())
    source = DIRECTORY / matrix['source']
    assert hashlib.sha256(source.read_bytes()).hexdigest() == matrix['sha256'], 'El Excel original cambio'
    rows = workbook_rows(source)
    assert len(matrix['roles']) == 15 and len(matrix['resources']) == 30
    for index, role in enumerate(matrix['roles'], 4):
        row = rows[index]
        assert (role['name'], role['model']) == (row['B'], row['AR']), role['code']
        assert set(role['resources']) == {r['column'] for r in matrix['resources'] if row.get(r['column'], '').lower() == 'x'}, role['code']
    known = set()
    for migration in (ROOT / 'services/ms-identidad/src/main/resources/db/migration').glob('V*.sql'):
        if migration == MIGRATION:
            continue
        known.update(re.findall(r"\('([a-z]+\.[a-z0-9_]+\.[a-z_]+)', '[A-Z_]+'", migration.read_text()))
    known.update(code for code, _, _, _ in NEW_PERMISSIONS)
    for role in matrix['roles']:
        assert set(role['permissions'] + role['dependencies']) <= known, role['code']
        assert all(p.endswith('.read') for p in role['dependencies']), role['code']
        if role['code'] != 'PATIENT':
            permitted = {p for resource in matrix['resources'] if resource['column'] in role['resources'] for p in resource['permissions']}
            assert set(role['permissions']) <= permitted, role['code']
    sql = generate(matrix)
    if args.write:
        MIGRATION.write_text(sql)
    else:
        assert MIGRATION.read_text() == sql, 'V10 no coincide: ejecutar --write y revisar el diff'
    print('Excel y migracion coinciden: 15 roles, nombres y modelos exactos, 30 recursos y permisos conocidos.')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except AssertionError as error:
        print(f'Matriz invalida: {error}', file=sys.stderr)
        sys.exit(1)
