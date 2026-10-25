-- Último pedido de reposición enviado al proveedor desde una alerta de stock.

ALTER TABLE stock_alert ADD COLUMN ordered_quantity INTEGER;
ALTER TABLE stock_alert ADD COLUMN ordered_at TIMESTAMP(6);
