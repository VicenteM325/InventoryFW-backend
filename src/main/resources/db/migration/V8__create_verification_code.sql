-- Códigos de un solo uso enviados por correo: segundo factor del login
-- (LOGIN_2FA), confirmación de activación de 2FA (ENABLE_2FA) y token de
-- recuperación de contraseña (PASSWORD_RESET). Solo se guarda un HMAC del
-- valor, nunca el código en claro.
CREATE TABLE verification_code (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL
        CONSTRAINT verification_code_type_check CHECK (type IN ('LOGIN_2FA', 'ENABLE_2FA', 'PASSWORD_RESET')),
    code_hash   VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts    INTEGER NOT NULL DEFAULT 0,
    consumed    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX verification_code_user_type_idx ON verification_code (user_id, type, created_at DESC);
CREATE INDEX verification_code_hash_idx ON verification_code (code_hash);
