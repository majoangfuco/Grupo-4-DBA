#!/usr/bin/env pwsh

# Script de ejecucion automatica de los scripts SQL del Control 1 (Windows/PowerShell)
# Uso:
#   .\run_all_psql.ps1 [-DB_NAME control1_db] [-DB_USER postgres]
# Por defecto: DB_NAME=control1_db, DB_USER=postgres

[CmdletBinding()]
param(
    [string]$DB_NAME = "control1_db",
    [string]$DB_USER = "postgres"
)

$ErrorActionPreference = "Stop"
$clearPgPassword = $false

# Pedir password una sola vez (si no viene definida en PGPASSWORD)
if (-not $env:PGPASSWORD) {
    $securePassword = Read-Host "Password for user '$DB_USER'" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
        $clearPgPassword = $true
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

Write-Host "========================================"
Write-Host "  Control 1 - Ejecucion de scripts SQL  "
Write-Host "========================================"

# 1) Crear la base de datos si no existe
$dbList = psql -U $DB_USER -lqt
$dbExists = $false

foreach ($line in $dbList) {
    $name = ($line -split "\|")[0].Trim()
    if ($name -eq $DB_NAME) {
        $dbExists = $true
        break
    }
}

if ($dbExists) {
    Write-Host "[INFO] La base de datos '$DB_NAME' ya existe. No se crea de nuevo."
}
else {
    Write-Host "[INFO] Creando base de datos '$DB_NAME'..."
    createdb -U $DB_USER $DB_NAME
    Write-Host "[OK] Base de datos '$DB_NAME' creada correctamente."
}

# 2) Ejecutar dbCreate.sql
Write-Host ""
Write-Host "[INFO] Ejecutando dbCreate.sql..."
psql -v ON_ERROR_STOP=1 -U $DB_USER -d $DB_NAME -f dbCreate.sql
Write-Host "[OK] Estructura de la base de datos creada (dbCreate.sql)."

# 3) Ejecutar loadData.sql
Write-Host ""
Write-Host "[INFO] Ejecutando loadData.sql (carga de datos)..."
psql -v ON_ERROR_STOP=1 -U $DB_USER -d $DB_NAME -f loadData.sql
Write-Host "[OK] Datos cargados correctamente (loadData.sql)."

# 4) Ejecutar runStatements.sql
Write-Host ""
Write-Host "[INFO] Ejecutando runStatements.sql (consultas)..."
psql -v ON_ERROR_STOP=1 -U $DB_USER -d $DB_NAME -f runStatements.sql
Write-Host "[OK] Consultas ejecutadas correctamente (runStatements.sql)."

Write-Host ""
Write-Host "========================================"
Write-Host "  Proceso completado sin errores.       "
Write-Host "========================================"

if ($clearPgPassword) {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
