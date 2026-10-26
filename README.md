# InventoryFW — Backend

API REST de Multiservicios Wilson: inventario, ventas, alertas de stock, notificaciones, reportes (PDF/XLSX), fotocopia de DPI y hojas de fotos tamaño cédula/pasaporte (PDF/DOCX).
Spring Boot 3.5 · Java 25 · PostgreSQL · Flyway · JWT en cookie HttpOnly.

Frontend: [InventoryFW-frontend](https://github.com/VicenteM325/InventoryFW-frontend).

## Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `DB_URL` | URL JDBC de PostgreSQL | `jdbc:postgresql://postgres_db:5432/inventory_db` |
| `DB_USER` / `DB_PASSWORD` | Credenciales de la base de datos | `postgres` / obligatoria |
| `JWT_SECRET` | Secreto HS256, mínimo 32 bytes (`openssl rand -base64 48`) | obligatoria |
| `JWT_EXPIRATION` | Vigencia del JWT en segundos | `3600` |
| `CORS_ALLOWED_ORIGINS` | Orígenes del frontend, separados por coma | `http://localhost:5173,http://localhost:3000,http://localhost` |
| `PORT` | Puerto HTTP | `8080` |
| `SPRING_PROFILES_ACTIVE` | `prod` en Railway (sin SQL en logs ni Security en DEBUG) | — |

## Correr en local

```bash
cp .env.example .env          # completar contraseñas y JWT_SECRET
docker compose up --build     # PostgreSQL en localhost:5433, API en localhost:8080
curl localhost:8080/actuator/health
```

Tests (JDK 25 y un PostgreSQL; por ejemplo el de docker compose):

```bash
docker compose up -d postgres_db
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
DB_URL=jdbc:postgresql://localhost:5433/inventory_db DB_PASSWORD=<la de .env> JWT_SECRET=<la de .env> \
./mvnw verify
```

El esquema lo maneja Flyway (`src/main/resources/db/migration`). Nunca editar una migración ya aplicada: Flyway valida su checksum. Los cambios van en una migración nueva (`V7__...sql`).

## Fotos tamaño cédula y pasaporte

`/api/documents/id-photo` recorta una foto con el marco que elige el Encargado (`cropX/cropY/cropWidth/cropHeight`, fracciones de la foto ya derecha y girada) y la repite en una hoja de papel fotográfico 4×6 in. `/id-photo/preview` devuelve una sola copia en PNG y `/id-photo/specs` informa medidas y capacidad de cada tipo; el frontend las toma de ahí.

- Medidas de foto (cédula 3.1 × 3.9 cm, pasaporte 5 × 5 cm), tamaño de hoja, margen (~3 mm), separación entre copias y dpi viven solo en `DocumentLayoutRegistry`. La capacidad sale de esas medidas (hoy 9 de cédula y 2 de pasaporte): con un margen mayor a ~10 pt la cédula baja de 3 a 2 columnas.
- El PDF dibuja la misma imagen una vez por copia, con un borde gris fino como guía de corte. El Word usa una tabla de celdas de tamaño fijo y no se pudo verificar en Word en las pruebas (solo su estructura XML), así que conviene abrir un `.docx` generado antes de depender de él.
- Hay que imprimir al 100 % en papel 4×6 in (sin "ajustar a la página") para que la foto mida lo que dice.

## CI/CD

`.github/workflows/ci-cd.yml`:

- **CI** (PRs y push a `develop`/`main`): tests contra un PostgreSQL real, que aplican todas las migraciones, y build de la imagen Docker.
- **Deploy** (push a `main`): `railway up` al servicio de Railway y smoke test de `/actuator/health`.

En GitHub: environment `production` (solo rama `main`) con el secreto `RAILWAY_TOKEN` (Project Token del entorno `production` de Railway), y las variables de repositorio `RAILWAY_SERVICE` y `BACKEND_URL`.

## Railway

- Un servicio vacío para la API (sin repo conectado: los deploys llegan desde GitHub Actions) y PostgreSQL, ambos en la misma región.
- Variables del servicio: `SPRING_PROFILES_ACTIVE=prod`, `PORT=8080`, `DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`, `DB_USER=${{Postgres.PGUSER}}`, `DB_PASSWORD=${{Postgres.PGPASSWORD}}`, `JWT_SECRET` y `CORS_ALLOWED_ORIGINS=https://<sitio>.netlify.app`.
- Settings: dominio público con target port 8080, healthcheck `/actuator/health`, restart On Failure, límites de 2 vCPU y 1 GB de RAM (Railway cobra lo que se usa, no el límite), y Serverless apagado (con 1 vCPU el arranque tarda ~30 s, más que el timeout de 26 s del proxy de Netlify).
- Memoria: heap fijo de 512 MB con SerialGC (ver `ENTRYPOINT` del `Dockerfile`), y las fotos de DPI se procesan a 2560 px como máximo (`ImageCropServiceImpl`). En pruebas con 1 vCPU: ~230 MiB en reposo y ~720 MiB de pico con dos fotocopias simultáneas a partir de fotos originales de 12 MP.

La migración V2 crea el usuario `admin` con una contraseña que es pública en el historial del repositorio. En cada base de datos nueva, crear de inmediato otro Administrador desde la pantalla de Usuarios y desactivar `admin`.
