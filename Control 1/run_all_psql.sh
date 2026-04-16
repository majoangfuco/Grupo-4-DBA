#!/usr/bin/env bash

# Script de ejecución automática de los scripts SQL del Control 1
# Uso:
#   ./run_all_psql.sh [NOMBRE_DB] [USUARIO]
# Por defecto: NOMBRE_DB=control1_db, USUARIO=postgres

set -euo pipefail

DB_NAME="${1:-control1_db}"
DB_USER="${2:-postgres}"
CLEAR_PGPASSWORD=0

# Pedir password una sola vez (si no viene definida en PGPASSWORD)
if [[ -z "${PGPASSWORD:-}" ]]; then
  read -rsp "Password for user '$DB_USER': " DB_PASS
  echo
  export PGPASSWORD="$DB_PASS"
  unset DB_PASS
  CLEAR_PGPASSWORD=1
fi

cleanup() {
  if [[ "$CLEAR_PGPASSWORD" -eq 1 ]]; then
    unset PGPASSWORD
  fi
}
trap cleanup EXIT

echo "========================================"
echo "  Control 1 - Ejecución de scripts SQL  "
echo "========================================"

# 1) Crear la base de datos si no existe
if psql -U "$DB_USER" -lqt | cut -d '|' -f 1 | grep -qw "$DB_NAME"; then
  echo "[INFO] La base de datos '$DB_NAME' ya existe. No se crea de nuevo."
else
  echo "[INFO] Creando base de datos '$DB_NAME'..."
  createdb -U "$DB_USER" "$DB_NAME"
  echo "[OK] Base de datos '$DB_NAME' creada correctamente."
fi

# 2) Ejecutar dbCreate.sql
echo
echo "[INFO] Ejecutando dbCreate.sql..."
psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" -f dbCreate.sql
echo "[OK] Estructura de la base de datos creada (dbCreate.sql)."

# 3) Ejecutar loadData.sql
echo
echo "[INFO] Ejecutando loadData.sql (carga de datos)..."
psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" -f loadData.sql
echo "[OK] Datos cargados correctamente (loadData.sql)."

# 4) Ejecutar runStatements.sql
echo
echo "[INFO] Ejecutando runStatements.sql (consultas)..."
psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" -f runStatements.sql
echo "[OK] Consultas ejecutadas correctamente (runStatements.sql)."

echo
echo "========================================"
echo "  Proceso completado sin errores.       "
echo "========================================"