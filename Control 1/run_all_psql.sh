#!/usr/bin/env bash
set -euo pipefail

# Uso:
#   ./run_all_psql.sh <db_name> <db_user>
# Ejemplo:
#   ./run_all_psql.sh control1_db postgres

DB_NAME="${1:-control1_db}"
DB_USER="${2:-postgres}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[1/4] Ejecutando dbCreate.sql en ${DB_NAME}..."
psql -v ON_ERROR_STOP=1 -U "${DB_USER}" -d "${DB_NAME}" -f "${SCRIPT_DIR}/dbCreate.sql"

echo "[2/4] Ejecutando loadData.sql en ${DB_NAME}..."
psql -v ON_ERROR_STOP=1 -U "${DB_USER}" -d "${DB_NAME}" -f "${SCRIPT_DIR}/loadData.sql"

echo "[3/4] Validando carga minima (TIPO_DOC y COMUNA)..."
TIPO_DOC_COUNT="$(psql -X -A -t -U "${DB_USER}" -d "${DB_NAME}" -c "SELECT COUNT(*) FROM TIPO_DOC;")"
COMUNA_COUNT="$(psql -X -A -t -U "${DB_USER}" -d "${DB_NAME}" -c "SELECT COUNT(*) FROM COMUNA;")"

echo "TIPO_DOC: ${TIPO_DOC_COUNT} filas"
echo "COMUNA: ${COMUNA_COUNT} filas"

if [[ "${TIPO_DOC_COUNT}" -lt 2 || "${COMUNA_COUNT}" -lt 1 ]]; then
  echo "[ERROR] No se detecto carga valida. No se ejecutara runStatements.sql"
  exit 1
fi

echo "[4/4] Ejecutando runStatements.sql..."
psql -v ON_ERROR_STOP=1 -U "${DB_USER}" -d "${DB_NAME}" -f "${SCRIPT_DIR}/runStatements.sql"

echo "Proceso finalizado correctamente."
