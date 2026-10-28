-- Campos opcionales: no reemplazan órdenes ni eventos históricos.
ALTER TABLE work_orders ADD COLUMN IF NOT EXISTS error_aceptacion varchar(500);
ALTER TABLE order_outbox ADD COLUMN IF NOT EXISTS stock_fallido boolean;
