# Control 1 - Base de Datos

Este repositorio contiene los entregables solicitados:

- Diagrama Entidad-Relacion (ER) en [docs/ERD.md](docs/COMPLETAT)
- Diccionario de datos en [docs/data-dictionary.md](docs/COMPLETAT)
- Script de creacion de base de datos en [dbCreate.sql](dbCreate.sql)
- Script de carga de datos de prueba en [loadData.sql](loadData.sql)
- Script de consultas requeridas en [runStatements.sql](runStatements.sql)

## Sugerencia de uso

1. Ejecutar `dbCreate.sql`.
2. Ejecutar `loadData.sql`.
3. Ejecutar `runStatements.sql`.

## Ejecucion por linea de comandos (PostgreSQL)

Desde la carpeta del proyecto:

"/.../Grupo-4-DBA/Control 1"


Crear la base de datos (si no existe):

```bash
createdb -U postgres control1_db
```

Ejecutar scripts en secuencia:

```bash
psql -v ON_ERROR_STOP=1 -U postgres -d control1_db -f dbCreate.sql
psql -v ON_ERROR_STOP=1 -U postgres -d control1_db -f loadData.sql
psql -v ON_ERROR_STOP=1 -U postgres -d control1_db -f runStatements.sql
```

Nota: `runStatements.sql` debe ejecutarse solo si `loadData.sql` cargo correctamente los datos.


Alternativa automatizada:

```bash
./run_all_psql.sh control1_db postgres
```
