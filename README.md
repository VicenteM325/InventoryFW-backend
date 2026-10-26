# InventoryFW — Backend

API REST de Multiservicios Wilson: inventario, ventas, alertas de stock con pedido de reposición por correo, notificaciones, reportes (PDF/XLSX), fotocopia de DPI y hojas de fotos tamaño cédula/pasaporte (PDF/DOCX). Incluye inicio de sesión con verificación en dos pasos por correo y recuperación de contraseña.
Spring Boot 3.5 · Java 25 · PostgreSQL 17 · Flyway · JWT en cookie HttpOnly.

Frontend: [InventoryFW-frontend](https://github.com/VicenteM325/InventoryFW-frontend).
Manual de usuario: [Scribe](https://scribehow.com/o/YKnGuif3TM2hFmaEspMEdQ/viewer/Managing_Inventory_and_Sales_at_Multiservicios_Wilson__UzU__U-NQQiRFGiAnrS06w).

## Documentación

- **Documentación técnica del proyecto (Fase 3):** manual técnico (arquitectura, modelo de datos, trazabilidad), guía de despliegue y Runbook (despliegue, rollback, monitoreo, incidentes), escalabilidad futura, procesos BPMN, migraciones propuestas y tecnologías nuevas.
- `docs/`: bitácoras de las pruebas de correo (`mailtrap-test-log.md`) y de validaciones (`validation-test-log.md`).

## API

Todas las rutas, salvo las de `/auth/` de inicio de sesión y recuperación y `/actuator/health`, exigen la cookie de sesión. Roles: `ADMIN` (Administrador) y `EMPLOYEE` (Encargado).

| Ruta | Qué hace | Rol |
|---|---|---|
| `/auth/login`, `/auth/2fa/verify`, `/auth/2fa/resend`, `/auth/logout`, `/auth/check-auth`, `/auth/user/details` | Sesión y segundo factor | público / sesión |
| `/auth/forgot-password`, `/auth/reset-password` | Recuperación de contraseña por correo | público |
| `/api/account` (`/password`, `/email`, `/2fa/enable`, `/2fa/confirm`, `/2fa/disable`) | Datos y seguridad de la cuenta propia | sesión |
| `/api/users` | Cuentas de usuario (crear, editar, activar, desactivar) | ADMIN |
| `/api/products` | Productos; desactivar y reactivar solo ADMIN | sesión |
| `/api/suppliers` | Proveedores; eliminar solo ADMIN | sesión |
| `/api/sales` | Registrar ventas y ver las recientes (descuenta stock y genera alerta) | sesión |
| `/api/stock-alerts` | Alertas pendientes (cualquier sesión); marcar atendida y pedido de reposición al proveedor (`/{id}/supplier-order` y `/preview`) | ADMIN |
| `/api/notifications` | Listar y marcar como leídas | ADMIN, EMPLOYEE |
| `/api/reports` | Ventas e inventario, con resumen y exportación | ADMIN, EMPLOYEE |
| `/api/documents` | Fotocopia de DPI y fotos tamaño cédula/pasaporte | ADMIN, EMPLOYEE |
| `/actuator/health` | Estado del servicio (lo usan Railway y el CI) | público |

## Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `DB_URL` | URL JDBC de PostgreSQL | `jdbc:postgresql://postgres_db:5432/inventory_db` |
| `DB_USER` / `DB_PASSWORD` | Credenciales de la base de datos | `postgres` / obligatoria |
| `JWT_SECRET` | Secreto HS256, mínimo 32 bytes (`openssl rand -base64 48`). También firma los códigos de un solo uso | obligatoria |
| `JWT_EXPIRATION` | Vigencia del JWT en segundos | `3600` |
| `CORS_ALLOWED_ORIGINS` | Orígenes del frontend, separados por coma | `http://localhost:5173,http://localhost:3000,http://localhost` |
| `FRONTEND_URL` | URL pública del frontend (los enlaces de recuperación apuntan ahí) | `http://localhost:5173` |
| `COOKIE_SECURE` | `false` solo en `http://localhost`; en producción `true` | `true` |
| `COOKIE_SAME_SITE` | `Lax`; `None` solo si el frontend está en otro sitio | `Lax` |
| `BREVO_API_KEY` | Envío de correo por la API HTTP de Brevo (recomendado en producción; Railway bloquea SMTP). Si tiene valor, se ignora el SMTP | vacía |
| `MAIL_FROM` / `MAIL_FROM_NAME` | Remitente (verificado en Brevo) y su nombre | `no-reply@inventoryfw.local` / `Multiservicios Wilson` |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP de desarrollo (Mailtrap Sandbox) | `sandbox.smtp.mailtrap.io` / `2525` / vacías |
| `MAIL_REPLY_TO` | Dónde llegan las respuestas de los proveedores | vacía (usa `MAIL_FROM`) |
| `BUSINESS_NAME` / `BUSINESS_PHONE` | Nombre y teléfono en la firma del pedido a proveedores | `Multiservicios Wilson` / vacío |
| `OTP_TTL_MINUTES` / `OTP_MAX_ATTEMPTS` / `OTP_RESEND_COOLDOWN_SECONDS` | Vigencia, intentos y espera para reenviar el código de 2FA | `10` / `5` / `60` |
| `RESET_TTL_MINUTES` | Vigencia del enlace de recuperación de contraseña | `30` |
| `LOGIN_MAX_FAILURES` / `LOGIN_FAILURE_WINDOW_MINUTES` / `LOGIN_LOCK_MINUTES` | Bloqueo tras contraseñas incorrectas consecutivas | `5` / `15` / `15` |
| `SECURITY_FAIL_ON_DEFAULT_ADMIN_PASSWORD` | `true` impide arrancar mientras `admin` conserve la contraseña sembrada, esté activo o no | `false` |
| `PORT` | Puerto HTTP | `8080` |
| `SPRING_PROFILES_ACTIVE` | `prod` en Railway (sin SQL en logs ni Security en DEBUG, y reintenta la conexión a la base hasta 60 s) | — |
| `SHOW_SQL` / `SECURITY_LOG_LEVEL` | Trazas verbosas bajo demanda | `false` / `WARN` |

`.env.example` trae la lista completa con comentarios.

## Correr en local

```bash
cp .env.example .env          # completar contraseñas y JWT_SECRET (y COOKIE_SECURE=false en http://localhost)
docker compose up --build     # PostgreSQL en localhost:5433, API en localhost:8080
curl localhost:8080/actuator/health
```

El correo de desarrollo sale por Mailtrap (variables `MAIL_*`) o por Brevo si hay `BREVO_API_KEY`. Sin ninguno de los dos, el 2FA y la recuperación de contraseña no podrán enviar códigos.

Tests (JDK 25 y un PostgreSQL; por ejemplo el de docker compose):

```bash
docker compose up -d postgres_db
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
DB_URL=jdbc:postgresql://localhost:5433/inventory_db DB_PASSWORD=<la de .env> JWT_SECRET=<la de .env> \
./mvnw verify
```

`ManualRealPhotoCheck` es una prueba manual con fotos reales; no corre en el CI.

## Base de datos y migraciones

El esquema lo maneja Flyway (`src/main/resources/db/migration`, hoy V1 a V11) y Hibernate solo lo valida (`ddl-auto=validate`). Nunca editar una migración ya aplicada: Flyway valida su checksum. Los cambios van en una migración nueva (la siguiente es `V12__...sql`) y solo hacia adelante: una migración no se revierte, se corrige con otra. Para cambios destructivos, separar en dos despliegues (primero agregar y migrar, después quitar lo viejo).

## Seguridad

- Sesión con JWT (HS256) en cookie HttpOnly; la vigencia sale de `JWT_EXPIRATION`.
- Verificación en dos pasos por correo (opcional por usuario) y recuperación de contraseña por enlace. Los códigos de un solo uso se guardan como HMAC, nunca en claro, y tienen vigencia, tope de intentos y espera entre reenvíos.
- Bloqueo temporal del login tras varias contraseñas incorrectas.
- Autorización por rol con `@PreAuthorize` en los controladores.
- Limitaciones conocidas: el cierre de sesión no revoca el token (caduca por vigencia) y el bloqueo de login vive en memoria, así que se reinicia con el servicio y no se comparte entre réplicas. Por eso la API se despliega hoy como una sola instancia.

## Fotos tamaño cédula y pasaporte

`/api/documents/id-photo` recorta una foto con el marco que elige el Encargado (`cropX/cropY/cropWidth/cropHeight`, fracciones de la foto ya derecha y girada) y la repite en una hoja de papel fotográfico 4×6 in. `/id-photo/preview` devuelve una sola copia en PNG y `/id-photo/specs` informa medidas y capacidad de cada tipo; el frontend las toma de ahí.

- Medidas de foto (cédula 3.1 × 3.9 cm, pasaporte 5 × 5 cm), tamaño de hoja, margen (~3 mm), separación entre copias y dpi viven solo en `DocumentLayoutRegistry`. La capacidad sale de esas medidas (hoy 9 de cédula y 2 de pasaporte): con un margen mayor a ~10 pt la cédula baja de 3 a 2 columnas.
- El PDF dibuja la misma imagen una vez por copia, con un borde gris fino como guía de corte. El Word usa una tabla de celdas de tamaño fijo y no se pudo verificar en Word en las pruebas (solo su estructura XML), así que conviene abrir un `.docx` generado antes de depender de él.
- Hay que imprimir al 100 % en papel 4×6 in (sin "ajustar a la página") para que la foto mida lo que dice.

## CI/CD

`.github/workflows/ci-cd.yml`:

- **CI** (PRs y push a `develop`/`main`): tests contra un PostgreSQL 17 real, que aplican todas las migraciones, y build de la imagen Docker.
- **Deploy** (push a `main`): `railway up` al servicio de Railway y smoke test de `/actuator/health`. Railway solo le pasa el tráfico a la versión nueva cuando el healthcheck responde 200; si no, sigue la anterior.

En GitHub: environment `production` (solo rama `main`) con el secreto `RAILWAY_TOKEN` (Project Token del entorno `production` de Railway), y las variables de repositorio `RAILWAY_SERVICE` y `BACKEND_URL`.

## Railway

- Un servicio vacío para la API (sin repo conectado: los deploys llegan desde GitHub Actions) y PostgreSQL, ambos en la misma región.
- Variables del servicio: `SPRING_PROFILES_ACTIVE=prod`, `PORT=8080`, `DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`, `DB_USER=${{Postgres.PGUSER}}`, `DB_PASSWORD=${{Postgres.PGPASSWORD}}`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS=https://<sitio>.netlify.app`, `FRONTEND_URL=https://<sitio>.netlify.app`, `BREVO_API_KEY`, `MAIL_FROM` y `SECURITY_FAIL_ON_DEFAULT_ADMIN_PASSWORD=true` (solo después de cambiar la contraseña de `admin`, o el servicio no arranca).
- Settings: dominio público con target port 8080, healthcheck `/actuator/health`, restart On Failure, límites de 2 vCPU y 1 GB de RAM (Railway cobra lo que se usa, no el límite), y Serverless apagado (con 1 vCPU el arranque tarda ~30 s, más que el timeout de 26 s del proxy de Netlify).
- Memoria: heap fijo de 512 MB con SerialGC (ver `ENTRYPOINT` del `Dockerfile`), y las fotos de DPI se procesan a 2560 px como máximo (`ImageCropServiceImpl`). En pruebas con 1 vCPU: ~230 MiB en reposo y ~720 MiB de pico con dos fotocopias simultáneas a partir de fotos originales de 12 MP.
- Revertir un deploy: en Railway, desde los despliegues del servicio, volver a desplegar la versión anterior (los nombres exactos del menú pueden variar). La base de datos no se revierte con el código; si una migración ya corrió, se corrige con una migración nueva (ver el Runbook).

La migración V2 crea el usuario `admin` con una contraseña que es pública en el historial del repositorio. En cada base de datos nueva, cambiarla de inmediato (Perfil → Seguridad o "olvidé mi contraseña"), crear un Administrador propio desde la pantalla de Usuarios y, si se quiere, desactivar `admin`. Mientras `admin` conserve la contraseña sembrada, el arranque solo emite una advertencia en el log, salvo que `SECURITY_FAIL_ON_DEFAULT_ADMIN_PASSWORD=true`.





# InventoryFW
Inventory built with Tailwind + React as the frontend and Spring Boot and Java as the backend
## Manual de Usuario
[Manual de Usuario](https://scribehow.com/o/YKnGuif3TM2hFmaEspMEdQ/viewer/Managing_Inventory_and_Sales_at_Multiservicios_Wilson__UzU__U-NQQiRFGiAnrS06w)
