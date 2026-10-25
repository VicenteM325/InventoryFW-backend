-- Nuevos eventos de negocio para la campana de notificaciones: producto agotado
-- y cambio de estado (activado/desactivado) de un producto.

ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN ('STOCK_BAJO', 'DOCUMENTO_GENERADO', 'AGOTADO', 'PRODUCTO_ESTADO'));
