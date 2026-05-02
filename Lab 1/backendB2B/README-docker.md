# Backend Docker

## Levantar backend + Postgres

Desde la carpeta backendB2B:

```bash
docker compose up --build
```

## Variables

Las variables se inyectan desde docker-compose.yml.

- APP_PORT: 8090
- DB_HOST: db
- DB_PORT: 5432
- DB_NAME: b2b
- DB_USER: postgres
- DB_PASSWORD: postgres

## Notas

- El init.sql se ejecuta al inicializar la base de datos.
- Si necesitas reiniciar datos, elimina el volumen:

```bash
docker compose down -v
```
