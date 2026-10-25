-- Para poder pedirle producto a un proveedor, el correo y el teléfono pasan a ser obligatorios.
-- Los proveedores ya registrados no tienen correo: se les pone un valor provisional (el dominio
-- ".invalid" está reservado y nunca entrega correo) para que alguien lo complete desde la pantalla
-- de Proveedores; el sistema no permite enviar pedidos a una dirección ".invalid".

ALTER TABLE supplier ADD COLUMN email VARCHAR(254);

UPDATE supplier SET email = 'sin-correo-' || supplier_id || '@proveedor.invalid' WHERE email IS NULL;
UPDATE supplier SET phone = 'Pendiente' WHERE phone IS NULL OR TRIM(phone) = '';

ALTER TABLE supplier ALTER COLUMN email SET NOT NULL;
ALTER TABLE supplier ALTER COLUMN phone SET NOT NULL;
