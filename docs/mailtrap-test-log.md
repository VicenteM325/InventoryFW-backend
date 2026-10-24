# Pruebas empíricas de correo, 2FA y recuperación de contraseña

Fecha: 2026-10-24 · Backend en `integration/2fa` · PostgreSQL 15 real (Docker) · JDK 25.

## Estado del envío a Mailtrap

**Pendiente: no hay credenciales de Mailtrap en este entorno.** El backend ya está
configurado para Mailtrap Sandbox (`sandbox.smtp.mailtrap.io:2525`) y toma las
credenciales de variables de entorno (`.env.example`). Para completar la
verificación real:

1. Copiar `.env.example` a `.env` y completar `MAIL_USERNAME` / `MAIL_PASSWORD`
   con los valores de *Email Testing → Inboxes → SMTP Settings* de Mailtrap.
2. Levantar el backend con esas variables y repetir los comandos de la sección
   "Reproducir contra Mailtrap". Los correos deben aparecer en la bandeja.

## Qué se probó (contra un servidor SMTP local descartable, puerto 1025)

Se usó un receptor SMTP local en lugar de Mailtrap para validar de extremo a
extremo el flujo real HTTP → servicio → SMTP → contenido del correo. **48 de 48
comprobaciones pasaron.** Se recibieron los 5 correos esperados (activación de
2FA, 3 códigos de login, enlace de recuperación) con asunto, destinatario y
contenido correctos.

| Área | Comprobaciones |
|---|---|
| Sesión | `check-auth` = 200 con sesión y 401 sin ella; un token *challenge* de 2FA usado como cookie de sesión = 401 |
| Activar 2FA | correo inválido 400; contraseña errónea 400; correo normalizado a minúsculas; llega el código; código con formato inválido 400, erróneo 400, correcto activa 2FA; el código no se reutiliza |
| Login con 2FA | devuelve `twoFactorRequired` + `challengeToken` y **no** abre sesión; correo con código; reenvío inmediato 429 (cooldown); challenge inválido 401; código erróneo 400; código correcto abre sesión; código reutilizado 400; 5 códigos erróneos → 400×4 y 429, y luego ni el correcto sirve |
| Recuperación | correo inexistente y existente responden idéntico (200) y solo el existente recibe correo; email malformado 400; contraseña débil 400; token falso 400; token válido cambia la contraseña; el token es de un solo uso; la contraseña vieja deja de funcionar |
| Cuenta | cambio de contraseña: actual errónea 400, igual a la actual 400, correcto 200; desactivar 2FA exige contraseña |
| Fuerza bruta | 5 logins fallidos → 401, el sexto 429 |
| Alta de usuarios | usuario con espacios 400; contraseña débil 400; correo repetido 409; alta con correo nace con 2FA activo |

Además, pruebas unitarias (`./mvnw test`): `MailServiceImplTest`,
`VerificationCodeServiceTest`, `LoginAttemptServiceTest`, `JwtUtilTest` y
`PasswordRecoveryServiceImplTest`.

## Reproducir contra Mailtrap

Con el backend arriba (`./mvnw spring-boot:run`) y las variables `MAIL_*` definidas:

```bash
# 1) Registrar correo y pedir el código de activación de 2FA (requiere sesión)
curl -c jar -H 'Content-Type: application/json' -X POST localhost:8080/auth/login \
  -d '{"userName":"admin","password":"<contraseña>"}'
curl -b jar -H 'Content-Type: application/json' -X PUT localhost:8080/api/account/email \
  -d '{"email":"admin@ejemplo.com","currentPassword":"<contraseña>"}'
curl -b jar -X POST localhost:8080/api/account/2fa/enable      # llega el código a Mailtrap

# 2) Recuperación de contraseña
curl -H 'Content-Type: application/json' -X POST localhost:8080/auth/forgot-password \
  -d '{"email":"admin@ejemplo.com"}'                           # llega el enlace a Mailtrap
```
