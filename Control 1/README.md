    q# Control 1 - Base de Datos

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


### Opcion 1: script automatizado (recomendada)

El script [run_all_psql.sh](run_all_psql.sh) ejecuta en orden los archivos
[dbCreate.sql](dbCreate.sql), [loadData.sql](loadData.sql) y [runStatements.sql](runStatements.sql),
mostrando mensajes de avance y deteniendose ante cualquier error.

Dar permisos de ejecucion (una sola vez):

```bash
chmod +x run_all_psql.sh
```

Luego ejecutar (usando por defecto la base de datos `control1_db` y el usuario `postgres`):

```bash
./run_all_psql.sh
```

Tambien se puede indicar explicitamente base de datos y usuario:

```bash
./run_all_psql.sh control1_db postgres
```

### Opcion 2: comandos manuales

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



