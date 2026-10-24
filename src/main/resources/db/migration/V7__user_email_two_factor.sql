-- Correo del usuario (necesario para enviar códigos 2FA y enlaces de
-- recuperación de contraseña) y bandera de verificación en dos pasos.
-- email es opcional: las cuentas existentes (p.ej. el admin sembrado) no lo
-- tienen, por eso two_factor_enabled arranca en FALSE para no bloquearlas.
ALTER TABLE users ADD COLUMN email VARCHAR(254);
ALTER TABLE users ADD COLUMN two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Un correo identifica a una sola cuenta, sin distinguir mayúsculas.
CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email)) WHERE email IS NOT NULL;
